import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IBarrel extends Remote {
  public void send(String s) throws RemoteException;
}
