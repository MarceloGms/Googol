import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Gateway extends UnicastRemoteObject implements Gateway_I {
  // static Client_I client;
  public ArrayList<Client_I> clients;
  private static final Logger LOGGER = Logger.getLogger(Gateway.class.getName());

  Gateway() throws RemoteException {
    super();
    clients = new ArrayList<>();

    try {
      FileHandler fileHandler = new FileHandler("gateway.log");
      fileHandler.setFormatter(new SimpleFormatter());
      LOGGER.addHandler(fileHandler);
      LOGGER.setLevel(Level.INFO);
    } catch (IOException e) {
      System.err.println("Failed to configure logger: " + e.getMessage());
    }

    try {
      LOGGER.info("Gateway starting...\n");
      LocateRegistry.createRegistry(1099);
      LOGGER.info("RMI registry created...\n");
      Naming.rebind("rmi://localhost:1099/gw", this);
      LOGGER.info("Gateway bound to RMI registry...\n");
      run();
    } catch (RemoteException | MalformedURLException e) {
      LOGGER.log(Level.SEVERE, "Exception occurred: ", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void printOnServer(String s) throws RemoteException {
		LOGGER.info("Received message from client: " + s + "\n");
	}

  @Override
  public void subscribe(Client_I c) throws RemoteException {
    clients.add(c);
    LOGGER.info("Client subscribed\n");
	}

  @Override
  public void unsubscribe(Client_I c) throws RemoteException {
    if (clients.remove(c)) {
      LOGGER.info("Client unsubscribed\n");
    } else {
      LOGGER.warning("Client not found in the subscription list\n");
    }
  }

  public void run() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      shutdown();
    }));
    // TODO: Implement gw
  }

  public void shutdown() {
    try {
      for (Client_I c : clients) {
        c.printOnClient("Gateway shutting down.");
      }
      Naming.unbind("rmi://localhost:1099/gw");
      UnicastRemoteObject.unexportObject(this, true);
      System.out.println("Gateway shutting down...\n");
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Error occurred during shutdown: ", e);
    }
  }

  public static void main(String[] args) {
    try {
      new Gateway();
    } catch (RemoteException e) {
      LOGGER.log(Level.SEVERE, "Exception occurred: ", e);
      throw new RuntimeException(e);
    }
  }
}
