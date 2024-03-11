package impl.tftp;

import api.MessageEncoderDecoder;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class TftpClient {
    // TODO: implement the main logic of the client, when using a thread per client
    // the main logic goes here
    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            args = new String[] { "localhost", "7777" };

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final Socket sock = new Socket(host, port);

        Thread keyboardThread = new Thread(() -> {
            // BufferedReader and BufferedWriter automatically using UTF-8 encoding
            try (BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream())) {
                System.out.println("Starting keyboard thread...");
                MessageEncoderDecoder<byte[]> encoderDecoder = new TftpEncoderDecoder();

                while (true) {
                    String message;
                    byte[] encodedMsg;
                    Scanner input = new Scanner(System.in);

                    System.out.print("< ");
                    message = input.nextLine();

                    encodedMsg = encoderDecoder.encode(message);

                    out.write(encodedMsg);
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        });

        Thread listeningThread = new Thread(() -> {
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
                    BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()))) {
                // MessageEncoderDecoder<String> encoderDecoder = new TftpEncoderDecoder();

                System.out.println("Starting listening thread...");

                while (true) {
                    System.out.print("> ");
                    String message = in.readLine(); // TODO: change the function
                    System.out.println(message);
                }
            } catch (IOException ignored) {
            }
        });

        listeningThread.start();
        while (!listeningThread.isAlive()) {
        }
        keyboardThread.start();
    }
}
