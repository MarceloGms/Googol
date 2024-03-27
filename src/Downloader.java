import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.text.Normalizer;

public class Downloader extends UnicastRemoteObject implements IDownloader {
  private int id;
  private int nThreads;
  private IGatewayDl gw;
  private Set<String> stopWords;
  private ArrayList<String> ulrsList;
  private ArrayList<String> keywords;

  Downloader(int nThreads) throws RemoteException {
    super();
    this.nThreads = nThreads;
    stopWords = new HashSet<>();
    loadStopWords("assets/stop_words.txt");

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
    run();
  }

  public void run() {
    
  }

  @Override
  public void download(String url) throws RemoteException {
    extract(url);
  }

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
      
      System.out.println("URL: " + url);
      System.out.println("Title: " + title);
      System.out.println("Citation: " + citation);
      System.out.println("Keywords: " + keywords);
      System.out.println("Links: " + ulrsList);
      System.out.println("--------------------------------------");
      // TODO: sera q é suposto remover a pontuaçao das palavras?

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
