package impl.tftp;

import java.io.*;
import java.net.Socket;

import api.MessagingProtocol;
import api.MessageEncoderDecoder;

public class TftpClient {
    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            args = new String[] { "localhost", "7777" };

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        Socket sock = new Socket(host, port);

        MessagingProtocol<byte[]> protocol = new TftpProtocol();
        MessageEncoderDecoder<byte[]> encdec = new TftpEncoderDecoder();
        
        KeyboardHandler inputHandler = new KeyboardHandler(sock, protocol);
        Thread keyboardThread = new Thread(inputHandler);

        Runnable listener = new Listener(sock, encdec, protocol, inputHandler);
        Thread listenerThread = new Thread(listener);

        keyboardThread.start();
        listenerThread.start();
    }
}
