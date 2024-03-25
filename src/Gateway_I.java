import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Gateway_I extends Remote{
  public void send(String s) throws RemoteException;
  public void subscribe(Client_I client) throws RemoteException;
  public void unsubscribe(Client_I client) throws RemoteException;
}
