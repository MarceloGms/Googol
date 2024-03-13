import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

public class Client extends UnicastRemoteObject implements Client_I {
  private int id;

  Client(int id) throws RemoteException{
    super();
    this.id = id;
  }

  @Override
  public int func(String s) throws RemoteException {
    System.out.println(s);
    return 0;
  }

  
  
  public static void main(String[] args) {
    int n = 0;
    
    // client id block
    try {
      if (args.length > 0) {
        n = Integer.parseInt(args[0]);
      } else {
        System.out.println("usage: make cli id=n");
        System.exit(1);
      }
    } catch (NumberFormatException e) {
      System.err.println("Invalid integer format: " + args[0]);
      System.exit(1);
    }

    // connect and send message
    try (Scanner sc = new Scanner(System.in)){
      Gateway_I gw = (Gateway_I) Naming.lookup("rmi://localhost:1099/gw");
      Client c = new Client(n);
      String m = sc.nextLine();
      gw.func(m, c);
      sc.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public String toString() {
    return "id=" + id;
  }
  
}
