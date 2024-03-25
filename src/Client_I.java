import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Client_I extends Remote{
  public void printOnClient(String s) throws RemoteException;
}
