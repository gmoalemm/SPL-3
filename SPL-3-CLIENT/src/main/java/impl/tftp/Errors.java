package impl.tftp;

public enum Errors {
    NOT_DEFINED(0),
    FILE_NOT_FOUND(1),
    ACCESS_VIOLATION(2),
    DISC_FULL(3),
    ILLEGAL_OP(4),
    FILE_EXISTS(5),
    NOT_LOGGED_IN(6),
    ALR_LOGGED_IN(7);

    private final short num;

    Errors(int num) {
        this.num = (short) num;
    }

    public byte[] getBytes() {
        return new byte[] { (byte) (num >> 8), (byte) (num & 0xff) };
    }

    public short getShort() {
        return num;
    }

    public static Errors fromInt(int num) {
        for (Errors error : Errors.values()) {
            if (error.num == num) {
                return error;
            }
        }

        return NOT_DEFINED;
    }

    public static Errors fromBytes(byte a, byte b) {
        return fromInt((short) (((short) a) << 8 | (short) (b) & 0x00ff));
    }

    public String getMessage() {
        Errors err = this;

        switch (err) {
            case NOT_DEFINED:
                return "Not defined, see error message (if any).";
            case FILE_NOT_FOUND:
                return "File not found – RRQ DELRQ of non-existing file.";
            case ACCESS_VIOLATION:
                return " Access violation – File cannot be written, read or deleted.";
            case DISC_FULL:
                return "Disk full or allocation exceeded – No room in disk.";
            case ILLEGAL_OP:
                return "Illegal TFTP operation – Unknown Opcode.";
            case FILE_EXISTS:
                return "File already exists – File name exists on WRQ.";
            case NOT_LOGGED_IN:
                return "User not logged in – Any opcode received before Login completes.";
            case ALR_LOGGED_IN:
                return "User already logged in – Login username already connected.";
            default:
                return "Not defined, see error message (if any).";
        }
    }
}
