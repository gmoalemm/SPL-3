package api;

import srv.Connections;

/**
 * This interface replaces the MessagingProtocol interface. 
 * It exists to support p2p (peer-to-peer) messaging via the Connections interface.
 */
public interface BidiMessagingProtocol<T>  {
	/**
	 * Used to initiate the current client protocol with its personal
     * connection ID and the connections' implementation.
	**/
    void start(int connectionId, Connections<T> connections);
    
    /** 
     * As in MessagingProtocol, processes a given message. 
     * Unlike MessagingProtocol, responses are sent via the connections 
     * object send functions (if needed).
     */
    void process(T message);
	
	/**
     * @return true if the connection should be terminated.
     */
    boolean shouldTerminate();
}
