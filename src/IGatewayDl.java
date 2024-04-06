import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface allows the Downloaders Manager to connect to the Gateway via RMI.
 */
public interface IGatewayDl  extends Remote {
  /**
   * Adds the Downloaders Manager to the gateway.
   * @param dm The downloader to add.
   * @return True if the downloader was added successfully, false otherwise.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public Boolean AddDM(IDownloader dm) throws RemoteException;

  /**
   * Removes the Downloaders Manager from the gateway.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void RmvDM() throws RemoteException;

  /**
   * Sends error messages from the Downloaders Manager to print on the Gateway log.
   * @param s The message to send.
   * @param type The type of message to send.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void DlMessage(String s, String type) throws RemoteException;
}
