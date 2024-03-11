package impl.tftp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import api.MessageEncoderDecoder;
import api.MessagingProtocol;

public class Listener implements Runnable{
    private BufferedInputStream in;
    private MessageEncoderDecoder<byte[]> encdec;
    private MessagingProtocol<byte[]> protocol;
    private KeyboardHandler keyboardHandler;

    public Listener(Socket socket, MessageEncoderDecoder<byte[]> encdec, MessagingProtocol<byte[]> protocol, KeyboardHandler keyboardHandler){        
        try {
            this.in = new BufferedInputStream(socket.getInputStream());
        } catch (IOException ignored) {

        }

        this.encdec = encdec;
        this.protocol = protocol;

        this.keyboardHandler = keyboardHandler;
    }

    @Override
    public void run(){
        System.out.println("Starting listening thread...");

        int read;   // current byte
        byte[] nextMessage, response;

        try {
            while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                nextMessage = encdec.decodeNextByte((byte) read);
            
                if (nextMessage != null) {
                    response = protocol.process(nextMessage);
                    keyboardHandler.send(response);
                }
            }
        } catch (IOException ignored) {

        }
    }
}
