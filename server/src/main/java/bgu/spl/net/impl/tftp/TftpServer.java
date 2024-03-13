package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args) {

        try {
            Server.threadPerClient(7777, TftpProtocol::new, TftpEncoderDecoder::new).serve();
        } catch (Exception ignored) {
        }
    }
}
