import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

public class Gateway extends UnicastRemoteObject implements Gateway_I {
  static Client_I client;

  Gateway() throws RemoteException {
    super();
  }

  @Override
  public void func(String s, Client_I c) throws RemoteException {
		System.out.println(s);
    client = c;
	}

  public static void main(String[] args) {
    try (Scanner sc = new Scanner(System.in)) {
      Registry registry = LocateRegistry.createRegistry(1099);
      Naming.rebind("rmi://localhost:1099/gw", new Gateway());
      System.out.println("Gateway running...");
      while (true) {
				System.out.print("> ");
				String a = sc.nextLine();
				client.func(a);
			}
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
