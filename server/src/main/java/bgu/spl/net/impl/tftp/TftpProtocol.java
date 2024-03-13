package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private int connectionId;
    private boolean isLoggedIn;
    private boolean shouldTerminate = false;
    private short lastBlockNumber = 0;
    private Queue<byte[]> packetsQueue;
    private Connections<byte[]> connections;
    private String newFilename; // the name of the file that is being created
    private String directoryPath = "Files";
    private ArrayDeque<byte[]> newFileBytes;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.isLoggedIn = false;
        this.packetsQueue = new LinkedBlockingDeque<>();
        this.newFilename = new String();
        this.newFileBytes = new ArrayDeque<>();
    }

    @Override
    public void process(byte[] message) {
        OpCodes opcode = OpCodes.extractOpcode(message);

        if (!isLoggedIn && opcode != OpCodes.LOGRQ) {
            connections.send(connectionId, createErrorMessage(Errors.NOT_LOGGED_IN));
            return;
        }

        switch (opcode) {
            case RRQ:
                // start at index 2 (after the opcode) and read everything except the last byte
                handleRRQ(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
                break;
            case WRQ:
                handleWRQ(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
                break;
            case LOGRQ:
                handleLogin(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
                break;
            case DELRQ:
                handleDELRQ(new String(message, 2, message.length - 3, StandardCharsets.UTF_8));
                break;
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
            case DIRQ:
                handleDIRQ();
                break;
            case DISC:
                isLoggedIn = false;
                connections.send(connectionId, buildAckPacket((short) 0));
                connections.disconnect(connectionId);
                break;
            default:
                connections.send(connectionId, createErrorMessage(Errors.ILLEGAL_OP));
                return;
        }
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
     * @param blockNumber
     */
    private void handleACK(short blockNumber) {
        byte[] packetToAcknowledge = packetsQueue.peek();
        short packetBlockNum = TftpEncoderDecoder.bytesToShort(packetToAcknowledge[4], packetToAcknowledge[5]);
        OpCodes opcode = OpCodes.extractOpcode(packetToAcknowledge);

        System.out.println("ACK " + packetBlockNum);

        if (opcode == OpCodes.DATA) {
            if (packetBlockNum != blockNumber) {
                connections.send(connectionId, createErrorMessage(Errors.NOT_DEFINED));
                return;
            }

            packetsQueue.remove(); // the first packed was ssufuuly handle

            if (!packetsQueue.isEmpty()) {
                connections.send(connectionId, packetsQueue.peek());
            }
        } else {
            connections.send(connectionId, createErrorMessage(Errors.NOT_DEFINED));
        }
    }

    /**
     * Handle Read Requests.
     * 
     * @param filename the filename as a string.
     */
    private void handleRRQ(String filename) {
        try {
            PublicResources.accessSemaphore.acquire();

            // open the file
            File file = new File(directoryPath + File.separator + filename);
            FileInputStream fstream = new FileInputStream(file);

            if (!file.exists()) {
                connections.send(connectionId, createErrorMessage(Errors.FILE_NOT_FOUND));
                fstream.close();
                PublicResources.accessSemaphore.release();
                return;
            }

            addDataPackets(filename);

            connections.send(connectionId, packetsQueue.peek());

            fstream.close();
        } catch (IOException | InterruptedException ignored) {
        } finally {
            PublicResources.accessSemaphore.release();
        }
    }

    /**
     * Handle Write Requests.
     * 
     * @param filename the filename as a string.
     */
    private void handleWRQ(String filename) {
        try {
            PublicResources.accessSemaphore.acquire();

            // open the file
            File file = new File(directoryPath + File.separator + filename);

            if (file.exists()) {
                connections.send(connectionId, createErrorMessage(Errors.FILE_EXISTS));
                PublicResources.accessSemaphore.release();
                return;
            }

            if (!newFilename.isEmpty()) {
                // in the middle of another writing!
                connections.send(connectionId, createErrorMessage(Errors.ACCESS_VIOLATION));
                PublicResources.accessSemaphore.release();
                return;
            }

            newFilename = filename; // save the name of the file that we'll create

            connections.send(connectionId, buildAckPacket((short) 0)); // send ack packet
        } catch (InterruptedException ignored) {
        } finally {
            PublicResources.accessSemaphore.release();
        }
    }

    /**
     * Handle Delete Requests.
     * 
     * @param filename the filename as a string.
     */
    private void handleDELRQ(String filename) {
        try {
            PublicResources.accessSemaphore.acquire();

            // open the file
            File file = new File(directoryPath + File.separator + filename);

            if (file.exists()) {
                file.delete();
                connections.send(connectionId, buildAckPacket((short) 0));
                sendBCAST(false, filename); // send all users about the update.
            } else {
                connections.send(connectionId, createErrorMessage(Errors.FILE_NOT_FOUND));
            }

        } catch (InterruptedException ignored) {
        } finally {
            PublicResources.accessSemaphore.release();
        }
    }

    /**
     * Handle Directory Requests.
     */
    private void handleDIRQ() {
        try {
            PublicResources.accessSemaphore.acquire();

            File folder = new File(directoryPath);

            File[] files = folder.listFiles();
            ArrayDeque<Byte> message = new ArrayDeque<>();
            byte[] currentPacket;

            if (files != null) {
                lastBlockNumber = 0;

                for (File file : files) {
                    if (message.size() == TftpEncoderDecoder.MAX_DATA_PACKET) {
                        currentPacket = buildDataPacket(message, ++lastBlockNumber);
                        packetsQueue.add(currentPacket);
                        message.clear();
                    }

                    // assuming the file is not a directory

                    for (byte b : file.getName().getBytes()) {
                        message.add(b);

                        if (message.size() == TftpEncoderDecoder.MAX_DATA_PACKET) {
                            currentPacket = buildDataPacket(message, ++lastBlockNumber);
                            packetsQueue.add(currentPacket);
                            message.clear();
                        }
                    }

                    message.add((byte) 0);
                }

                message.removeLast();
                currentPacket = buildDataPacket(message, ++lastBlockNumber);
                packetsQueue.add(currentPacket);

                connections.send(connectionId, packetsQueue.peek());
            }
        } catch (InterruptedException ignored) {
        } finally {
            PublicResources.accessSemaphore.release();
        }
    }

    /**
     * Handle Login.
     * 
     * @param username the username as a string.
     */
    private void handleLogin(String username) {
        // the clinet is alredy looge d in.
        if (isLoggedIn || PublicResources.usersMap.containsValue(username)) {
            connections.send(connectionId, createErrorMessage(Errors.ALR_LOGGED_IN));
            return;
        }

        PublicResources.usersMap.put(connectionId, username);
        isLoggedIn = true;

        // send ack
        connections.send(connectionId, buildAckPacket((short) 0));
    }

    /**
     * Handle Data packet.
     * 
     * @param packet actual packet, as bytes.
     */
    private void handleData(byte[] packet) {
        short blockNumber = TftpEncoderDecoder.bytesToShort(packet[4], packet[5]);
        short packetSize = TftpEncoderDecoder.bytesToShort(packet[2], packet[3]);

        // System.out.println("GOT DATA BN#" + blockNumber);

        connections.send(connectionId, buildAckPacket(blockNumber));

        // in the server we know that the data is a file
        newFileBytes.add(packet);

        if (packetSize < TftpEncoderDecoder.MAX_DATA_PACKET) {
            createNewFile();
        }
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
     * Send a BCAST to everyone.
     * 
     * @param added    did a file was created or deleted?
     * @param filename the name if the file as string.
     */
    private void sendBCAST(boolean added, String filename) {
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[4 + filenameBytes.length];

        packet[0] = OpCodes.BCAST.getBytes()[0];
        packet[1] = OpCodes.BCAST.getBytes()[1];

        packet[2] = added ? (byte) 1 : (byte) 0;

        for (int i = 0; i < filenameBytes.length; i++)
            packet[3 + i] = filenameBytes[i];

        packet[packet.length - 1] = (byte) 0;

        for (Map.Entry<Integer, String> entry : PublicResources.usersMap.entrySet()) {
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
        for (int j = 0; j < bytesSize; j++) {
            packet[6 + j] = bytes.removeFirst();
        }

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

    /**
     * Read a file into packets and insert them to the queue.
     * 
     * @param filename
     */
    private void addDataPackets(String filename) {
        File fileToSend = new File(directoryPath + File.separator + filename);

        // already checked the existence of the file

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
            System.out.println(ignored.getMessage());
        }
    }

    /**
     * Create the file that we got in WRQ.
     */
    private void createNewFile() {
        Path filePath = Paths.get(directoryPath + File.separator + newFilename);

        // Create the directory if it doesn't exist

        File directory = new File(directoryPath);

        if (!directory.exists()) {
            directory.mkdir();
        }

        // Create the file

        File newFile = new File(filePath.toString());
        // System.out.println(newFile.createNewFile());

        byte[] packet = null;

        try (FileOutputStream fStream = new FileOutputStream(newFile, true)) {
            while (!newFileBytes.isEmpty()) {
                packet = newFileBytes.removeFirst();
                fStream.write(packet, 6, packet.length - 6);
            }
        } catch (IOException ignored) {
        } finally {
            sendBCAST(true, newFilename); // send all useres about the update
            newFilename = "";
        }
    }
}
