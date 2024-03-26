import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.StringTokenizer;
public class Downloader extends UnicastRemoteObject implements IDownloader {
  private int id;
  private int nThreads;
  private IGatewayDl gw;

  Downloader(int nThreads) throws RemoteException {
    super();
    this.nThreads = nThreads;

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
    while (true) {
      
    }
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

  public void extract(String url) {
    try {
      Document doc = Jsoup.connect(url).get();
      StringTokenizer tokens = new StringTokenizer(doc.text());
      int countTokens = 0;
      while (tokens.hasMoreElements() && countTokens++ < 100)
        System.out.println(tokens.nextToken().toLowerCase());
      Elements links = doc.select("a[href]");
      for (Element link : links)
        System.out.println(link.text() + "\n" + link.attr("abs:href") + "\n");
    } catch (IOException e) {
      e.printStackTrace();
    }
    // TODO: not finished (wrong output i think)
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
