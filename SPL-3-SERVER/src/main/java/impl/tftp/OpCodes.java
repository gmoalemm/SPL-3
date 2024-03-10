package impl.tftp;

public enum OpCodes {
    RRQ(1), WRQ(2), DATA(3), ACK(4), ERROR(5),
    DIRQ(6), LOGRQ(7), DELRQ(8), BCAST(9), DISC(10),
    UNKNOWN(-1);

    private final short num;

    OpCodes(int num) {
        this.num = (short) num;
    }

    public byte[] getBytes() {
        return new byte[] { (byte) (num >> 8), (byte) (num & 0xff) };
    }

    public short getShort() {
        return num;
    }

    public static OpCodes fromInt(int num) {
        for (OpCodes opcode : OpCodes.values()) {
            if (opcode.num == num) {
                return opcode;
            }
        }

        return UNKNOWN;
    }

    public static OpCodes fromBytes(byte a, byte b) {
        return fromInt((short) (((short) a) << 8 | (short) (b) & 0x00ff));
    }
}
