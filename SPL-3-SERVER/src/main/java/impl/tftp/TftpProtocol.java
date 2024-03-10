package impl.tftp;

import api.BidiMessagingProtocol;
import srv.Connections;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private boolean terminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    private boolean isLoggedIn;
    private Queue<byte[]> packetsQueue;

    private short lastBlockNumber = 0;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.terminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.isLoggedIn = false;
        this.packetsQueue = new LinkedBlockingDeque<>();
    }

    @Override
    public void process(byte[] message) {
        OpCodes opcode = OpCodes.fromBytes(message[0], message[1]);

        switch (opcode) {
            case RRQ:
                // TODO: look at the running example. We need to send each pakcet, wait for an
                // ACK packet and only then continue.

                // start at index 2 (after the opcode) and read everything except the last byte
                handleRRQ(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
                break;
            case WRQ:
                break;
            case LOGRQ:
                handleLogin(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
                break;
            case DELRQ:
                handleDELQ(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
                break;
            case DATA:
                break;
            case ACK:
                break;
            case ERROR:
                break;
            case DIRQ:
                handleDIRQ();
                break;
            case DISC:
                break;
            case BCAST:
                break;
            default:
                throw new UnsupportedOperationException("Unimplemented method 'process'"); // error 4
        }

        if (!packetsQueue.isEmpty()) connections.send(connectionId, packetsQueue.remove());
    }

    @Override
    public boolean shouldTerminate() {
        return terminate;
    }

    private void handleRRQ(String filename) {
        // open the file
        File file;
        ArrayDeque<Byte> packetData;
        byte[] packet;
        short blockNumber = 1;
        int nextByte;

        if (!isLoggedIn) {
            // TODO: error message
        }

        file = new File("server" + File.separator + "Files" + File.separator + filename);

        if (!file.exists()) {
            // TODO: error message?
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
        } catch (IOException ignored) {
        }
    }

    private void handleDELQ(String filename) {
        // open the file
        File file;

        if (!isLoggedIn) {
            // TODO: error message
        }

        file = new File("server" + File.separator + "Files" + File.separator + filename);

        if (file.exists()) {
            file.delete();
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
        if (isLoggedIn){
            // TODO: error
            return;
        }

        LoggedUsers.usersMap.put(connectionId, username);
        System.out.println("Added " + username + " as #" + connectionId);
        isLoggedIn = true;

        // send ack
        packetsQueue.add(buildAckPacket((short)0));
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
        for (int j = 0; j < bytes.size(); j++) packet[6 + j] = bytes.removeFirst();

        return packet;
    }

    private byte[] buildAckPacket(short blockNum){
        byte[] packet = new byte[4];
        byte[] block = TftpEncoderDecoder.shortToBytes(blockNum);

        packet[0] = OpCodes.LOGRQ.getBytes()[0];
        packet[1] = OpCodes.LOGRQ.getBytes()[1];
        packet[2] = block[0];
        packet[3] = block[1];

        return packet;
    }
}
