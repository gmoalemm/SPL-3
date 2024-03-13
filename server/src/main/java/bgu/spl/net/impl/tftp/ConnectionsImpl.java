package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class ConnectionsImpl<T> implements Connections<T> {
    public final ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionsMap = new ConcurrentHashMap<>();

    @Override
    public boolean connect(int connectionId, ConnectionHandler<T> handler) {
        if (connectionsMap.containsKey(connectionId))
            return false;

        connectionsMap.put(connectionId, handler);

        return true;
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if (!connectionsMap.containsKey(connectionId))
            return false;

        ConnectionHandler<T> handler = connectionsMap.get(connectionId);

        handler.send(msg);

        return true;
    }

    @Override
    public void disconnect(int connectionId) {
        connectionsMap.remove(connectionId);
        PublicResources.usersMap.remove(connectionId);
    }

    private static void printBytes(byte[] bytes) {
        System.out.println("SENDING");

        for (byte b : bytes) {
            System.out.println(b + " (" + (new String(new byte[] { b }, StandardCharsets.UTF_8)) + ")");
        }

        System.out.println();
    }
}
