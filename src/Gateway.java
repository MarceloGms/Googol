import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Scanner;

public class Gateway extends UnicastRemoteObject implements Gateway_I {
  // static Client_I client;
  public ArrayList<Client_I> clients;

  Gateway() throws RemoteException {
    super();
    clients = new ArrayList<>();

    LocateRegistry.createRegistry(1099);

		try {
			Naming.rebind("rmi://localhost:1099/gw", (Gateway_I)this);
      System.out.println("Gateway running...");
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		run();
  }

  @Override
  public void printOnServer(String s) throws RemoteException {
		System.out.println(s);
	}

  @Override
  public void subscribe(Client_I c) throws RemoteException {
    clients.add(c);
		System.out.println("client subscribed");
	}

  public void run() {
    // TODO: Implement gw
  }

  public static void main(String[] args) {
    try {
      new Gateway();
    } catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }
}
