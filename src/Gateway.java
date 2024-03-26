import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Gateway extends UnicastRemoteObject implements IGatewayCli, IGatewayDl {
  // static Client_I client;
  private static final Logger LOGGER = Logger.getLogger(Gateway.class.getName());

  private boolean isRunning;
  public ArrayList<IClient> clients;
  public Queue<String> queue;
  public IDownloader downloaderManager;
  private Semaphore queueSemaphore;
  private Semaphore dmSemaphore;

  Gateway() throws RemoteException {
    super();
    isRunning = true;
    clients = new ArrayList<>();
    queue = new LinkedList<>();
    downloaderManager = null;
    queueSemaphore = new Semaphore(0);
    dmSemaphore = new Semaphore(0);

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
    } catch (RemoteException | MalformedURLException e) {
      LOGGER.log(Level.SEVERE, "Exception occurred: ", e);
      throw new RuntimeException(e);
    }
    run();
  }
  
  // Gateway-Client methods
  @Override
  public void send(String s) throws RemoteException {
    queue.add(s);
    LOGGER.info("Message added to the queue: " + s + "\n");
    queueSemaphore.release();
	}

  @Override
  public void subscribe(IClient c) throws RemoteException {
    clients.add(c);
    LOGGER.info("Client subscribed\n");
	}

  @Override
  public void unsubscribe(IClient c) throws RemoteException {
    if (clients.remove(c)) {
      LOGGER.info("Client unsubscribed\n");
    } else {
      LOGGER.warning("Client not found in the subscription list\n");
    }
  }

  // Gateway-Downloader methods
  @Override
  public void AddDM(IDownloader dm) throws RemoteException {
    System.out.println("Downloader added to the Gateway.");
    downloaderManager = dm;
    dmSemaphore.release();
  }

  public void run() {
    // handle SIGINT
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      isRunning = false;
      shutdown();
    }));

    // semaphore to wait for the downloader manager to be ready
    try {
      dmSemaphore.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // gateway main loop
    while (isRunning) {
      if (queue.isEmpty()) {
        // semaphore to wait for a message to be added to the queue
        try {
          queueSemaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
      }

      String url = queue.poll();

      if (downloaderManager != null && url != null) {
        try {
          LOGGER.info("Gateway sending download request to Downloader Manager: " + url + "\n");
          downloaderManager.download(url);
        } catch (RemoteException e) {
          LOGGER.log(Level.SEVERE, "Error occurred during download: ", e);
        }
      }
    }
  }

  public void shutdown() {
    try {
      // send shutdown message to all clients and downloader manager
      for (IClient c : clients) {
        c.printOnClient("Gateway shutting down.");
      }
      // TODO: quando o gateway manda um url para o downloader dps se fizer CTRL+C no gateway, o downloader nao para
      downloaderManager.send("Gateway shutting down.");
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
