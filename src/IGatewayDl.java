import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGatewayDl  extends Remote {
  public void AddDM(IDownloader dm) throws RemoteException;
  public void DlMessage(String s, String type) throws RemoteException;
}
