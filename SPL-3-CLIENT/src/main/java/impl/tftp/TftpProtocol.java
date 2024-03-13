package impl.tftp;

import api.MessagingProtocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class TftpProtocol implements MessagingProtocol<byte[]> {
    private boolean shouldTerminate = false;
    private short lastBlockNumber = 0;
    private Queue<byte[]> packetsQueue = new ConcurrentLinkedQueue<>(); // packets that require ACK only
    private OpCodes lastKeyboardOptOpcode = OpCodes.UNKNOWN;
    private final String directoryPath = ""; // TODO: get current directory somethow?
    private String fileTransfered = "";
    // the name of the current directory name sent from the server
    private ArrayDeque<Byte> currentDirName = new ArrayDeque<>();
    String lastCommandArg;

    @Override
    public byte[] process(byte[] message) {
        OpCodes opcode = OpCodes.extractOpcode(message);
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
            case RRQ:
            case WRQ:
                lastCommandArg = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
            default:
                lastKeyboardOptOpcode = opcode;
        }

        return response;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    /**
     * Create an error packet.
     * 
     * @param err an error code in the form of an enum.
     * @return an error packet.
     */
    private byte[] createErrorMessage(Errors err) {
        byte[] msg = err.getMessage().getBytes();
        byte[] pac = new byte[5 + msg.length];

        pac[0] = OpCodes.ERROR.getBytes()[0];
        pac[1] = OpCodes.ERROR.getBytes()[1];
        pac[2] = err.getBytes()[0];
        pac[3] = err.getBytes()[1];

        for (int i = 0; i < msg.length; i++)
            pac[4 + i] = msg[i];

        pac[pac.length - 1] = 0;

        return pac;
    }

    /**
     * Handle an ack message.
     * 
     * @param message
     * @return a response to the ack, if any, null if no response shpuld be sent.
     */
    private byte[] handleACK(byte[] message) {
        short ackBlockNum = TftpEncoderDecoder.bytesToShort(message[2], message[3]);
        OpCodes commandOpCode; // the opcode of the command that required an ack message
        byte[] lastSentPacket = null;

        System.out.println("ACK " + ackBlockNum);

        if (ackBlockNum == 0)
            commandOpCode = lastKeyboardOptOpcode;
        else if (packetsQueue.isEmpty())
            return createErrorMessage(Errors.NOT_DEFINED);
        else {
            lastSentPacket = packetsQueue.peek();

            // the type of the last messege sent.
            commandOpCode = OpCodes.extractOpcode(lastSentPacket);
        }

        short packetBlockNum = commandOpCode == OpCodes.DATA
                ? TftpEncoderDecoder.bytesToShort(lastSentPacket[4], lastSentPacket[5])
                : 0;
        byte[] response = null;

        if (packetBlockNum != ackBlockNum) {
            // last massege blockNumber");
            return createErrorMessage(Errors.NOT_DEFINED);
        }

        String filename;

        switch (commandOpCode) {
            case WRQ:
                filename = lastCommandArg;
                addDataPackets(filename);
                fileTransfered = filename;
                response = packetsQueue.peek();
                break;
            case DATA:
                packetsQueue.remove();
                response = packetsQueue.peek();

                if (response == null) {
                    System.out.println("WRQ " + fileTransfered + " complete");
                    fileTransfered = "";
                }

                break;
            case LOGRQ:
                // System.out.println("this clinet was successfully logged in to server");
                break;
            case DELRQ:
                // System.out.println("the file was successfully deleted");
                break;
            case DISC:
                shouldTerminate = true;
                break;
            default:
                response = createErrorMessage(Errors.ILLEGAL_OP);
                break;
        }

        return response;
    }

    /**
     * Read a file into packets and insert them to the queue.
     * 
     * @param filename
     */
    private void addDataPackets(String filename) {
        File fileToSend = new File(filename);

        // System.out.println("Working Directory = " + System.getProperty("user.dir"));
        // System.out.println("fts " + fileToSend);

        if (!fileToSend.exists())
            System.out.println(Errors.FILE_NOT_FOUND.getMessage());

        try (FileInputStream fstream = new FileInputStream(fileToSend)) {
            //System.out.println("adding packets");
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
                    //System.out.println("packet added");
                }
            }

            // last packet
            packet = buildDataPacket(packetData, ++lastBlockNumber);
            packetsQueue.add(packet);
            //System.out.println("packet added");
        } catch (IOException ignored) {
            System.out.println(ignored.getMessage());
        }
    }

    /**
     * Handle a DATA packet.
     * 
     * @param packet
     * @return a response (ack or error).
     */
    private byte[] handleData(byte[] packet) {
        short blockNumber = TftpEncoderDecoder.bytesToShort(packet[4], packet[5]);
        short packetSize = TftpEncoderDecoder.bytesToShort(packet[2], packet[3]);
        byte[] bytes;
        File file;

        // case of the data package contain file.
        if (lastKeyboardOptOpcode == OpCodes.RRQ) {
            file = new File(lastCommandArg);

            try (FileOutputStream fStream = new FileOutputStream(file, true)) {
                fStream.write(packet, 6, packet.length - 6);
            } catch (IOException ignored) {
            } finally {
                if (packetSize < TftpEncoderDecoder.MAX_DATA_PACKET) {
                    System.out.println("RRQ " + file.getName() + " complete");
                    file = null; // the call for the file is finised.
                }
            }
        }
        // the data is a files' names.
        else {
            for (int i = 6; i < packet.length; i++) {
                // completed a file name
                if (packet[i] == 0) {
                    bytes = new byte[currentDirName.size()];

                    for (int j = 0; j < bytes.length; j++) {
                        bytes[j] = (byte) currentDirName.removeFirst();
                    }

                    System.out.println(new String(bytes, StandardCharsets.UTF_8));
                    currentDirName.clear();
                } else
                    currentDirName.add(packet[i]);
            }

            // last filename in the last packet
            if (packetSize < TftpEncoderDecoder.MAX_DATA_PACKET) {
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

    /**
     * Handle an error message.
     * 
     * @param packet
     */
    private void handleError(byte[] packet) {
        short errNum = TftpEncoderDecoder.bytesToShort(packet[2], packet[3]);

        String msg = new String(packet, 4, packet.length - 5, StandardCharsets.UTF_8);

        System.out.println("Error " + errNum + " (" + msg + ")");
    }

    /**
     * Handle a BCAST packet.
     * 
     * @param message
     */
    private void handleBCAST(byte[] message) {
        boolean added = message[2] == 1;
        String filename = new String(message, 3, message.length - 4, StandardCharsets.UTF_8);

        System.out.print("BCAST ");

        if (added)
            System.out.print("add ");
        else
            System.out.print("del ");

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
        int bytesSize = bytes.size();

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
        for (int j = 0; j < bytesSize; j++)
            packet[6 + j] = bytes.removeFirst();

        return packet;
    }

    /**
     * uild an ACK packet with a given block number.
     * 
     * @param blockNum
     * @return a byte array.
     */
    private byte[] buildAckPacket(short blockNum) {
        byte[] packet = new byte[4];
        byte[] block = TftpEncoderDecoder.shortToBytes(blockNum);

        packet[0] = OpCodes.ACK.getBytes()[0];
        packet[1] = OpCodes.ACK.getBytes()[1];
        packet[2] = block[0];
        packet[3] = block[1];

        return packet;
    }
}
