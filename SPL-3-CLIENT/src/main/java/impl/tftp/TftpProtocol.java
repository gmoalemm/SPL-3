package impl.tftp;

import api.BidiMessagingProtocol;
import api.MessagingProtocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
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
    private File currentFile;
    private final String directoryPath = "/SPL-3-CLIENT/";

    @Override
    public byte[] process(byte[] message) {
        OpCodes opcode = OpCodes.fromBytes(message[0], message[1]);

        switch (opcode) {
            case DATA:
                handleData(message);
                break;
            case ACK:
                short blockNum = TftpEncoderDecoder.bytesToShort(message[2], message[3]);
                handleACK(blockNum);
                break;
            case ERROR:
                handleError(message);
                break;
            case BCAST:
                handleBCAST(message);
                break;
            default:
                return createErrorMessage(Errors.NOT_DEFINED);
        }
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

    private byte[] handleACK(short blockNumber) {
        byte[] lastSentPacket = packetsQueue.peek();
        short packetBlockNum = TftpEncoderDecoder.bytesToShort(lastSentPacket[2], lastSentPacket[3]);
        OpCodes opcode = OpCodes.fromBytes(lastSentPacket[0], lastSentPacket[1]);

        switch (opcode) {
            // sent data, got ack
            case DATA:
                if (packetBlockNum != blockNumber)
                    return createErrorMessage(Errors.NOT_DEFINED);

                packetsQueue.remove();

                break;

            // sent login, got ack
            case LOGRQ:

                // sent delete req., got ack (file deleted)
            case DELRQ:
                if (packetBlockNum != 0)
                    return createErrorMessage(Errors.NOT_DEFINED);

                break;

            // sent WRQ, got ack. now start sending the data packets
            case WRQ:
                if (packetBlockNum != blockNumber)
                    return createErrorMessage(Errors.NOT_DEFINED);

                // TODO: send data

                break;

            // requested to disconnect, got acknoleged
            case DISC:
                if (packetBlockNum != 0)
                    return createErrorMessage(Errors.NOT_DEFINED);

                shouldTerminate = false;
                break;
            default:
                break;
        }

        return null;
    }

    private void handleRRQ(String filename) {
        // open the file
        File file;
        ArrayDeque<Byte> packetData;
        byte[] packet;
        short blockNumber = 1;
        int nextByte;

        file = new File(directoryPath + filename);

        if (!file.exists()) {
            connections.send(connectionId, createErrorMessage(Errors.FILE_NOT_FOUND));
            return;
        }

        try (FileInputStream fstream = new FileInputStream(file)) {
            packetData = new ArrayDeque<>();

            // read each byte
            while ((nextByte = fstream.read()) != -1) {
                packetData.add((byte) nextByte);

                // if reached max num of bytes in a packet, create one
                if (packetData.size() == TftpEncoderDecoder.MAX_DATA_PACKET) {
                    packet = buildDataPacket(packetData, blockNumber++);
                    packetsQueue.add(packet);
                    packetData.clear();
                }
            }

            // last packet
            packet = buildDataPacket(packetData, blockNumber);
            packetsQueue.add(packet);

            connections.send(connectionId, packetsQueue.peek());
        } catch (IOException ignored) {
        }
    }

    private void handleWRQ(String filename) {
        // open the file
        File file;
        byte[] packet;

        file = new File(directoryPath + File.separator + filename);

        if (file.exists()) {
            connections.send(connectionId, createErrorMessage(Errors.FILE_EXISTS));
            return;
        }

        try {
            Path filePath = Paths.get(directoryPath + File.separator + filename);

            // Create the directory if it doesn't exist
            Files.createDirectory(filePath.getParent());

            // Create the file
            Files.createFile(filePath);

            currentFile = new File(filePath.toString());

            System.out.println("File created successfully at: " + filePath);
        } catch (IOException e) {
            // Handle IOException
            e.printStackTrace();
        }

        packet = buildAckPacket((short) 0);
        connections.send(connectionId, packet); // send ack packet
    }

    private void handleDELRQ(String filename) {
        // open the file
        File file;

        file = new File(directoryPath + filename);

        if (file.exists()) {
            file.delete();
            connections.send(connectionId, buildAckPacket((short) 0));
            sendBCAST(false, filename); // send all users about the update.
        } else {
            connections.send(connectionId, createErrorMessage(Errors.FILE_NOT_FOUND));
        }
    }

    private void handleDIRQ() {
        File folder = new File("server" + File.separator + "Files" + File.separator);
        File[] files = folder.listFiles();
        ArrayDeque<Byte> message = new ArrayDeque<>();
        byte[] packet;

        if (files != null) {
            for (File file : files) {
                if (message.size() == TftpEncoderDecoder.MAX_DATA_PACKET) {
                    packet = buildDataPacket(message, ++lastBlockNumber);
                    packetsQueue.add(packet);
                    message.clear();
                }

                // assuming the file is not a directory

                for (byte b : file.getName().getBytes()) {
                    message.add(b);

                    if (message.size() == TftpEncoderDecoder.MAX_DATA_PACKET) {
                        packet = buildDataPacket(message, ++lastBlockNumber);
                        packetsQueue.add(packet);
                        message.clear();
                    }
                }

                message.add((byte) 0);
            }

            message.removeFirst();
            packet = buildDataPacket(message, ++lastBlockNumber);
            packetsQueue.add(packet);
        }
    }

    private void handleLogin(String username) {
        // the clinet is alredy looge d in.
        if (isLoggedIn || LoggedUsers.usersMap.containsValue(username)) {
            connections.send(connectionId, createErrorMessage(Errors.ALR_LOGGED_IN));
            return;
        }

        LoggedUsers.usersMap.put(connectionId, username);
        System.out.println("Added " + username + " as #" + connectionId);
        isLoggedIn = true;

        // send ack
        packetsQueue.add(buildAckPacket((short) 0));
    }

    private void handleData(byte[] packet) {
        short blockNumber = TftpEncoderDecoder.bytesToShort(packet[2], packet[3]);

        // case of the data pakeges contain file.
        if (currentFile != null) {
            try (FileOutputStream fStream = new FileOutputStream(currentFile)) {
                fStream.write(packet, 6, packet.length - 6);
            } catch (IOException ignored) {
            } finally {
                if (packet.length < TftpEncoderDecoder.MAX_DATA_PACKET) {
                    sendBCAST(true, currentFile.getName()); // send all useres about the uptadet
                    currentFile = null; // the call for the file is finised.
                }
            }
        }
        // the data is a files' names.
        else {
            for (int i = 6; i < packet.length; i++) {
                if (packet[i] == 0)
                    System.out.println();
                else
                    System.out.println(new String(new byte[] { packet[i] }, StandardCharsets.UTF_8));
            }
        }

        connections.send(connectionId, buildAckPacket(blockNumber));
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

    private void sendBCAST(boolean added, String filename) {
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[4 + filenameBytes.length];

        packet[0] = OpCodes.BCAST.getBytes()[0];
        packet[1] = OpCodes.BCAST.getBytes()[1];

        packet[2] = added ? (byte) 1 : (byte) 0;

        for (int i = 0; i < filenameBytes.length; i++)
            packet[3 + i] = filenameBytes[i];

        packet[packet.length - 1] = (byte) 0;

        for (Map.Entry<Integer, String> entry : LoggedUsers.usersMap.entrySet()) {
            Integer id = entry.getKey();
            connections.send(id, packet);
        }
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
