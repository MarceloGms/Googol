import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Client extends UnicastRemoteObject implements IClient {
  public static final String ANSI_RESET = "\u001B[0m";
  public static final String ANSI_RED = "\u001B[31m";
  public static final String ANSI_GREEN = "\u001B[32m";
  public static final String ANSI_YELLOW = "\u001B[33m";
  public static final String ANSI_BLUE = "\u001B[34m";
  public static final String ANSI_PURPLE = "\u001B[35m";
  public static final String ANSI_CYAN = "\u001B[36m";

  private IGatewayCli gw;

  Client() throws RemoteException{
    super();

    // Connect to the Gateway
    try {
      gw = (IGatewayCli) Naming.lookup("rmi://localhost:1099/gw");
    } catch (NotBoundException e) {
      System.err.println(ANSI_RED + "Gateway not bound. Exiting program." + ANSI_RESET);
      System.exit(1);
    } catch (MalformedURLException e) {
      System.err.println(ANSI_RED + "Malformed URL. Exiting program." + ANSI_RESET);
      System.exit(1);
    } catch (RemoteException e) {
      System.err.println(ANSI_RED + "Gateway down. Exiting program." + ANSI_RESET);
      System.exit(1);
    }

    try {
      gw.subscribe(this);
    } catch (RemoteException e) {
      System.err.println(ANSI_RED + "Unable to subscribe." + ANSI_RESET);
      System.exit(1);
    }

    run();
  }

  @Override
  public void printOnClient(String s) throws RemoteException {
    if (s.equals("Gateway shutting down.")) {
      System.out.println(ANSI_YELLOW + "Received shutdown signal from server. Exiting program..." + ANSI_RESET);
      try {
          UnicastRemoteObject.unexportObject(this, true);
      } catch (NoSuchObjectException e) {
      }
      System.exit(0);
    } else {
      System.out.println(ANSI_RED + s + ANSI_RESET + "\n");
    }
  }

  public void run() throws RemoteException {
    // Menu
    Scanner sc = new Scanner(System.in);
    while (true) {
      int inp = -1;
      printMenu();
      System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
      try {
        inp = sc.nextInt();
        System.out.println();
        sc.nextLine(); // Consume \n
        switch (inp) {
          case 0: // Exit
            sc.close();
            try {
              gw.unsubscribe(this);
              // Unexport the Client object to disconnect from the RMI Registry
              UnicastRemoteObject.unexportObject(this, true);
            } catch (NoSuchObjectException e) {
            }
            System.out.println(ANSI_YELLOW + "Exiting program..." + ANSI_RESET);
            return;
          case 1: // Index URL
            indexURL(sc, gw);
            break;
          case 2: // Search
            search(sc);
            break;
          case 3: // Consult Admin Pages
            handleAdminPages(sc);
            break;
          default:
            System.out.println(ANSI_RED + "Invalid input. Try again.\n" + ANSI_RESET);
        }
      } catch (InputMismatchException e) {
        System.out.println(ANSI_RED + "Invalid input. Please enter a valid integer.\n" + ANSI_RESET);
        sc.nextLine(); // Consume invalid input
      } catch (NoSuchElementException e) {
        System.out.println(ANSI_RED + "Input not found. Please try again.\n" + ANSI_RESET);
        sc.nextLine(); // Consume invalid input
      } catch (IllegalStateException e) {
        System.out.println(ANSI_RED + "Scanner is closed. Exiting program." + ANSI_RESET);
        return;
      }
    }
  }

  private void printMenu() {
    System.out.println(ANSI_CYAN + "Googol Search Engine" + ANSI_RESET);
    System.out.println(ANSI_BLUE + "1. Index URL");
    System.out.println("2. Search");
    System.out.println("3. Consult Admin Pages");
    System.out.println("0. Exit\n" + ANSI_RESET);
  }

  private void indexURL(Scanner sc, IGatewayCli gw) {
    System.out.println(ANSI_PURPLE + "Enter URL to index:" + ANSI_RESET);
    System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
    String url = sc.nextLine();
    System.out.println();
    try {
      gw.send(url ,this);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }

  private void search(Scanner sc) {
    System.out.println(ANSI_PURPLE + "Enter search query:" + ANSI_RESET);
    System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
    String query = sc.nextLine();
    System.out.println();
    // TODO: Implement search
  }

  private void handleAdminPages(Scanner sc) {
    int admInp = -1;
    while (true) {
      try {
        printAdminMenu();
        System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
        admInp = sc.nextInt();
        System.out.println();
        sc.nextLine(); // Consume \n
        switch (admInp) {
          case 0: // Back
            return; // Return to the main menu
          case 1: // Top 10 Searches
            top10Searches();
            break;
          case 2: // List of Active Barrels
            listActiveBarrels();
            break;
          default:
            System.out.println("Invalid input. Try again.\n");
        }
      } catch (InputMismatchException e) {
        System.out.println("Invalid input. Please enter a valid integer.\n");
        sc.nextLine(); // Consume invalid input
      } catch (NoSuchElementException e) {
        System.out.println("Input not found. Please try again.\n");
        sc.nextLine(); // Consume invalid input
      }
    }
  }

  private void printAdminMenu() {
    System.out.println(ANSI_CYAN + "Admin Pages" + ANSI_RESET);
    System.out.println(ANSI_BLUE + "1. Top 10 Searches");
    System.out.println("2. List of Active Barrels");
    System.out.println("0. Back\n" + ANSI_RESET);
  }

  private void top10Searches() {
    // TODO: Implement top 10 searches
  }

  private void listActiveBarrels() {
    // TODO: Implement list of active barrels
  }

  public static void main(String[] args) {
    try {
      new Client();
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }
}
