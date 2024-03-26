import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IDownloader extends Remote {
  public void download(String url) throws RemoteException;
  public void send(String s) throws RemoteException;
}
