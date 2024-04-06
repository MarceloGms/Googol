import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * This interface allows the Barrels to communicate to the Gateway via RMI.
 */
public interface IGatewayBrl extends Remote {
  /**
   * Adds a barrel to the gateway.
   * @param brl The barrel to add.
   * @return The ID assigned to the added barrel.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public int AddBrl(IBarrel brl) throws RemoteException;

  /**
   * Removes a barrel from the gateway.
   * @param brl The barrel to remove.
   * @param id  The ID of the barrel to remove.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void rmvBrl(IBarrel brl, int id) throws RemoteException;

  /**
   * Sends error messages from the Barrels to print on the Gateway log.
   * @param s The message to send.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void BrlMessage(String s) throws RemoteException;
}
