package impl.tftp;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import api.MessagingProtocol;

public class KeyboardHandler implements Runnable {
    private BufferedOutputStream out;
    private BufferedReader in;
    private MessagingProtocol<byte[]> protocol;

    public KeyboardHandler(Socket socket, MessagingProtocol<byte[]> protocol){        
        try {
            this.out = new BufferedOutputStream(socket.getOutputStream());
            this.in = new BufferedReader(new InputStreamReader(System.in));
        } catch (IOException ignored) {
        }

        this.protocol = protocol;
    }

    @Override
    public void run(){
        String message;
        byte[] encodedMessage;

        System.out.println("Starting keyboard thread...");
        
        try {
            while (!protocol.shouldTerminate()) {
                System.out.print("< ");
                message = in.readLine();
                
                if (message != null) {
                    encodedMessage = encodeMessage(message);
                    protocol.process(encodedMessage);
                    send(encodedMessage);
                }
            }

            in.close();
            out.close();
        } catch (IOException ignored) {
        }
    }

    private byte[] encodeMessage(String message){
        String[] args = message.split(" ");

        OpCodes code = OpCodes.fromString(args[0]);

        byte[] encodedMessage = null;

        switch (code) {
            case RRQ:
            case WRQ:
            case LOGRQ:
            case DELRQ:
                byte[] encodedArg = args[1].getBytes(StandardCharsets.UTF_8);
                encodedMessage = new byte[3 + encodedArg.length];
                encodedMessage[0] = code.getBytes()[0];
                encodedMessage[1] = code.getBytes()[1];
                encodedMessage[encodedMessage.length - 1] = 0;
                for (int i = 0; i < encodedArg.length; i++)
                     encodedMessage[2 + i] = encodedArg[i]; //copy the argument to the messege.
                break;
            case DIRQ:
            case DISC:
                encodedMessage = code.getBytes();
                break;
            default:
                System.out.println(Errors.NOT_DEFINED.getMessage());
                break;        
        }

        return encodedMessage;
    }

    public synchronized void send(byte[] msg){
        try {
            out.write(msg);
            out.flush();
        } catch (IOException ignored) {

        }
    }
}
