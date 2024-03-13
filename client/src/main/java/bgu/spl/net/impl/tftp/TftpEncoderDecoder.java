package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private final byte[] packetBytes = new byte[1 << 10];
    private int len = 0; // how many byted did we read into the current packet
    private OpCodes opcode;
    private short leftToRead = 0;

    public static final int MAX_DATA_PACKET = 512;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        packetBytes[len++] = nextByte; // save the last byte

        if (len == 1)
            return null;

        if (len == 2)
            opcode = OpCodes.fromBytes(packetBytes[0], packetBytes[1]);

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
        } else if (len > 4) {
            // this is the end of the packet
            if (--leftToRead == 0) {
                message = new byte[len];

                for (int i = 0; i < len; i++)
                    message[i] = packetBytes[i];

                len = 0;
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