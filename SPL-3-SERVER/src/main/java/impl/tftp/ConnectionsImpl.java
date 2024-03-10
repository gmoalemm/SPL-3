package impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

import srv.ConnectionHandler;
import srv.Connections;

public class ConnectionsImpl<T> implements Connections<T> {
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionsMap = new ConcurrentHashMap<>();

    @Override
    public boolean connect(int connectionId, ConnectionHandler<T> handler) {
        if (connectionsMap.containsKey(connectionId)) return false;

        connectionsMap.put(connectionId, handler);

        return true;
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if (!connectionsMap.containsKey(connectionId)) return false;

        ConnectionHandler<T> handler = connectionsMap.get(connectionId);

        handler.send(msg);

//        // TODO Auto-generated method stub
//        throw new UnsupportedOperationException("Unimplemented method 'send'");

        return true;
    }

    @Override
    public void disconnect(int connectionId) {
        connectionsMap.remove(connectionId);
        LoggedUsers.usersMap.remove(connectionId);
    }
}
