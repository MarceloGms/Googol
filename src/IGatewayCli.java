import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGatewayCli extends Remote{
  public void send(String s, IClient client) throws RemoteException;
  public void subscribe(IClient client) throws RemoteException;
  public void unsubscribe(IClient client) throws RemoteException;
  public String search(String s) throws RemoteException;
  public String findSubLinks(String s) throws RemoteException;
  public String getTop10Searches() throws RemoteException;
  public String getActiveBarrels() throws RemoteException;
}
