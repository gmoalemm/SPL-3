package impl.tftp;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import api.MessagingProtocol;

public class KeyboardHandler implements Runnable {
    private BufferedOutputStream out;
    private BufferedReader in;
    private MessagingProtocol<byte[]> protocol;
    public Object discLock;

    public KeyboardHandler(Socket socket, MessagingProtocol<byte[]> protocol){        
        try {
            this.out = new BufferedOutputStream(socket.getOutputStream());
            this.in = new BufferedReader(new InputStreamReader(System.in));
        } catch (IOException ignored) {
        }

        this.protocol = protocol;

        this.discLock = new Object();
    }

    @Override
    public void run(){
        String message;
        byte[] encodedMessage;
        
        try {
            while (!protocol.shouldTerminate()) {
                message = in.readLine();
                    
                if (message != null) {
                    encodedMessage = encodeMessage(message);
                    protocol.process(encodedMessage);   // response should be null, just inform the thread that we sent this message
                    send(encodedMessage);

                    if (KeyboardHandler.getOpCode(encodedMessage) == OpCodes.DISC){
                        try {
                            synchronized (discLock){
                                discLock.wait();
                            }
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }

            in.close();
            out.close();
        } catch (IOException ignored) {
        }
    }

    public static OpCodes getOpCode(byte[] msg){
        return OpCodes.fromBytes(msg[0], msg[1]);
    }

    private byte[] encodeMessage(String message){
        String[] args = message.split(" ");

        OpCodes code = OpCodes.fromString(args[0]);

        byte[] encodedMessage = null;
        String arg = args.length > 1 ? args[1] : "";

        switch (code) {
            case RRQ:   
                arg = message.substring(args[0].length() + 1);

                if ((new File(arg)).exists()){
                    System.out.println("File already exists!");
                }

                break;
            case WRQ:
                arg = message.substring(args[0].length() + 1);

                if (!(new File(arg)).exists()){
                    System.out.println("File does not exists!");
                }

                break;
            case DELRQ: //
                arg = message.substring(args[0].length() + 1);
            case LOGRQ:
                byte[] encodedArg = arg.getBytes(StandardCharsets.UTF_8);
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
