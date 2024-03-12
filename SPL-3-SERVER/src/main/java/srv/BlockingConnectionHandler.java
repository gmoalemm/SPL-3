package srv;

import api.MessageEncoderDecoder;
import api.BidiMessagingProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {
    private final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;
    private final int id;
    private final Connections<T> connections;

    public BlockingConnectionHandler(int id, Connections<T> connections, Socket sock, MessageEncoderDecoder<T> reader,
            BidiMessagingProtocol<T> protocol) {
        this.id = id;
        this.connections = connections;
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { // just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            connections.connect(id, this);
            protocol.start(id, connections);

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                T nextMessage = encdec.decodeNextByte((byte) read);

                if (nextMessage != null) {
                    protocol.process(nextMessage);
                }
            }

        } catch (IOException ignored) {
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(T msg) {
        // According to Hedi's vid

        // MAKE SURE PACKETS DON'T MIX UP

        try {
            if (msg != null) {
                out.write(encdec.encode(msg));
                out.flush();
            }
        } catch (IOException ignored) {

        }
    }
}
