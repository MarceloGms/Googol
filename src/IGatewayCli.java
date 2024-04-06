import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface allows the Clients to connect to the Gateway via RMI.
 */
public interface IGatewayCli extends Remote{
  /**
   * Sends a download request to the downloaders manager for a given URL.
   * @param s The message to send.
   * @param client The client to send the message to.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void send(String s, IClient client) throws RemoteException;

  /**
   * Subscribes a client to the gateway.
   * @param client The client to subscribe.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void subscribe(IClient client) throws RemoteException;

  /**
   * Unsubscribes a client from the gateway.
   * @param client The client to unsubscribe.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void unsubscribe(IClient client) throws RemoteException;

  /**
   * Searches for a given query string.
   * @param s The query string to search for.
   * @return The search results.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public String search(String s) throws RemoteException;

  /**
   * Finds sub-links for a given URL.
   * @param s The URL to find sub-links for.
   * @return The sub-links found.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public String findSubLinks(String s) throws RemoteException;

  /**
   * Gets the top 10 searches.
   * @return The top 10 searches.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public String getTop10Searches() throws RemoteException;

  /**
   * Gets the active barrels.
   * @return The active barrels.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public String getActiveBarrels() throws RemoteException;
}
