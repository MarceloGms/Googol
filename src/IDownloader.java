import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The interface for the Downloaders Manager in the search engine system.
 * This interface allows the Gateway to connect to the Downloader via RMI-callback.
 */
public interface IDownloader extends Remote {
  /**
   * Adds the URL to the URLs queue.
   * @param url The URL to download.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void download(String url) throws RemoteException;

  /**
   * For the Gateway sending messages to the Downloaders Manager, mainly the shutdown message.
   * @param s The message to send.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void send(String s) throws RemoteException;
}