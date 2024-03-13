import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Client_I extends Remote{
  public int func(String s) throws RemoteException;
}
