import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.text.Normalizer;

public class Downloader extends UnicastRemoteObject implements IDownloader, Runnable {
  private int MAX_THREADS;
  private IGatewayDl gw;
  private Set<String> stopWords;
  private ArrayList<String> ulrsList;
  private ArrayList<String> keywords;
  private Semaphore queueSemaphore;
  private Queue<String> queue;
  private String title;
  private String citation;
  private final String multicastAddress;
  private final int multicastPort;
  private Boolean running;
  private DatagramSocket multicastSocket;
  private final Object multicastLock;
  private String SERVER_IP_ADDRESS;

  Downloader(String multicastAddress, int multicastPort) throws RemoteException {
    super();
    this.multicastAddress = multicastAddress;
    this.multicastPort = multicastPort;
    stopWords = new HashSet<>();
    loadStopWords("assets/stop_words.txt");
    queueSemaphore = new Semaphore(0);
    queue = new ConcurrentLinkedQueue<>();
    running = true;
    multicastLock = new Object();
    try {
      loadConfig();

      // Connect to the Gateway
      try {
        gw = (IGatewayDl) Naming.lookup("rmi://" + SERVER_IP_ADDRESS + ":1099/gw");
        System.out.println("Connected to Gateway.");
      } catch (NotBoundException e) {
        System.err.println("Gateway not bound. Exiting program.");
        System.exit(1);
      } catch (MalformedURLException e) {
        System.err.println("Malformed URL. Exiting program.");
        System.exit(1);
      } catch (RemoteException e) {
        System.err.println("Gateway down. Exiting program.");
        System.exit(1);
      }

      if (!gw.AddDM(this)) {
        System.err.println("Error binding Downloader to Gateway. Exiting program.");
        System.exit(1);
      }

      System.out.println("Downloader bound to Gateway.");

      try {
        multicastSocket = new DatagramSocket();
      } catch (IOException e) {
        System.err.println("Error creating multicast socket: " + e.getMessage());
        System.exit(1);
      }

      // handle SIGINT
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        shutdown();
      }));

      for (int i = 1; i <= MAX_THREADS; i++) {
        Thread thread = new Thread(this, Integer.toString(i));
        thread.start();
      }
    } catch (Exception e) {
      System.err.println("Error occurred during initialization: " + e.getMessage());
      if (gw != null) {
        try {
          gw.RmvDM();
        } catch (RemoteException e1) {
          System.out.println("Error removing Downloader from Gateway: " + e1.getMessage());
        }
      }
    }
  }

  public void run() {
    while (running) {
      try {
        try {
          if (queue.isEmpty()) {
            System.out.println(Thread.currentThread().getName() + ": No URLs to download. Waiting...");
            gw.DlMessage(Thread.currentThread().getName() + ": No URLs to download. Waiting...", "info");
          }
          queueSemaphore.acquire();
        } catch (InterruptedException e) {
          System.out.println("Error occurred while waiting for semaphore: " + e.getMessage());
          shutdown();
        } catch (RemoteException e) {
          System.out.println("Error occurred while sending message to Gateway: " + e.getMessage());
        }
        String url = queue.poll();
        if (url == null) {
          continue;
        }
        System.out.println(Thread.currentThread().getName() + ": Downloading URL: " + url);
        try {
          gw.DlMessage(Thread.currentThread().getName() + ": Downloading URL: " + url, "info");
        } catch (RemoteException e) {
          System.out.println("Error sending message to Gateway: " + e.getMessage());
        }
        extract(url);
        try {
            // Prepare the information to be sent
            String resposta = "URL: " + url + "\nTitle: " + title + "\nCitation: " + citation + "\nKeywords: " + keywords + "\nLinks: " + ulrsList;
            byte[] data = resposta.getBytes();

            // Create a DatagramPacket with the data and the multicast address and port
            InetAddress group = InetAddress.getByName(multicastAddress);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, multicastPort);

            // Send the DatagramPacket via multicast
            synchronized (multicastLock) {
              try {
                  multicastSocket.send(packet);
              } catch (SocketException e) {
                  return;
              }
              System.out.println("Information sent successfully via multicast.");
          }
        } catch (IOException e) {
            System.out.println("IO: " + e.getMessage());
        }
      } catch (RuntimeException e) {
        System.out.println(Thread.currentThread().getName() + "crashed. Restarting...");
        Thread newThread = new Thread(this);
        newThread.setName(Thread.currentThread().getName());
        newThread.start();
        return;
      }
    }
  }

  @Override
  public void download(String url) throws RemoteException {
    queue.offer(url);
    queueSemaphore.release();
    // gw.DlMessage("URL added to the DL queue: " + url);
  }

  // TODO: o downloader ainda nao desliga quando recebe a mensagem de shutdown nao sei porque
  @Override
  public void send(String s) throws RemoteException {
    if (s.equals("Gateway shutting down.")) {
      System.out.println("Received shutdown signal from server. Shutting down...");
      running = false;
      multicastSocket.close();
      try {
        UnicastRemoteObject.unexportObject(this, true);
      } catch (NoSuchObjectException e) {
      }
    } else {
      System.out.println(s);
    }
  }

  private void extract(String url) {
    try {
      Document doc = null;
      try {
        doc = Jsoup.connect(url).get();
      } catch (IllegalArgumentException e) {
        System.err.println("Error: Invalid URL.");
        gw.DlMessage("Error: Invalid URL.", "error");
        return;
      }
      
      String text = doc.text().toLowerCase();
      
      title = doc.title();
      
      citation = doc.select("meta[name=description]").attr("content");
      
      // Extract links
      ulrsList = new ArrayList<>();
      Elements links = doc.select("a[href]");
      for (Element link : links) {
        String linkUrl = link.attr("abs:href");
        ulrsList.add(linkUrl);
        queue.offer(linkUrl);
        queueSemaphore.release();
        // gw.DlMessage("URL added to the DL queue: " + linkUrl);
      }
      
      // Extract keywords
      keywords = new ArrayList<>();
      StringTokenizer tokenizer = new StringTokenizer(text);
      while (tokenizer.hasMoreTokens()) {
        String word = tokenizer.nextToken();
        
        // remove ponctuation
        word = word.replaceAll("\\p{Punct}", "");
        
        // skip stop words
        if (!isStopWord(word))
          keywords.add(normalizeWord(word));
      }
      System.out.println(Thread.currentThread().getName() + ": Download complete for URL: " + url);
      System.out.println("--------------------------------------");
      try {
          gw.DlMessage(Thread.currentThread().getName() + ": Download complete for URL: " + url, "info");
      } catch (RemoteException e) {
          System.out.println("Error sending message to Gateway: " + e.getMessage());
      }

    } catch (IOException e) {
      System.err.println("Error: Failed to extract content from URL. URL may be unreachable.");
      try {
        gw.DlMessage("Error: Failed to extract content from URL. URL may be unreachable.", "error");
      } catch (RemoteException e1) {
        System.out.println("Error sending message to Gateway: " + e1.getMessage());
      }
    }
  }

  private void loadStopWords(String filename) {
    try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String word = line.trim().toLowerCase();
        if (!word.isEmpty()) {
          stopWords.add(normalizeWord(word));
        }
      }
    } catch (IOException e) {
      System.err.println("Error: Failed to load stop words file. Exiting program.");
      System.exit(1);
    }
  }

  private boolean isStopWord(String word) {
    return stopWords.contains(normalizeWord(word));
  }

  // normalize the word by removing accents
  private String normalizeWord(String word) {
    return Normalizer.normalize(word, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase();
  }

  private void loadConfig() {
    Properties prop = new Properties();
    try (FileInputStream input = new FileInputStream("assets/config.properties")) {
      prop.load(input);
      MAX_THREADS = Integer.parseInt(prop.getProperty("downloaders"));
      SERVER_IP_ADDRESS = prop.getProperty("server_ip");
    } catch (IOException ex) {
      System.out.println("Failed to load config file: " + ex.getMessage());
      System.exit(1);
    }
  }

  private void shutdown() {
    try {
        System.out.println("Downloader shutting down...");
        running = false;
        multicastSocket.close();
        // Notify the Gateway about the shutdown
        if (gw != null) {
            gw.RmvDM();
        }
        // Unexport the object
        UnicastRemoteObject.unexportObject(this, true);
    } catch (RemoteException e) {
    }
  }

  public static void main(String args[]) {
    String multicastAddress = "230.0.0.0"; 
    int multicastPort = 12345; 
    
    try {
      new Downloader(multicastAddress, multicastPort);
    } catch (RemoteException e) {
      System.out.println("Error creating Downloader: " + e.getMessage());
    }
  }
}
