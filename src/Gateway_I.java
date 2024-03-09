import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Gateway_I extends Remote{
  public void func(String s, Client_I c) throws RemoteException;
}
