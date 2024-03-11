package impl.tftp;

import java.io.*;
import java.net.Socket;

import api.MessagingProtocol;

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
        
        Runnable inputHandler = new KeyboardHandler(sock, protocol);
        Thread keyboardThread = new Thread(inputHandler);

        keyboardThread.start();
        
    }
}
