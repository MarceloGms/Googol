import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * The Gateway class implements the remote interfaces IGatewayCli, IGatewayDl, and IGatewayBrl.
 * It acts as an intermediary between clients, barrels, and downloaders manager in a distributed search engine system.
 * The Gateway class is responsible for handling client requests, managing barrels, and downloaders.
 */
public class Gateway extends UnicastRemoteObject implements IGatewayCli, IGatewayDl, IGatewayBrl {
  /**
   * Logger for the Gateway class.
   */
  private static final Logger LOGGER = Logger.getLogger(Gateway.class.getName());
  /**
   * List of connected clients.
   */
  private ArrayList<IClient> clients;

  /**
   * List of active barrels.
   */
  private ArrayList<IBarrel> barrels;

  /**
   * The downloader manager responsible for handling download requests.
   */
  private IDownloader downloaderManager;

  /**
   * Current number of barrels.
   */
  private int brlCount;

  /**
   * The IP address of the gateway RMI server.
   */
  private String SERVER_IP_ADDRESS;

  /**
   * The port number of the gateway RMI server.
   */
  private int SERVER_PORT;

  /**
   * The ID to assign to the next barrel.
   */
  private int nextId;

  /**
   * Set of available IDs to reuse.
   */
  private Set<Integer> availableIds;

  /**
   * Constructs a Gateway object.
   * Initializes necessary data structures and sets up RMI registry.
   * @throws RemoteException if there is a remote communication error.
   */
  Gateway() throws RemoteException {
    super();
    clients = new ArrayList<>();
    barrels = new ArrayList<>();
    downloaderManager = null;
    brlCount = 0;
    nextId = 1;
    availableIds = new HashSet<>();
    loadConfig();
    initializeLogger();
    System.getProperties().put("java.security.policy", "policy.all");
    bindGatewayToRegistry();
    deleteQueueFile();
  }

  /**
   * Initializes the logger for the Gateway.
   */
  private void initializeLogger() {
    try {
      FileHandler fileHandler = new FileHandler("gateway.log");
      fileHandler.setFormatter(new SimpleFormatter());
      LOGGER.addHandler(fileHandler);
      LOGGER.setLevel(Level.INFO);
    } catch (IOException e) {
      System.err.println("Failed to configure logger: " + e.getMessage());
    }
  }

  /**
   * Binds the Gateway to the RMI registry.
   */
  private void bindGatewayToRegistry() {
    try {
      LOGGER.info("Gateway starting...\n");
      LocateRegistry.createRegistry(SERVER_PORT);
      LOGGER.info("RMI registry created...\n");
      Naming.rebind("rmi://" + SERVER_IP_ADDRESS + ":" + SERVER_PORT + "/gw", this);
      LOGGER.info("Gateway bound to RMI registry on ip: " + SERVER_IP_ADDRESS + "\n");
    } catch (RemoteException | MalformedURLException e) {
      LOGGER.log(Level.SEVERE, "Exception occurred: ", e);
      System.exit(1);
    }
  }
  
  // Gateway-Client methods
  
  /**
   * Sends a download request to the downloaders manager for a given URL.
   * Sends an error message to the client via RMI callback if the URL is invalid or the download manager is not active, or if there are no active barrels available.
   * @param s the URL to download.
   * @param client the interface of the client requesting the download.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public void send(String s , IClient client) throws RemoteException {
    if (isValidURL(s)) {
      if (downloaderManager == null || brlCount < 1) {
        LOGGER.warning("Downloader Manager or Barrels not active\n");
        client.printOnClient("Downloader Manager or barrels not active");
      } else {
        downloaderManager.download(s);
        LOGGER.info("Gateway sending download request to Downloader Manager: " + s + "\n");
      }
    } else {
      LOGGER.warning("Invalid URL: " + s + "\n");
      client.printOnClient("Invalid URL");
    }
	}

  /**
   * Subscribes a client to the Gateway by adding their interface to the clients list.
   * @param c the client interface to subscribe.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public void subscribe(IClient c) throws RemoteException {
    clients.add(c);
    LOGGER.info("Client subscribed\n");
	}

  /**
   * Unsubscribes a client from the Gateway by removing their interface from the clients list.
   * @param c the client interface to unsubscribe.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public void unsubscribe(IClient c) throws RemoteException {
    if (clients.remove(c)) {
      LOGGER.info("Client unsubscribed\n");
    } else {
      LOGGER.warning("Client not found in the subscription list\n");
    }
  }

  /**
   * Gets the search results by choosing a random barrel to perform the search operation.
   * @param s the query string to search for.
   * @return the search results to the client. Returns "No barrels available" if there are no barrels available.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public String search(String s) throws RemoteException {
    Random rand = new Random();
    if (brlCount == 0) {
      LOGGER.warning("No barrels available\n");
      return "No barrels available";
    }
    int idx = rand.nextInt(barrels.size());
    return barrels.get(idx).search(s);
  }

  /**
   * Finds the sub-links of a given URL by choosing a random barrel to perform the operation.
   * @param s the URL to find sub-links for.
   * @return The sub-links of the URL to the client. Returns "Invalid URL" if the URL is invalid
   *         or "No barrels available" if there are no barrels available.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public String findSubLinks(String s) throws RemoteException {
    if (!isValidURL(s)) {
      return "Invalid URL.";
    } else {
      Random rand = new Random();
      if (brlCount == 0) {
        LOGGER.warning("No barrels available\n");
        return "No barrels available";
      }
      int idx = rand.nextInt(barrels.size());
      return barrels.get(idx).findSubLinks(s);
    }
  }

  /**
   * Gets the top 10 searches by choosing a random barrel to perform the operation.
   * @return the top 10 searches to the client.
   * @return "No barrels available" if there are no barrels available.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public String getTop10Searches() throws RemoteException {
    Random rand = new Random();
    if (brlCount == 0) {
      LOGGER.warning("No barrels available\n");
      return "No barrels available";
    }
    int idx = rand.nextInt(barrels.size());
    return barrels.get(idx).getTop10Searches();
  }

  /**
   * Gets the active barrels by returning the IDs of the active barrels.
   * @return the IDs of the active barrels to the client.
   * @return "No barrels available" if there are no active barrels.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public String getActiveBarrels() throws RemoteException {
    if (brlCount == 0) {
      LOGGER.warning("No barrels available\n");
      return "No barrels available";
    }
    String activeBarrels = "";
    for (IBarrel b : barrels) {
      activeBarrels += b.getId() + "\n";
    }
    return activeBarrels;
  }

  // Gateway-Downloader methods

  /**
   * Adds the downloader manager to the Gateway.
   * @param dm the interface downloader manager to add.
   * @return true if the downloader manager is successfully added, false otherwise.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public Boolean AddDM(IDownloader dm) throws RemoteException {
    if (downloaderManager != null) {
      LOGGER.warning("Downloader Manager already active\n");
      return false;
    }
    downloaderManager = dm;
    LOGGER.info("Downloader Manager active\n");
    return true;
  }

  /**
   * Messages from the downloaders manager to be printed on the log.
   * @param s the message sent by the download manager.
   * @param type the type of message (error or info).
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public void DlMessage(String s, String type) throws RemoteException {
    if (type.equals("error"))
      LOGGER.warning(s + "\n");
    else
      LOGGER.info(s + "\n");
  }

  /**
   * Removes the downloader manager from the Gateway.
   * (Commented because works with bugs) Starts a new downloader manager if the current one crashes.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public void RmvDM() throws RemoteException {
    if (downloaderManager != null) {
      downloaderManager = null;
      LOGGER.warning("Downloader Manager crashed\n");
      /* new Downloader();
      LOGGER.info("New Downloader Manager starting...\n"); */
    } else {
      LOGGER.warning("Downloader Manager not found\n");
    }
  }

  // Gateway-Barrel methods

  /**
   * Adds a barrel to the Gateway by adding its iterface to the active barrels list.
   * @param brl the interface of the barrel to add.
   * @return the ID of the barrel added.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public int AddBrl(IBarrel brl) throws RemoteException {
    synchronized (barrels) {
      int currentId;
      if (!availableIds.isEmpty()) { // reuse id if available
          Iterator<Integer> iterator = availableIds.iterator();
          currentId = iterator.next();
          iterator.remove();
      } else { // create new id
          currentId = nextId++;
      }
      barrels.add(brl);
      brlCount++;
      LOGGER.info("Barrel added with ID: " + currentId + "\n");
      return currentId;
    }
  }

  /**
   * Removes a barrel from the Gateway by removing its interface from the active barrels list.
   * @param brl the interface of the barrel to remove.
   * @param id the ID of the barrel to remove.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public void rmvBrl(IBarrel brl, int id) throws RemoteException {
    synchronized (barrels) {
      if (barrels.remove(brl)) {
        LOGGER.warning("Barrel crashed: " + id + "\n");
        brlCount--;
        availableIds.add(id);
      } else {
        LOGGER.warning("Barrel not found\n");
      }
    }
  }

  /**
   * Messages from the barrels to be printed on the log.
   * @param s the message sent by the barrel.
   * @throws RemoteException if there is a remote communication error.
   */
  @Override
  public void BrlMessage(String s) throws RemoteException {
    LOGGER.warning(s + "\n");
  }

  /**
   * Shuts down the Gateway by sending a shutdown message to all clients, barrels, and the downloaders manager.
   * Unbinds the Gateway from the RMI registry and unexports the Gateway object.
   * Currently not working as expected.
   */
  private void shutdown() {
    try {
      LOGGER.info("Gateway shutting down...\n");
      // send shutdown message to all clients, barrels and downloader manager
      if (downloaderManager != null) {
        downloaderManager.send("Gateway shutting down.");
      }
      for (IClient c : clients) {
        c.printOnClient("Gateway shutting down.");
      }
      for (IBarrel b : barrels) {
        b.send("Gateway shutting down.");
      }
      Naming.unbind("rmi://" + SERVER_IP_ADDRESS + ":1099/gw");
      UnicastRemoteObject.unexportObject(this, true);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Error occurred during shutdown: ", e);
    }
  }

  /**
   * Deletes the queue file used by the downloaders manager.
   */
  private void deleteQueueFile() {
    File queueFile = new File("assets/queue.ser");
    if (queueFile.exists()) {
        if (queueFile.delete()) {
        } else {
          LOGGER.warning("Failed to delete queue file.\n");
        }
    }
  }

  /**
   * Checks if a given string is a valid URL.
   * @param url the string to check if it is a valid URL.
   * @return true if the string is a valid URL, false otherwise.
   */
  private boolean isValidURL(String url) {
    try {
        new URL(url).toURI();
        return true;
    } catch (Exception e) {
        return false;
    }
  }

  /**
   * Makes a barrel ID available for reuse.
   * @param id the ID to make available.
   */
  public void makeIdAvailable(int id) {
        availableIds.add(id);
  }

  /**
   * Loads the configuration file to get the server IP address.
   */
  private void loadConfig() {
    Properties prop = new Properties();
    try (FileInputStream input = new FileInputStream("assets/config.properties")) {
      prop.load(input);
      SERVER_IP_ADDRESS = prop.getProperty("server_ip");
      SERVER_PORT = Integer.parseInt(prop.getProperty("server_port"));
    } catch (IOException ex) {
      System.out.println("Failed to load config file: " + ex.getMessage());
      System.exit(1);
    }
  }

  /**
   * The main method to start the Gateway.
   * @param args the command line arguments.
   */
  public static void main(String[] args) {
    try {
      Gateway gateway = new Gateway();

      // handle SIGINT
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        gateway.shutdown();
      }));
      
    } catch (RemoteException e) {
      LOGGER.log(Level.SEVERE, "Exception occurred: ", e);
      System.exit(1);
    }
  }
}
