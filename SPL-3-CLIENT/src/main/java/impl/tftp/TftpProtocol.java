package impl.tftp;

import api.MessagingProtocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TftpProtocol implements MessagingProtocol<byte[]> {
    private boolean shouldTerminate = false;
    private short lastBlockNumber = 0;
    private Queue<byte[]> packetsQueue; // packets that require ACK only
    private OpCodes lastKeyboardOptOpcode = OpCodes.UNKNOWN;
    private File currentFile;
    private final String directoryPath = "/SPL-3-CLIENT/";
    private String fileTransfered = "";
    private ArrayDeque<Byte> currentDirName = new ArrayDeque<>();

    @Override
    public byte[] process(byte[] message) {
        OpCodes opcode = OpCodes.fromBytes(message[0], message[1]);
        byte[] response = null;

        switch (opcode) {
            case DATA:
                response = handleData(message);
                break;
            case ACK:
                response = handleACK(message);
                break;
            case ERROR:
                handleError(message);
                break;
            case BCAST:
                handleBCAST(message);
                break;
            case UNKNOWN:
                response = createErrorMessage(Errors.NOT_DEFINED);
                break;
            default:
                lastKeyboardOptOpcode = opcode;
        }

        return response;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private byte[] createErrorMessage(Errors err) {
        byte[] msg = err.getMessage().getBytes();
        byte[] pac = new byte[5 + msg.length];

        pac[0] = OpCodes.ERROR.getBytes()[0];
        pac[1] = OpCodes.ERROR.getBytes()[1];
        pac[2] = err.getBytes()[0];
        pac[3] = err.getBytes()[1];

        for (int i = 0; i < msg.length; i++) {
            pac[4 + i] = msg[i];
        }

        pac[pac.length - 1] = 0;

        return pac;
    }

    private byte[] handleACK(byte[] message) {
        //short packetBlockNum = TftpEncoderDecoder.bytesToShort(lastSentPacket[2], lastSentPacket[3]);
            
        short ackBlockNum = TftpEncoderDecoder.bytesToShort(message[2], message[3]);

        System.out.println("ACK " + ackBlockNum);

        OpCodes opcode;

        byte[] lastSentPacket = null;

        if (ackBlockNum == 0){
            opcode = lastKeyboardOptOpcode;
        }
        else if (packetsQueue.isEmpty()) {
            return createErrorMessage(Errors.NOT_DEFINED);
        }
        else{
            lastSentPacket = packetsQueue.peek(); 
            opcode = OpCodes.fromBytes(lastSentPacket[0], lastSentPacket[1]); //the type of the last messege sent.
        }

        short packetBlockNum = opcode == OpCodes.DATA ? TftpEncoderDecoder.bytesToShort(lastSentPacket[0], lastSentPacket[1]): 0;
        byte[] response = null;

        if (packetBlockNum != ackBlockNum){
            System.out.println("Got ACK with blockNumber that doest coresponding to the last massege blockNumber");
            return createErrorMessage(Errors.NOT_DEFINED);
        }

        String filename;

        switch (opcode) {
            case RRQ:
                filename = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                createFile(filename);
                break;
            case WRQ:
                filename = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                addDataPackets(filename);
                fileTransfered = filename;
            case DATA:
                packetsQueue.remove();
                response = packetsQueue.peek();
                break;
            case LOGRQ:
                System.out.println("this clinet was successfully logged in to server");
                break;
            // sent delete req., got ack (file deleted)
            case DELRQ:
                System.out.println("the file was successfully deleted");
                break;
            // requested to disconnect, got acknoleged
            case DISC:
                shouldTerminate = true;
                break;
            default:
                response = createErrorMessage(Errors.ILLEGAL_OP);
                break;
        }

        return response;
    }

    private void addDataPackets(String filename){
        File fileToSend = new File(directoryPath + filename);

        try (FileInputStream fstream = new FileInputStream(fileToSend)) {
            ArrayDeque<Byte> packetData = new ArrayDeque<>();
            int nextByte;
            byte[] packet;

            lastBlockNumber = 0;

            // read each byte
            while ((nextByte = fstream.read()) != -1) {
                packetData.add((byte) nextByte);

                // if reached max num of bytes in a packet, create one
                if (packetData.size() == TftpEncoderDecoder.MAX_DATA_PACKET) {
                    packet = buildDataPacket(packetData, ++lastBlockNumber);
                    packetsQueue.add(packet);
                    packetData.clear();
                }
            }

            // last packet
            packet = buildDataPacket(packetData, ++lastBlockNumber);
            packetsQueue.add(packet);
        } catch (IOException ignored) {
        }
    }

    private void createFile(String filename){
        Path filePath = Paths.get(directoryPath + File.separator + filename);

        // Create the directory if it doesn't exist
        try {
            Files.createDirectory(filePath.getParent());

            // Create the file
            Files.createFile(filePath);

            currentFile = new File(filePath.toString());
            fileTransfered = filename;
        } catch (IOException ignored) {
                
        }
    }

    private byte[] handleData(byte[] packet) {
        short blockNumber = TftpEncoderDecoder.bytesToShort(packet[2], packet[3]);
        short pacetSize = TftpEncoderDecoder.bytesToShort(packet[0], packet[1]);
        byte[] bytes;

        // case of the data package contain file.
        if (currentFile != null) {
            try (FileOutputStream fStream = new FileOutputStream(currentFile)) {
                fStream.write(packet, 6, packet.length - 6);
            } catch (IOException ignored) {
            } finally {
                if (pacetSize < TftpEncoderDecoder.MAX_DATA_PACKET) {
                    System.out.println("RRQ " + currentFile.getName() + " complete");
                    currentFile = null; // the call for the file is finised.
                    System.out.println("WRQ " + fileTransfered);
                    fileTransfered = "";
                }
            }
        }
        // the data is a files' names.
        else {
            for (int i = 6; i < packet.length; i++) {
                if (packet[i] == 0){
                    bytes = new byte[currentDirName.size()];

                    for (int j = 0; j < bytes.length; j++) {
                        bytes[j] = (byte) currentDirName.removeFirst();
                    }  

                    System.out.println(new String(bytes, StandardCharsets.UTF_8));
                    currentDirName.clear();
                }
                else
                    currentDirName.add(packet[i]);
            }

            if (pacetSize < TftpEncoderDecoder.MAX_DATA_PACKET){
                bytes = new byte[currentDirName.size()];

                for (int j = 0; j < bytes.length; j++) {
                    bytes[j] = (byte) currentDirName.removeFirst();
                }  

                System.out.println(new String(bytes, StandardCharsets.UTF_8));
                currentDirName.clear();
            }
        }

        return buildAckPacket(blockNumber);
    }

    private void handleError(byte[] packet) {
        short errNum = TftpEncoderDecoder.bytesToShort(packet[2], packet[3]);

        String msg = new String(packet, 4, packet.length - 5, StandardCharsets.UTF_8);

        System.out.println("Error " + errNum + " (" + msg + ")");
    }

    private void handleBCAST(byte[] message) {
        boolean added = message[2] == 1;
        String filename = new String(message, 3, message.length - 4, StandardCharsets.UTF_8);

        System.out.print("BCAST ");

        if (added)
            System.out.print("add ");
        else
            System.out.println("del ");

        System.out.println(filename);
    }

    /**
     * Get a message and wrap it with a DATA packet header.
     * 
     * @param bytes    an array of bytes that are the message.
     * @param blockNum the block number of this packet.
     * @return a byte array.
     */
    private byte[] buildDataPacket(ArrayDeque<Byte> bytes, short blockNum) {
        byte[] packet = new byte[6 + bytes.size()];

        // opcode
        packet[0] = OpCodes.DATA.getBytes()[0];
        packet[1] = OpCodes.DATA.getBytes()[1];

        // packet size
        packet[2] = TftpEncoderDecoder.shortToBytes((short) bytes.size())[0];
        packet[3] = TftpEncoderDecoder.shortToBytes((short) bytes.size())[1];

        // block no.
        packet[4] = TftpEncoderDecoder.shortToBytes(blockNum)[0];
        packet[5] = TftpEncoderDecoder.shortToBytes(blockNum)[1];

        // add the bytes
        for (int j = 0; j < bytes.size(); j++)
            packet[6 + j] = bytes.removeFirst();

        return packet;
    }

    private byte[] buildAckPacket(short blockNum) {
        byte[] packet = new byte[4];
        byte[] block = TftpEncoderDecoder.shortToBytes(blockNum);

        packet[0] = OpCodes.LOGRQ.getBytes()[0];
        packet[1] = OpCodes.LOGRQ.getBytes()[1];
        packet[2] = block[0];
        packet[3] = block[1];

        return packet;
    }
}
