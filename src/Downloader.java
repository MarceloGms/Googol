import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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
  private int id;
  private int MAX_THREADS;
  private IGatewayDl gw;
  private Set<String> stopWords;
  private ArrayList<String> ulrsList;
  private ArrayList<String> keywords;
  private Semaphore threadsSemaphore;
  private Queue<String> queue;

  Downloader(int MAX_THREADS) throws RemoteException {
    super();
    this.MAX_THREADS = MAX_THREADS;
    stopWords = new HashSet<>();
    loadStopWords("assets/stop_words.txt");
    threadsSemaphore = new Semaphore(MAX_THREADS);
    queue = new ConcurrentLinkedQueue<>();

    // Connect to the Gateway
    try {
      gw = (IGatewayDl) Naming.lookup("rmi://localhost:1099/gw");
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
  }

  public void run() {
    String url = queue.poll();
    extract(url);
    System.out.println("Download complete for URL: " + url);
    System.out.println("--------------------------------------");
    threadsSemaphore.release();
  }

  @Override
  public void download(String url) throws RemoteException {
    queue.add(url);
    if (threadsSemaphore.availablePermits() == 0) {
      System.out.println("No threads available. Waiting for one to be free...");
      System.out.println("--------------------------------------");
      gw.message("No threads available. Waiting for one to be free...");
      try {
        threadsSemaphore.acquire();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      System.out.println("Thread available.");
      System.out.println("--------------------------------------");
      gw.message("Thread available.");
    } else {
      try {
        threadsSemaphore.acquire();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    Thread thread = new Thread(this);
    thread.start();
    System.out.println("Downloading URL: " + url);
    System.out.println("--------------------------------------");
  }

  // TODO: o downloader ainda nao desliga quando recebe a mensagem de shutdown nao sei porque
  @Override
  public void send(String s) throws RemoteException {
    if (s.equals("Gateway shutting down.")) {
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
      e.printStackTrace();
    }
  }

  private void extract(String url) {
    try {
      Document doc = Jsoup.connect(url).get();
      
      String text = doc.text().toLowerCase();
      
      String title = doc.title();
      
      String citation = doc.select("meta[name=description]").attr("content");
      
      // Extract links
      ulrsList = new ArrayList<>();
      Elements links = doc.select("a[href]");
      for (Element link : links) {
        String linkUrl = link.attr("abs:href");
        ulrsList.add(linkUrl);
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

      // TODO: send extracted data to the barrels via multicast
    } catch (IOException e) {
      e.printStackTrace();
    }
    // TODO: ns se é so isto q é suposto extrair mas acho q sim
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

    try {
      new Downloader(n);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }
}
