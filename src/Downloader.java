import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.text.Normalizer;

public class Downloader extends UnicastRemoteObject implements IDownloader, Runnable {
  private final int MAX_THREADS;
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

  Downloader(int MAX_THREADS, String multicastAddress, int multicastPort) throws RemoteException {
    super();
    this.MAX_THREADS = MAX_THREADS;
    this.multicastAddress = multicastAddress;
    this.multicastPort = multicastPort;
    stopWords = new HashSet<>();
    loadStopWords("assets/stop_words.txt");
    queueSemaphore = new Semaphore(0);
    queue = new ConcurrentLinkedQueue<>();
    running = true;

    // Connect to the Gateway
    try {
      gw = (IGatewayDl) Naming.lookup("rmi://localhost:1099/gw");
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

    gw.AddDM(this);
    System.out.println("Downloader bound to Gateway.");

    for (int i = 1; i <= MAX_THREADS; i++) {
      Thread thread = new Thread(this, Integer.toString(i));
      thread.start();
    }
  }

  public void run() {
    while (running) {
      try {
        if (queue.isEmpty()) {
          System.out.println(Thread.currentThread().getName() + ": No URLs to download. Waiting...");
          gw.DlMessage(Thread.currentThread().getName() + ": No URLs to download. Waiting...");
        }
        queueSemaphore.acquire();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (RemoteException e) {
        e.printStackTrace();
      }
      String url = queue.poll();
      if (url == null) {
        continue;
      }
      System.out.println(Thread.currentThread().getName() + ": Downloading URL: " + url);
      try {
        gw.DlMessage(Thread.currentThread().getName() + ": Downloading URL: " + url);
      } catch (RemoteException e) {
        e.printStackTrace();
      }
      extract(url);
      System.out.println(Thread.currentThread().getName() + ": Download complete for URL: " + url);
      System.out.println("--------------------------------------");
      try {
          gw.DlMessage(Thread.currentThread().getName() + ": Download complete for URL: " + url);
      } catch (RemoteException e) {
          e.printStackTrace();
      }

      try (DatagramSocket multicastSocket = new DatagramSocket()) {
          // Prepare the information to be sent
          String resposta = "URL: " + url + "\nTitle: " + title + "\nCitation: " + citation + "\nKeywords: " + keywords + "\nLinks: " + ulrsList;
          byte[] data = resposta.getBytes();

          // Create a DatagramPacket with the data and the multicast address and port
          InetAddress group = InetAddress.getByName(multicastAddress);
          DatagramPacket packet = new DatagramPacket(data, data.length, group, multicastPort);

          // Send the DatagramPacket via multicast
          multicastSocket.send(packet);

          System.out.println("Information sent successfully via multicast.");
      } catch (IOException e) {
          System.out.println("IO: " + e.getMessage());
      }
    }
  }

  @Override
  public void download(String url) throws RemoteException {
    queue.add(url);
    queueSemaphore.release();
    // gw.DlMessage("URL added to the DL queue: " + url);
  }

  // TODO: o downloader ainda nao desliga quando recebe a mensagem de shutdown nao sei porque
  @Override
  public void send(String s) throws RemoteException {
    if (s.equals("Gateway shutting down.")) {
      running = false;
      System.out.println("Received shutdown signal from server. Shutting down...");
      try {
          UnicastRemoteObject.unexportObject(this, true);
      } catch (NoSuchObjectException e) {
      }
      System.exit(0);
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
        System.err.println("Error: Invalid URL. Exiting program.");
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
        queue.add(linkUrl);
        queueSemaphore.release();
        // gw.DlMessage("URL added to the DL queue: " + linkUrl);
      }
      
      // Extract keywords
      keywords = new ArrayList<>();
      StringTokenizer tokenizer = new StringTokenizer(text);
      while (tokenizer.hasMoreTokens()) {
        String word = tokenizer.nextToken();

        // remove ponctuation
        word = word.replaceAll("[^a-zA-Z0-9]", "");
        
        // skip stop words
        if (!isStopWord(word))
          keywords.add(normalizeWord(word));
      }
      
      /* System.out.println("URL: " + url);
      System.out.println("Title: " + title);
      System.out.println("Citation: " + citation);
      System.out.println("Keywords: " + keywords);
      System.out.println("Links: " + ulrsList); */

    } catch (IOException e) {
      System.err.println("Error: Failed to extract content from URL. URL may be unreachable.");
      e.printStackTrace();
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

  public static void main(String args[]) {

    // Get number of threads
    int n = 0;
    try {
      n = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      System.err.println("Invalid number of threads: " + args[0]);
      System.exit(1);
    }

    String multicastAddress = "230.0.0.0"; // Example multicast address
    int multicastPort = 12345; // Example multicast port
    
    try {
      new Downloader(n, multicastAddress, multicastPort);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }
}
