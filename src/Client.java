import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;

/**
 * The Client class represents a client application for interacting with the Googol Search Engine Gateway.
 */
public class Client extends UnicastRemoteObject implements IClient {

  /**
   * ANSI color code for resetting console output color.
   */
  public static final String ANSI_RESET = "\u001B[0m";

  /**
   * ANSI color code for red console output.
   */
  public static final String ANSI_RED = "\u001B[31m";

  /**
   * ANSI color code for green console output.
   */
  public static final String ANSI_GREEN = "\u001B[32m";

  /**
   * ANSI color code for yellow console output.
   */
  public static final String ANSI_YELLOW = "\u001B[33m";

  /**
   * ANSI color code for blue console output.
   */
  public static final String ANSI_BLUE = "\u001B[34m";

  /**
   * ANSI color code for purple console output.
   */
  public static final String ANSI_PURPLE = "\u001B[35m";

  /**
   * ANSI color code for cyan console output.
   */
  public static final String ANSI_CYAN = "\u001B[36m";

  /**
   * The remote gateway interface used for communication with the gateway.
   */
  private IGatewayCli gw;

  /**
   * The IP address of the gateway RMI server.
   */
  private String SERVER_IP_ADDRESS;

  /**
   * Constructs a new Client object and connects it to the Gateway via rmi.
   * @throws RemoteException if there is an RMI-related error.
   */
  Client() throws RemoteException{
    super();
    loadConfig();
    connectToGateway();

    try {
      gw.subscribe(this);
    } catch (RemoteException e) {
      System.err.println(ANSI_RED + "Unable to subscribe." + ANSI_RESET);
      System.exit(1);
    }

    run();
  }

  /**
   * Connects to the RMI gateway by looking up the server's remote object.
   * If connection fails, it handles the error and exits the program.
   */
  private void connectToGateway() {
    try {
        gw = (IGatewayCli) Naming.lookup("rmi://" + SERVER_IP_ADDRESS + ":1099/gw");
        gw.subscribe(this);
    } catch (RemoteException | NotBoundException | MalformedURLException e) {
        handleErrorAndExit("Error connecting to the Gateway.");
    }
  }

  /**
   * Handles errors and exits the program.
   * @param message the error message to display.
   */
  private void handleErrorAndExit(String message) {
      System.err.println(ANSI_RED + message + " Exiting program." + ANSI_RESET);
      System.exit(1);
  }

  /**
   * Prints messages received from the server on the client console.
   * If the message is a shutdown signal, it exits the program.
   * @param s the message received from the server.
   * @throws RemoteException if there is an RMI-related error.
   */
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

  /**
   * Runs the client application by displaying the menu and handling user input.
   * If the user chooses to exit, it unsubscribes from the server and disconnects from the RMI Registry.
   */
  public void run() {
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
            } catch (RemoteException e) {
            }
            System.out.println(ANSI_YELLOW + "Exiting program..." + ANSI_RESET);
            return;
          case 1: // Index URL
            indexURL(sc, gw);
            break;
          case 2: // Search
            search(sc);
            break;
          case 3: // Find sub-links
            findSubLinks(sc);
            break;
          case 4: // Consult Admin Pages
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

  /**
   * Prints the main menu options.
   */
  private void printMenu() {
    System.out.println(ANSI_CYAN + "Googol Search Engine" + ANSI_RESET);
    System.out.println(ANSI_BLUE + "1. Index URL");
    System.out.println("2. Search");
    System.out.println("3. Find sub-links");
    System.out.println("4. Consult Admin Pages");
    System.out.println("0. Exit\n" + ANSI_RESET);
  }

  /**
   * Indexes a URL by sending it to the gateway for processing.
   * @param sc the Scanner object for user input.
   * @param gw the remote gateway object.
   */
  private void indexURL(Scanner sc, IGatewayCli gw) {
    System.out.println(ANSI_PURPLE + "Enter URL to index:" + ANSI_RESET);
    System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
    String url = sc.nextLine();
    System.out.println();
    try {
      gw.send(url ,this);
    } catch (RemoteException e) {
      System.out.println(ANSI_RED + "Error occurred sending url. Try again.\n" + ANSI_RESET);
    }
  }

  /**
   * Searches for a query by calling the remote gateway method and displays the results.
   * @param sc the Scanner object for user input.
   */
  private void search(Scanner sc) {
    System.out.println(ANSI_PURPLE + "Enter search query:" + ANSI_RESET);
    System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
    String query = sc.nextLine();
    System.out.println();
    String result = null;
    try {
      result = gw.search(query);
      if (result != null) {
        if (result.equals("No barrels available")) {
          System.out.println(ANSI_RED + "No barrels available.\n" + ANSI_RESET);
          return;
        } else {
          displayResults(result, sc);
        }
      } else {
        System.out.println(ANSI_RED + "No results found.\n" + ANSI_RESET);
      }
    } catch (RemoteException e) {
      System.out.println(ANSI_RED + "Error occurred during search.\n" + ANSI_RESET);
    }
  }

  /**
   * Displays the search results in pages of 10 results each.
   * @param result the search results to display.
   * @param sc the Scanner object for user input.
   */
  private void displayResults(String result, Scanner sc) {
    String[] resultsArray = result.split("\\*");
    // Calculate the total number of pages based on the number of search results
    int totalPages = (resultsArray.length + 10 - 1) / 10;
    int currentPage = 0;

    while (true) {
      // Display the current page of search results
      System.out.println(ANSI_YELLOW + "\nSearch results:" + ANSI_RESET);
      for (int i = currentPage * 10; i < Math.min((currentPage + 1) * 10, resultsArray.length); i++) {
        System.out.println(resultsArray[i]);
      }

      // Display the page number and total pages, and prompt for user input
      System.out.println(ANSI_BLUE + "\nPage " + (currentPage + 1) + " of " + totalPages + ANSI_RESET);
      System.out.println(ANSI_CYAN + "Press 'n' for next page, 'p' for previous page, or 'q' to quit:" + ANSI_RESET);
      System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
      String input = sc.nextLine().toLowerCase();

      if (input.equals("q")) {
        break;
      } else if (input.equals("n")) {
        currentPage = (currentPage + 1) % totalPages;
      } else if (input.equals("p")) {
        currentPage = (currentPage - 1 + totalPages) % totalPages;
      } else {
        System.out.println(ANSI_RED + "Invalid command. Please try again." + ANSI_RESET);
      }
    }
  }

  /**
   * Finds sub-links for a given URL and displays the results.
   * @param sc the Scanner object for user input.
   */
  private void findSubLinks(Scanner sc) {
    System.out.println(ANSI_PURPLE + "Enter URL to find sub-links:" + ANSI_RESET);
    System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
    String url = sc.nextLine();
    System.out.println();
    String result = null;
    try {
      result = gw.findSubLinks(url);
      if (result != null) {
        if (result.equals("No barrels available")) {
          System.out.println(ANSI_RED + "No barrels available.\n" + ANSI_RESET);
          return;
        } else if (result.equals("Invalid URL.")) {
          System.out.println(ANSI_RED + "Invalid URL.\n" + ANSI_RESET);
          return;
        } else {
          displaySubLinks(result, sc);
        }
      } else {
        System.out.println(ANSI_RED + "No results found.\n" + ANSI_RESET);
      }
    } catch (RemoteException e) {
      System.out.println(ANSI_RED + "Error occurred getting sub links.\n" + ANSI_RESET);
    }
  }

  /**
   * Displays the sub-links in pages of 10 results each.
   * @param result the sub-links to display.
   * @param sc the Scanner object for user input.
   */
  private void displaySubLinks(String result, Scanner sc) {
    String[] resultsArray = result.split("\\n");
    // Calculate the total number of pages based on the number of sub-links
    int totalPages = (resultsArray.length + 10 - 1) / 10;
    int currentPage = 0;

    while (true) {
      // Display the current page of sub-links
      System.out.println(ANSI_YELLOW + "\nSub-links:" + ANSI_RESET);
      for (int i = currentPage * 10; i < Math.min((currentPage + 1) * 10, resultsArray.length); i++) {
        System.out.println(resultsArray[i]);
      }

      // Display the page number and total pages, and prompt for user input
      System.out.println(ANSI_BLUE + "\nPage " + (currentPage + 1) + " of " + totalPages + ANSI_RESET);
      System.out.println(ANSI_CYAN + "Press 'n' for next page, 'p' for previous page, or 'q' to quit:" + ANSI_RESET);
      System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
      String input = sc.nextLine().toLowerCase();

      if (input.equals("q")) {
        break;
      } else if (input.equals("n")) {
        currentPage = (currentPage + 1) % totalPages;
      } else if (input.equals("p")) {
        currentPage = (currentPage - 1 + totalPages) % totalPages;
      } else {
        System.out.println(ANSI_RED + "Invalid command. Please try again." + ANSI_RESET);
      }
    }
  }

  /**
   * Handles the admin pages by displaying the admin menu and handling user input.
   * @param sc the Scanner object for user input.
   */
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
            top10Searches(sc);
            break;
          case 2: // List of Active Barrels
            listActiveBarrels(sc);
            break;
          default:
            System.out.println(ANSI_RED + "Invalid input. Try again.\n"+ ANSI_RESET);
        }
      } catch (InputMismatchException e) {
        System.out.println(ANSI_RED + "Invalid input. Please enter a valid integer.\n" + ANSI_RESET);
        sc.nextLine(); // Consume invalid input
      } catch (NoSuchElementException e) {
        System.out.println(ANSI_RED + "Input not found. Please try again.\n" + ANSI_RESET);
        sc.nextLine(); // Consume invalid input
      }
    }
  }

  /**
   * Prints the admin menu options.
   */
  private void printAdminMenu() {
    System.out.println(ANSI_CYAN + "Admin Pages" + ANSI_RESET);
    System.out.println(ANSI_BLUE + "1. Top 10 Searches");
    System.out.println("2. List of Active Barrels");
    System.out.println("0. Back\n" + ANSI_RESET);
  }

  /**
   * Gets the top 10 searches by calling the remote gateway method and displays the results.
   * @param sc the Scanner object for user input.
   */
  private void top10Searches(Scanner sc) {
    while (true) {
      String result = null;
      try {
        result = gw.getTop10Searches();
        if (result != null) {
          if (result.equals("")) {
            System.out.println(ANSI_RED + "No results found.\n" + ANSI_RESET);
            return;
          }
          else if (result.equals("No barrels available")) {
            System.out.println(ANSI_RED + "No barrels available.\n" + ANSI_RESET);
            return;
          } else {
            String[] resultsArray = result.split("\\n");

            // Display the top 10 searches
            System.out.println(ANSI_YELLOW + "\nTop 10 Searches:" + ANSI_RESET);
            for (int i = 0; i < resultsArray.length; i++) {
              System.out.println(resultsArray[i]);
            }
            System.out.println();

            // Prompt the user to refresh the list or quit
            System.out.println(ANSI_CYAN + "Press 'r' to refresh or 'q' to quit:" + ANSI_RESET);
            System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
            String input = sc.nextLine().toLowerCase();

            if (input.equals("q")) {
              break;
            } else if (input.equals("r")) {
              continue;
            }
            else {
              System.out.println(ANSI_RED + "Invalid command. Please try again." + ANSI_RESET);
            }
          }
        } else {
          System.out.println(ANSI_RED + "No results found.\n" + ANSI_RESET);
          return;
        }
      } catch (RemoteException e) {
        System.out.println(ANSI_RED + "Error occurred getting top10.\n" + ANSI_RESET);
        return;
      }
    }
  }

  /**
   * Gets the active barrels by calling the remote gateway method and displays the results.
   * @param sc the Scanner object for user input.
   */
  private void listActiveBarrels(Scanner sc) {
    while (true) {
      String result = null;
      try {
        result = gw.getActiveBarrels();
        if (result.equals("No barrels available")) {
          System.out.println(ANSI_RED + "No barrels available.\n" + ANSI_RESET);
          return;
        } else {
          String[] resultsArray = result.split("\\n");

          // Display the active barrels
          System.out.println(ANSI_YELLOW + "\nActive Barrels:" + ANSI_RESET);
          for (int i = 0; i < resultsArray.length; i++) {
            System.out.println("Barrel ID: " + resultsArray[i]);
          }
          System.out.println();

          // Prompt the user to refresh the list or quit
          System.out.println(ANSI_CYAN + "Press 'r' to refresh or 'q' to quit:" + ANSI_RESET);
          System.out.print(ANSI_GREEN + "> " + ANSI_RESET);
          String input = sc.nextLine().toLowerCase();

          if (input.equals("q")) {
            break;
          } else if (input.equals("r")) {
            continue;
          }
          else {
            System.out.println(ANSI_RED + "Invalid command. Please try again." + ANSI_RESET);
          }
        }
      } catch (RemoteException e) {
        System.out.println(ANSI_RED + "Error occurred while trying to get active barrels.\n" + ANSI_RESET);
        return;
      }
    }
  }

  /**
   * Loads the server IP address from the config file.
   */
  private void loadConfig() {
    Properties prop = new Properties();
    try (FileInputStream input = new FileInputStream("assets/config.properties")) {
      prop.load(input);
      SERVER_IP_ADDRESS = prop.getProperty("server_ip");
    } catch (IOException ex) {
      System.out.println(ANSI_RED + "Error occurred while trying to load config file." + ANSI_RESET);
      System.exit(1);
    }
  }

  /**
   * Main method to start the client application.
   * @param args the command line arguments.
   */
  public static void main(String[] args) {
    try {
      new Client();
    } catch (RemoteException e) {
      System.err.println(ANSI_RED + "Error occurred during client initialization." + ANSI_RESET);
      System.exit(1);
    }
  }
}
