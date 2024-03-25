import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Client extends UnicastRemoteObject implements Client_I {
  private int id;
  public static final String ANSI_RESET = "\u001B[0m";
  public static final String ANSI_RED = "\u001B[31m";
  public static final String ANSI_GREEN = "\u001B[32m";
  public static final String ANSI_YELLOW = "\u001B[33m";
  public static final String ANSI_BLUE = "\u001B[34m";
  public static final String ANSI_PURPLE = "\u001B[35m";
  public static final String ANSI_CYAN = "\u001B[36m";

  Client(int id) throws RemoteException{
    super();
    this.id = id;
  }

  @Override
  public int func(String s) throws RemoteException {
    System.out.println(s);
    System.out.print("> ");
    return 0;
  }

  public static void main(String[] args) {
    // Validate command line arguments
    try {
      if (args.length > 0) {
        int n = Integer.parseInt(args[0]);
      } else {
        System.out.println("usage: make cli id=n");
        System.exit(1);
      }
    } catch (NumberFormatException e) {
      System.err.println("Invalid integer format: " + args[0]);
      System.exit(1);
    }

    // Menu
    int inp = -1;
    Scanner sc = new Scanner(System.in);
    while (true) {
      printMenu();
      System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
      try {
        inp = sc.nextInt();
        sc.nextLine(); // Consume \n
        switch (inp) {
          case 0: // Exit
            sc.close();
            System.out.println(ANSI_YELLOW + "Exiting program." + ANSI_RESET);
            return;
          case 1: // Index URL
            indexURL(sc);
            break;
          case 2: // Search
            search(sc);
            break;
          case 3: // Consult Admin Pages
            handleAdminPages(sc);
            break;
          default:
            System.out.println(ANSI_RED + "Invalid input. Try again." + ANSI_RESET);
        }
      } catch (InputMismatchException e) {
        System.out.println(ANSI_RED + "Invalid input. Please enter a valid integer." + ANSI_RESET);
        sc.nextLine(); // Consume invalid input
      } catch (NoSuchElementException e) {
        System.out.println(ANSI_RED + "Input not found. Please try again." + ANSI_RESET);
        sc.nextLine(); // Consume invalid input
      } catch (IllegalStateException e) {
        System.out.println(ANSI_RED + "Scanner is closed. Exiting program." + ANSI_RESET);
        return;
      }
    }
  }

  private static void printMenu() {
    System.out.println(ANSI_CYAN + "Googol Search Engine" + ANSI_RESET);
    System.out.println(ANSI_BLUE + "1. Index URL");
    System.out.println("2. Search");
    System.out.println("3. Consult Admin Pages");
    System.out.println("0. Exit\n" + ANSI_RESET);
  }

  private static void indexURL(Scanner sc) {
    System.out.println(ANSI_PURPLE + "Enter URL to index:" + ANSI_RESET);
    System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
    String url = sc.nextLine();
    // TODO: Implement URL indexing
  }

  private static void search(Scanner sc) {
    System.out.println(ANSI_PURPLE + "Enter search query:" + ANSI_RESET);
    System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
    String query = sc.nextLine();
    // TODO: Implement search
  }

  private static void handleAdminPages(Scanner sc) {
    int admInp = -1;
    while (true) {
      try {
        printAdminMenu();
        System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
        admInp = sc.nextInt();
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
            System.out.println("Invalid input. Try again.");
        }
      } catch (InputMismatchException e) {
        System.out.println("Invalid input. Please enter a valid integer.");
        sc.nextLine(); // Consume invalid input
      } catch (NoSuchElementException e) {
        System.out.println("Input not found. Please try again.");
        sc.nextLine(); // Consume invalid input
      }
    }
  }

  private static void printAdminMenu() {
    System.out.println(ANSI_CYAN + "Admin Pages" + ANSI_RESET);
    System.out.println(ANSI_BLUE + "1. Top 10 Searches");
    System.out.println("2. List of Active Barrels");
    System.out.println("0. Back\n" + ANSI_RESET);
  }

  private static void top10Searches() {
    // TODO: Implement top 10 searches
  }

  private static void listActiveBarrels() {
    // TODO: Implement list of active barrels
  }

  
  @Override
  public String toString() {
    return "id=" + id;
  }
  
}

/* menu 
indexar url
pesquisar 
cunsultar paginas admin
sair 
------------------
pagina admin
top10 pesquisas
lista de barrels ativos
-------------------
a interface que o gateway disponibiliza para os barrels é diferente da que disponibiliza para os clientes
com uma funçao subscribe o client envia a sua interface para o gateway e o gateway guarda-a numa lista
para os barrels é parecido
-------------------
downloaders
jsoup processa url
agregar palavras, links, titulo, citaçao
implementar mensagem multicast
multicast 
downloaders -server
barrels -client
-------------------
downloaders manager que criar threads downloaders*/
