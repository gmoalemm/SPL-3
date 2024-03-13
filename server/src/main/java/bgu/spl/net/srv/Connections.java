package bgu.spl.net.srv;

import java.io.IOException;

/**
 * Implement Connections<T> to hold a list of the new ConnectionHandler
 * interface for each active client. Use it to implement the interface
 * functions.
 * Notice that given a Connections implementation, any protocol should run.
 * This means that you keep your implementation of Connections on T.
 */
public interface Connections<T> {
    /**
     * Add an client connectionId to active client map.
     * 
     * @param connectionId a client.
     * @param handler
     * @return true iff a new connecion has been made.
     * @note the original return type was void.
     */
    boolean connect(int connectionId, ConnectionHandler<T> handler);

    /**
     * Sends a message T to the client represented by the given connectionId.
     * 
     * @param connectionId a client.
     * @param msg          a message to the client.
     * @note should call ConnectionHandler.send().
     * @return
     */
    boolean send(int connectionId, T msg);

    /**
     * Removes an active client connectionId from the map.
     * 
     * @param connectionId a client to remove.
     */
    void disconnect(int connectionId);
}
