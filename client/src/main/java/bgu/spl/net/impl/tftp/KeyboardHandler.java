package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import bgu.spl.net.api.MessagingProtocol;

public class KeyboardHandler implements Runnable {
    private BufferedOutputStream out;
    private BufferedReader in;
    private MessagingProtocol<byte[]> protocol;
    public Object discLock; // used to lick the thread when the user wants to disconnect

    public KeyboardHandler(Socket socket, MessagingProtocol<byte[]> protocol) {
        try {
            this.out = new BufferedOutputStream(socket.getOutputStream());
            this.in = new BufferedReader(new InputStreamReader(System.in));
        } catch (IOException ignored) {
        }

        this.protocol = protocol;
        this.discLock = new Object();
    }

    @Override
    public void run() {
        String command; // the command from the user
        byte[] encodedCommand;

        try {
            while (!protocol.shouldTerminate()) {
                command = in.readLine();

                if (command != null) {
                    encodedCommand = encodeCommand(command);

                    if (encodedCommand != null) {
                        protocol.process(encodedCommand); // response should be null, just inform the thread that we
                                                          // sent this message
                        send(encodedCommand);

                        // if the user requested to disconnect, wait until the server send an answer
                        // that way, the thread won't get stuck on readline
                        if (OpCodes.extractOpcode(encodedCommand) == OpCodes.DISC) {
                            try {
                                synchronized (discLock) {
                                    discLock.wait();
                                }
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                }
            }

            in.close();
            out.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * Check if a command argument is valid.
     * 
     * @param arg the encoded argument.
     * @return true iff the arg. is valid, false otherwise.
     */
    private boolean argumentIsValid(byte[] arg) {
        if (arg != null && arg.length > 0) {
            for (byte b : arg)
                if (b == 0)
                    return false;

            return true;
        }

        return false;
    }

    /**
     * 
     * @param command
     * @return
     */
    private byte[] encodeCommand(String command) {
        String[] args = command.split(" ");
        OpCodes code = OpCodes.fromString(args[0]);
        byte[] encodedCommand = null;
        String arg = args.length > 1 ? command.substring(args[0].length() + 1) : "";
        byte[] encodedArg = arg.getBytes(StandardCharsets.UTF_8);

        switch (code) {
            case RRQ:
                if (!argumentIsValid(encodedArg))
                    System.out.println("Invalid filename");
                else if ((new File(arg)).exists())
                    System.out.println("File already exists!");
                else
                    encodedCommand = encapsulate(encodedArg, code);
                break;
            case WRQ:
                if (!argumentIsValid(encodedArg))
                    System.out.println("Invalid filename");
                else if (!(new File(arg)).exists())
                    System.out.println("File does not exists!");
                else
                    encodedCommand = encapsulate(encodedArg, code);
                break;
            case DELRQ:
                if (!argumentIsValid(encodedArg))
                    System.out.println("Invalid filename");
                else
                    encodedCommand = encapsulate(encodedArg, code);
                break;
            case LOGRQ:
                if (!argumentIsValid(encodedArg))
                    System.out.println("Invalid username");
                else if ((new File(arg)).exists())
                    System.out.println("File does not exist!");
                else
                    encodedCommand = encapsulate(encodedArg, code);
                break;
            case DIRQ:
            case DISC:
                encodedCommand = code.getBytes();
                break;
            default:
                System.out.println(Errors.ILLEGAL_OP.getMessage());
                break;
        }

        return encodedCommand;
    }

    /**
     * Create an encoded command from an argument and an opcode.
     * 
     * @param encodedArg
     * @param code
     * @return encoded command packet.
     */
    private byte[] encapsulate(byte[] encodedArg, OpCodes code) {
        byte[] encodedMessage = new byte[3 + encodedArg.length];

        encodedMessage[0] = code.getBytes()[0];
        encodedMessage[1] = code.getBytes()[1];
        encodedMessage[encodedMessage.length - 1] = 0;

        for (int i = 0; i < encodedArg.length; i++)
            encodedMessage[2 + i] = encodedArg[i]; // copy the argument to the messege.

        return encodedMessage;
    }

    /**
     * Send a message ro the server.
     * 
     * @param msg
     */
    public synchronized void send(byte[] msg) {
        try {
            // printBytes(msg);
            out.write(msg);
            out.flush();
        } catch (IOException ignored) {

        }
    }

    /**
     * Helper. Print an array of bytes and their char representation.
     * 
     * @param bytes
     */
    public static void printBytes(byte[] bytes) {
        System.out.println("SENDING");
        for (byte b : bytes) {
            System.out.println(b + " (" + (new String(new byte[] { b }, StandardCharsets.UTF_8)) + ")");
        }
        System.out.println();
    }
}
