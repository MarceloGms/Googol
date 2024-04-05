import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Gateway extends UnicastRemoteObject implements IGatewayCli, IGatewayDl, IGatewayBrl {
  private static final Logger LOGGER = Logger.getLogger(Gateway.class.getName());
  private ArrayList<IClient> clients;
  private ArrayList<IBarrel> barrels;
  private IDownloader downloaderManager;
  private int brlCount;
  private String SERVER_IP_ADDRESS;

  Gateway() throws RemoteException {
    super();
    clients = new ArrayList<>();
    barrels = new ArrayList<>();
    downloaderManager = null;
    brlCount = 0;
    loadConfig();

    try {
      FileHandler fileHandler = new FileHandler("gateway.log");
      fileHandler.setFormatter(new SimpleFormatter());
      LOGGER.addHandler(fileHandler);
      LOGGER.setLevel(Level.INFO);
    } catch (IOException e) {
      System.err.println("Failed to configure logger: " + e.getMessage());
    }

    System.getProperties().put("java.security.policy", "policy.all");

    try {
      LOGGER.info("Gateway starting...\n");
      LocateRegistry.createRegistry(1099);
      LOGGER.info("RMI registry created...\n");
      Naming.rebind("rmi://" + SERVER_IP_ADDRESS + ":1099/gw", this);
      LOGGER.info("Gateway bound to RMI registry on ip: " + SERVER_IP_ADDRESS + "\n");
    } catch (RemoteException | MalformedURLException e) {
      LOGGER.log(Level.SEVERE, "Exception occurred: ", e);
      throw new RuntimeException(e);
    }
  }
  
  // Gateway-Client methods
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

  @Override
  public String getActiveBarrels() throws RemoteException {
    if (brlCount == 0) {
      LOGGER.warning("No barrels available\n");
      return "No barrels available";
    }
    String activeBarrels = "";
    for (IBarrel b : barrels) {
      System.out.println(b.getId() + "\n");
      activeBarrels += b.getId() + "\n";
    }
    return activeBarrels;
  }

  // Gateway-Downloader methods
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

  @Override
  public void DlMessage(String s, String type) throws RemoteException {
    if (type.equals("error"))
      LOGGER.warning(s + "\n");
    else
      LOGGER.info(s + "\n");
  }

  @Override
  public void RmvDM() throws RemoteException {
    if (downloaderManager != null) {
      downloaderManager = null;
      LOGGER.info("Downloader Manager removed\n");
    } else {
      LOGGER.warning("Downloader Manager not found\n");
    }
  }

  // Gateway-Barrel methods
  @Override
  public void AddBrl(IBarrel brl, String id) throws RemoteException {
    barrels.add(brl);
    LOGGER.info("Barrel added: " + id + "\n");
    brlCount++;
  }

  @Override
  public void rmvBrl(IBarrel brl, String id) throws RemoteException {
    if (barrels.remove(brl)) {
      LOGGER.info("Barrel removed: " + id + "\n");
      brlCount--;
    } else {
      LOGGER.warning("Barrel not found\n");
    }
  }

  @Override
  public void BrlMessage(String s) throws RemoteException {
    LOGGER.warning(s + "\n");
  }

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

  // check if the URL is valid
  private boolean isValidURL(String url) {
    try {
        new URL(url).toURI();
        return true;
    } catch (Exception e) {
        return false;
    }
  }

  private void loadConfig() {
    Properties prop = new Properties();
    try (FileInputStream input = new FileInputStream("assets/config.properties")) {
      prop.load(input);
      SERVER_IP_ADDRESS = prop.getProperty("server_ip");
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public static void main(String[] args) {
    try {
      Gateway gateway = new Gateway();

      // handle SIGINT
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        gateway.shutdown();
      }));
    } catch (RemoteException e) {
      LOGGER.log(Level.SEVERE, "Exception occurred: ", e);
      throw new RuntimeException(e);
    }
  }
}
