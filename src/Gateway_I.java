import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Gateway_I extends Remote{
  public void printOnServer(String s) throws RemoteException;
  public void subscribe(Client_I client) throws RemoteException;
}
