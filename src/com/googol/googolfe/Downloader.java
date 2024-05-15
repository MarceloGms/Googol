package com.googol.googolfe;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

/**
 * The Downloader class implements the IDownloader interface and is responsible for downloading
 * web pages, extracting information such as title, citation, links, and keywords, and then
 * broadcasting this information via multicast to the barrels. It operates as a downloaders manager
 * that creates threads to download web pages concurrently.
 * The Downloaders Manager has a queue of URLs shared by all threads.
 */
public class Downloader extends UnicastRemoteObject implements IDownloader, Runnable {
  /**
 * Maximum number of downloader threads.
 */
  private int MAX_THREADS;

  /**
   * The remote gateway interface used for communication with the gateway.
   */
  private IGatewayDl gw;

  /**
   * Set of stop words used in text processing.
   */
  private Set<String> stopWords;

  /**
   * List of extracted URLs.
   */
  private ArrayList<String> urlsList;

  /**
   * List extracted keywords.
   */
  private ArrayList<String> keywords;

  /**
   * Semaphore for controlling access to the download queue.
   */
  private Semaphore queueSemaphore;

  /**
   * Queue of URLs to be downloaded.
   */
  private Queue<String> queue;

  /**
   * Title of the document being downloaded.
   */
  private String title;

  /**
   * Citation information for the document being downloaded.
   */
  private String citation;

  /**
   * Flag indicating whether the downloader is running.
   */
  private Boolean running;

  /**
   * Multicast socket for communication.
   */
  private DatagramSocket multicastSocket;

  /**
   * Lock object for multicast operations.
   */
  private final Object multicastLock;

  /**
   * The IP address of the gateway RMI server.
   */
  private String SERVER_IP_ADDRESS;

  /**
   * The port of the gateway RMI server.
   */
  private String SERVER_PORT;

  /**
   * The multicast address to send information.
   */
  private String MULTICAST_ADDR;

  /**
   * The multicast port to send information.
   */
  private int MULTICAST_PORT;

  /**
   * Constructs a Downloader object with the given multicast address and port.
   * Connects to the Gateway and creates threads to download web pages concurrently.
   * @throws RemoteException If a communication-related exception occurs.
   */
  Downloader() throws RemoteException {
    super();
    stopWords = new HashSet<>();
    loadStopWords("assets/stop_words.txt");
    queueSemaphore = new Semaphore(0);
    queue = new ConcurrentLinkedQueue<>();
    running = true;
    multicastLock = new Object();

    try {
      // Load configuration
      loadConfig();
      // Connect to the Gateway
      connectToGateway();
      // Restore the queue from file if it exists
      restoreQueueFromFile();

      // Bind Downloaders Manager to Gateway
      if (!gw.AddDM(this)) {
        System.err.println("Error binding Downloader to Gateway. Exiting program.");
        System.exit(1);
      }

      System.out.println("Downloader bound to Gateway.");

      // Create a multicast socket
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

      // Create and start downloader threads
      createDownloaderThreads();

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

  /**
   * Connects to the Gateway using RMI.
   */
  private void connectToGateway() {
    try {
      gw = (IGatewayDl) Naming.lookup("rmi://" + SERVER_IP_ADDRESS + ":" + SERVER_PORT + "/gw");
      System.out.println("Connected to Gateway.");
    } catch (NotBoundException | MalformedURLException | RemoteException e) {
      System.err.println("Error connecting to the Gateway: " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Creates and starts downloader threads.
   */
  private void createDownloaderThreads() {
    for (int i = 1; i <= MAX_THREADS; i++) {
      Thread thread = new Thread(this, Integer.toString(i));
      thread.start();
    }
  }

  /**
   * Restores the queue from file if it exists.
   */
  private void restoreQueueFromFile() {
    File queueFile = new File("assets/queue.ser");
    if (queueFile.exists()) {
      try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream("assets/queue.ser"))) {
        @SuppressWarnings("unchecked")
        ArrayList<String> savedQueue = (ArrayList<String>) inputStream.readObject();
        queue.addAll(savedQueue);
        queueSemaphore.release(savedQueue.size()); // Release semaphore permits
        System.out.println("Queue contents restored from file assets/queue.ser");
        gw.DlMessage("Queue contents restored from file assets/queue.ser", "info");
      } catch (IOException | ClassNotFoundException e) {
        System.err.println("Error loading queue contents from file: " + e.getMessage());
      }
    }
  }

  /**
   * Executes the downloader thread's main task.
   * Downloads web pages concurrently and extracts information such as title, citation, links, and
   * keywords. Broadcasts this information via multicast to the barrels.
   */
  public void run() {
    while (running) {
      try {
        try {
          // Wait for a URL to download
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
        }
        // Extract information from the URL
        extract(url);
        try {
            // Prepare the information to be sent
            String message = "URL: " + url + "\nTitle: " + title + "\nCitation: " + citation + "\nKeywords: " + keywords + "\nLinks: " + urlsList;
            byte[] data = message.getBytes();

            // Create a DatagramPacket with the data and the multicast address and port
            InetAddress group = InetAddress.getByName(MULTICAST_ADDR);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, MULTICAST_PORT);

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

  /**
   * Adds a URL to the download queue.
   * @param url The URL to download.
   * @throws RemoteException If a communication-related exception occurs.
   */
  @Override
  public void download(String url) throws RemoteException {
    queue.offer(url);
    queueSemaphore.release();
    // gw.DlMessage("URL added to the DL queue: " + url);
  }

  /**
   * Receives a message from the Gateway.
   * If the message is a shutdown signal, shuts down the Downloader.
   * @param s The message received.
   * @throws RemoteException If a communication-related exception occurs.
   */
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
  
  /**
   * Extracts information such as title, citation, links, and keywords from the given URL.
   * @param url The URL to extract information from.
   */
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
      urlsList = new ArrayList<>();
      Elements links = doc.select("a[href]");
      for (Element link : links) {
        String linkUrl = link.attr("abs:href");
        urlsList.add(linkUrl);
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
      }

    } catch (IOException e) {
      System.err.println("Error: Failed to extract content from URL. URL may be unreachable.");
      try {
        gw.DlMessage("Error: Failed to extract content from URL. URL may be unreachable.", "error");
      } catch (RemoteException e1) {
      }
    }
  }

  /**
   * Loads stop words from the specified file.
   * @param filename The path to the file containing stop words.
   */
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

  /**
   * Checks if a word is a stop word.
   * @param word The word to check.
   * @return True if the word is a stop word, otherwise false.
   */
  private boolean isStopWord(String word) {
    return stopWords.contains(normalizeWord(word));
  }

  /**
   * Normalizes a word by removing accents.
   * @param word The word to normalize.
   * @return The normalized word.
   */
  private String normalizeWord(String word) {
    return Normalizer.normalize(word, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase();
  }

  /**
   * Loads configuration settings from the properties file.
   */
  private void loadConfig() {
    Properties prop = new Properties();
    try (FileInputStream input = new FileInputStream("assets/config.properties")) {
      prop.load(input);
      MAX_THREADS = Integer.parseInt(prop.getProperty("downloaders"));
      SERVER_IP_ADDRESS = prop.getProperty("server_ip");
      SERVER_PORT = prop.getProperty("server_port");
      MULTICAST_ADDR = prop.getProperty("multicast_ip");
      MULTICAST_PORT = Integer.parseInt(prop.getProperty("multicast_port"));
    } catch (IOException ex) {
      System.out.println("Failed to load config file: " + ex.getMessage());
      System.exit(1);
    }
  }

  /**
   * Shuts down the downloader, releases resources, and notifies the Gateway.
   * Saves the queue contents to a file in case of a crash.
   * The Downloaders Manager can restore the queue from this file when it restarts.
   * The Gateway deletes this file when it restarts.
   */
  private void shutdown() {
    try {
        System.out.println("Downloader shutting down...");
        if (queue.isEmpty()) {
          System.out.println("Queue is empty. No URLs to save.");
        } else {
          try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream("assets/queue.ser"))) {
            outputStream.writeObject(new ArrayList<>(queue));
            System.out.println("Queue contents saved to file: assets/queue.ser");
          } catch (IOException e) {
              System.err.println("Error saving queue contents to file: " + e.getMessage());
          }
        }
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

  /**
   * The main method creates a Downloader object with the specified multicast address and port.
   * @param args The command line arguments.
   */
  public static void main(String args[]) { 
    try {
      new Downloader();
    } catch (RemoteException e) {
      System.out.println("Error creating Downloader: " + e.getMessage());
    }
  }
}
