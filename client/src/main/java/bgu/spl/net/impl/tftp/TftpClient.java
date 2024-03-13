package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.net.Socket;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;

public class TftpClient {
    // TODO: implement the main logic of the client, when using a thread per client
    // the main logic goes here
    public static void main(String[] args) {
        if (args.length == 0)
            args = new String[] { "localhost", "7777" };

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        Socket sock;

        try {
            sock = new Socket(host, port);

            MessagingProtocol<byte[]> protocol = new TftpProtocol();
            MessageEncoderDecoder<byte[]> encdec = new TftpEncoderDecoder();

            KeyboardHandler inputHandler = new KeyboardHandler(sock, protocol);
            Thread keyboardThread = new Thread(inputHandler);

            Runnable listener = new Listener(sock, encdec, protocol, inputHandler);
            Thread listenerThread = new Thread(listener);

            keyboardThread.start();
            listenerThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
