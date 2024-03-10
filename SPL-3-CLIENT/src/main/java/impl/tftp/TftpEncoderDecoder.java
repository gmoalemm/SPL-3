package impl.tftp;

import api.MessageEncoderDecoder;

import java.nio.charset.StandardCharsets;

public class TftpEncoderDecoder implements MessageEncoderDecoder<String> {
    @Override
    public String decodeNextByte(byte nextByte) {
        return null;
    }

    @Override
    public byte[] encode(String message) {
        String[] args = message.split(" ");

        OpCodes opcode = OpCodes.fromString(args[0]);

        System.out.println(opcode.name());

        switch (opcode) {
            case RRQ:
                break;
            case WRQ:
                break;
            case LOGRQ:
                return encodeLOGRQ(args);
            case DELRQ:
                break;
            case DATA:
                break;
            case ACK:
                // TODO: notice the client sends ACK automatically
                return encodeACK(args);
            case ERROR:
                break;
            case DIRQ:
                break;
            case DISC:
                break;
            default:
                break;
        }

        return null;
    }

    private byte[] encodeLOGRQ(String[] args){
        if (args.length != 2){
            return null;
        }

        byte[] username = args[1].getBytes(StandardCharsets.UTF_8);
        byte[] msg = new byte[3 + username.length];

        msg[0] = OpCodes.LOGRQ.getBytes()[0];
        msg[1] = OpCodes.LOGRQ.getBytes()[1];

        for (int i = 0; i < username.length; i++)
            msg[2 + i] = username[i];

        msg[msg.length - 1] = 0;
        return msg;
    }

    private byte[] encodeACK(String[] args){
        if (args.length != 2){
            return null;
        }

        byte[] block = shortToBytes(Short.parseShort(args[1]));
        byte[] msg = new byte[4];

        msg[0] = OpCodes.LOGRQ.getBytes()[0];
        msg[1] = OpCodes.LOGRQ.getBytes()[1];
        msg[2] = block[0];
        msg[3] = block[1];

        return msg;
    }

    //////////////////////// HELPERS //////////////////////

    public static short bytesToShort(byte a, byte b) {
        return (short) (((short) a) << 8 | (short) (b) & 0x00ff);
    }

    public static byte[] shortToBytes(short s) {
        return new byte[] { (byte) (s >> 8), (byte) (s & 0xff) };
    }
}
