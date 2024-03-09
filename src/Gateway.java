import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Gateway extends UnicastRemoteObject implements Gateway_I {

  Gateway() throws RemoteException {
    super();
  }

  @Override
  public void func(String s, Client_I c) throws RemoteException {
		System.out.println("> " + s);
	}

  public static void main(String[] args) {
    try {
      Registry registry = LocateRegistry.createRegistry(1099);
      Naming.rebind("rmi://localhost:1099/gw", new Gateway());
      System.out.println("Gateway running...");
    } catch (RemoteException | MalformedURLException e) {
      e.printStackTrace();
    }
  }
}
