package bgu.spl.net.impl.tftp;

/** Supported TFTP opcodes. */
public enum OpCodes {
    RRQ(1), WRQ(2), DATA(3), ACK(4), ERROR(5),
    DIRQ(6), LOGRQ(7), DELRQ(8), BCAST(9), DISC(10),
    UNKNOWN(-1);

    private final short num;

    OpCodes(int num) {
        this.num = (short) num;
    }

    /**
     * Get the number of the current opcode.
     * 
     * @return a 2-bytes array.
     */
    public byte[] getBytes() {
        return new byte[] { (byte) (num >> 8), (byte) (num & 0xff) };
    }

    /**
     * Get the number of the current opcode.
     * 
     * @return a short.
     */
    public short getShort() {
        return num;
    }

    /**
     * Create an opcode enum from an int.
     * 
     * @param num command code.
     * @return corresponding opcode enum or UNKNOWN enum if the value is invalid.
     */
    public static OpCodes fromInt(int num) {
        for (OpCodes opcode : OpCodes.values()) {
            if (opcode.num == num) {
                return opcode;
            }
        }

        return UNKNOWN;
    }

    /**
     * Create an opcode enum from two bytes.
     * 
     * @param a the first byte.
     * @param b the last byte.
     * @return corresponding opcode enum or UNKNOWN enum if the value is invalid.
     */
    public static OpCodes fromBytes(byte a, byte b) {
        return fromInt((short) (((short) a) << 8 | (short) (b) & 0x00ff));
    }

    public static OpCodes fromString(String string) {
        for (OpCodes opcode : OpCodes.values())
            if (opcode.name().equals(string))
                return opcode;

        return UNKNOWN;
    }

    /**
     * Extract the opcode from a given encoded message.
     * 
     * @param msg
     * @return the opcode in the message or UNKNOWN if the opcode is unsupported.
     */
    public static OpCodes extractOpcode(byte[] msg) {
        return OpCodes.fromBytes(msg[0], msg[1]);
    }
}
