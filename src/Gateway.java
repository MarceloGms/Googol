import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Gateway extends UnicastRemoteObject implements IGatewayCli, IGatewayDl {
  private static final Logger LOGGER = Logger.getLogger(Gateway.class.getName());
  private boolean isRunning;
  private ArrayList<IClient> clients;
  private Queue<String> queue;
  private IDownloader downloaderManager;
  private Semaphore queueSemaphore;
  private Semaphore dmSemaphore;
  private Semaphore dlThreadsSemaphore;
  private Boolean dlThreadsAvailable;

  Gateway() throws RemoteException {
    super();
    isRunning = true;
    clients = new ArrayList<>();
    queue = new LinkedList<>();
    downloaderManager = null;
    queueSemaphore = new Semaphore(0);
    dmSemaphore = new Semaphore(0);
    dlThreadsSemaphore = new Semaphore(1);
    dlThreadsAvailable = true;

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
  public void send(String s , IClient client) throws RemoteException {
    if (isValidURL(s)) {
      queue.add(s);
      LOGGER.info("URL added to the queue: " + s + "\n");
      queueSemaphore.release();
    } else {
      LOGGER.warning("Invalid URL: " + s + "\n");
      client.printOnClient("Invalid URL");
    }
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
    downloaderManager = dm;
    LOGGER.info("Downloader Manager active\n");
    dmSemaphore.release();
  }

  @Override
  public void DlMessage(String s) throws RemoteException {
    if (s.equals("No threads available.")) {
      LOGGER.warning(s + "\n");
      dlThreadsAvailable = false;
    } else if (s.equals("Thread available.")) {
      LOGGER.info(s + "\n");
      dlThreadsSemaphore.release();
    } else {
      LOGGER.info(s + "\n");
    }
  }

  public void run() {
    // handle SIGINT
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      isRunning = false;
      shutdown();
    }));

    // semaphore to wait for the downloader manager to be ready
    try {
      LOGGER.info("Gateway waiting for Downloader Manager to be ready...\n");
      dmSemaphore.acquire();
    } catch (InterruptedException e) {
      LOGGER.log(Level.SEVERE, "InterruptedException occurred: ", e);
    }

    // gateway main loop
    while (isRunning) {
      // semaphore to control the queue
      try {
        if (queue.isEmpty())
          LOGGER.info("Queue is empty. Waiting...\n");
        queueSemaphore.acquire();
      } catch (InterruptedException e) {
        LOGGER.log(Level.SEVERE, "InterruptedException occurred: ", e);
      }

      // semaphore to control the downloader threads
      if (!dlThreadsAvailable) {
        try {
          LOGGER.warning("Waiting for downloader threads to be available...\n");
          dlThreadsSemaphore.acquire();
        } catch (InterruptedException e) {
          LOGGER.log(Level.SEVERE, "InterruptedException occurred: ", e);
        }
        dlThreadsAvailable = true;
      }

      String url = queue.poll();

      // send the URL to the downloader manager
      if (downloaderManager != null && url != null) {
        try {
          LOGGER.info("Gateway sending download request to Downloader Manager: " + url + "\n");
          downloaderManager.download(url);
        } catch (RemoteException e) {
          LOGGER.log(Level.SEVERE, "Error occurred during download: ", e);
        }
      } else {
        LOGGER.warning("Downloader Manager not available or null URL\n");
      }
    }
  }

  private void shutdown() {
    try {
      // send shutdown message to all clients and downloader manager
      for (IClient c : clients) {
        c.printOnClient("Gateway shutting down.");
      }
      // TODO: quando o gateway manda um url para o downloader dps se fizer CTRL+C no gateway, o downloader nao para ns pq
      downloaderManager.send("Gateway shutting down.");
      Naming.unbind("rmi://localhost:1099/gw");
      UnicastRemoteObject.unexportObject(this, true);
      System.out.println("Gateway shutting down...\n");
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Error occurred during shutdown: ", e);
    }
  }

  // check if the URL is valid
  private boolean isValidURL(String url) {
    try {
        new URL(url).toURI();
        return true;
    } catch (Exception e) {
        return false;
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
