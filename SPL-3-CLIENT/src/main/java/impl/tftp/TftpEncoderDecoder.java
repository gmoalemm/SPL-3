package impl.tftp;

import java.util.ArrayDeque;
import java.util.Arrays;

import api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private final byte[] packetBytes = new byte[1 << 10];
    private final ArrayDeque<Byte> messageData = new ArrayDeque<>();

    private int len;    // how many byted did we read into the current packet
    private OpCodes opcode;
    private short leftToRead = 0;

    private boolean lastDataPacket = true;

    public static final int MAX_DATA_PACKET = 512;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        packetBytes[len++] = nextByte;

        if (len == 1) return null;

        if (len == 2) opcode = OpCodes.fromBytes(packetBytes[0], packetBytes[1]);

        switch (opcode) {
            case RRQ:
            case WRQ:
            case LOGRQ:
            case DELRQ:
                return decodeTerminatedBy0();
            case DATA:
                return decodeData();
            case ACK:
                return decodeAck();
            case ERROR:
                return decodeERR();
            case DIRQ:
            case DISC:
                return decodeDIRQ_DISC();
            case BCAST:
                return decodeBCAST();
            default:
                return OpCodes.UNKNOWN.getBytes();
        }
    }

    private byte[] decodeTerminatedBy0() {
        byte[] message = null;

        if (packetBytes[len - 1] == 0) {
            message = Arrays.copyOf(packetBytes, len);
            len = 0;
        }

        return message;
    }

    private byte[] decodeDIRQ_DISC() {
        byte[] message = Arrays.copyOf(packetBytes, len);
        len = 0;

        return message;
    }

    private byte[] decodeData() {
        byte[] message = null;

        if (len == 4) {
            leftToRead = bytesToShort(packetBytes[2], packetBytes[3]);
            leftToRead += 2; // the size of block-number field
            lastDataPacket = leftToRead < 2 + MAX_DATA_PACKET;
        }

        if (len > 4) {
            // the data starts after 6 bytes (opcode, size, block, each takes 2 bytes)
            if (len > 6) messageData.add(packetBytes[len - 1]);

            // this is the end of the packet
            if (--leftToRead == 0) {
                len = 0;

                if (lastDataPacket) {
                    message = new byte[2 + messageData.size()];

                    message[0] = OpCodes.DATA.getBytes()[0];
                    message[1] = OpCodes.DATA.getBytes()[1];

                    int i = 2;

                    while (!messageData.isEmpty()) message[i++] = messageData.removeFirst();
                }
            }
        }

        return message;
    }

    private byte[] decodeAck() {
        byte[] message = null;

        if (len == 4) {
            message = Arrays.copyOf(packetBytes, len);
            len = 0;
        }

        return message;
    }

    private byte[] decodeBCAST() {
        byte[] message = null;

        if (len > 3 && packetBytes[len - 1] == 0) {
            message = Arrays.copyOf(packetBytes, len);
            len = 0;
        }

        return message;
    }

    private byte[] decodeERR() {
        byte[] message = null;

        if (len > 4 && packetBytes[len - 1] == 0) {
            message = Arrays.copyOf(packetBytes, len);
            len = 0;
        }

        return message;
    }

    //////////////////////// ENCODER //////////////////////

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    //////////////////////// HELPERS //////////////////////

    public static short bytesToShort(byte a, byte b) {
        return (short) (((short) a) << 8 | (short) (b) & 0x00ff);
    }

    public static byte[] shortToBytes(short s) {
        return new byte[] { (byte) (s >> 8), (byte) (s & 0xff) };
    }
}