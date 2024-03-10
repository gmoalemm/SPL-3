package impl.tftp;

import srv.Server;

public class TftpServer {
    public static void main(String[] args) {

        try {
            Server.threadPerClient(7777, TftpProtocol::new, TftpEncoderDecoder::new).serve();
        } catch (Exception ignored) {
        }
    }
}
