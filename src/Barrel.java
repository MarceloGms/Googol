import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Barrel extends UnicastRemoteObject implements IBarrel, Runnable {
    private int id;
    private IGatewayBrl gw;
    private static final int N_BARRELS = 3;
    private final int multicastPort;
    private final String multicastAddress;
    private HashMap<String, HashSet<String>> invertedIndex;
    private boolean running;
    private HashMap<String, ArrayList<String>> pageLinks;
    private HashMap<String, Integer> pageLinkCounts;
    MulticastSocket multicastSocket;

    public Barrel(String multicastAddress, int multicastPort, int id) throws RemoteException {
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.id = id;
        invertedIndex = new HashMap<>();
        running = true;
        pageLinks = new HashMap<>();
        pageLinkCounts = new HashMap<>();
        try {
            multicastSocket = new MulticastSocket(multicastPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void send(String s) throws RemoteException {
        if (s.equals("Gateway shutting down.")) {
            running = false;
            multicastSocket.close();
            System.out.println("Received shutdown signal from server. Shutting down...");
            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (NoSuchObjectException e) {
            }
            System.exit(0);
        } else {
            System.out.println(s + "\n");
        }
    }

    public void run() {
        // Connect to the Gateway
        try {
            gw = (IGatewayBrl) Naming.lookup("rmi://localhost:1099/gw");
            System.out.println("Barrel " + id + " connected to Gateway.");
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

        try {
            gw.AddBrl(this, id);
            System.out.println("Barrel " + id + " bound to Gateway.");
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        listenForMulticastMessages();

    }

    private void listenForMulticastMessages() {
        try {
            InetAddress group = InetAddress.getByName(multicastAddress);
            multicastSocket.joinGroup(new InetSocketAddress(group, multicastPort), NetworkInterface.getByIndex(0));

            System.out.println("Barrel " + id + " listening for multicast messages...");

            while (running) {
                byte[] buffer = new byte[65507];  // Maximum size of a UDP packet
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                //System.out.println("Barrel " + id + " received message from " + packet.getAddress().getHostAddress() + ": " + message);
                String[] parts = message.split("\n");

                String url = parts[0].replace("URL: ", "");
                String title = parts[1].replace("Title: ", "");
                String citation = parts[2].replace("Citation: ", "");
                String keywordsString = parts[3].replace("Keywords: ", "").replace("[", "").replace("]", "");
                String linksString = parts[4].replace("Links: ", "").replace("[", "").replace("]", "");
                String[] keywords = keywordsString.split(", ");
                for (String keyword : keywords) {
                    addToIndex(keyword, url);
                }
                String[] links = linksString.split(", ");
                int linksCount = links.length;
                //System.out.println(invertedIndex);
                saveHashMapToFile(invertedIndex, "Barrel" + id + ".dat");

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void searchBarrel() {
        /*
        // Chamar o método para ler o HashMap do arquivo
        HashMap<String, HashSet<String>> hashMap1 = readHashMapFromFile("../barrels/" + filename);

        // Imprimir o conteúdo do HashMap
        if (hashMap1 != null) {
            for (String key : hashMap1.keySet()) {
                System.out.println("Chave: " + key);
                System.out.println("Valores: " + hashMap1.get(key));
            }
        } else {
            System.out.println("O arquivo está vazio ou ocorreu um erro ao ler o arquivo.");
        }*/
    }

    // Save hashmap to file
    private static void saveHashMapToFile(HashMap<String, HashSet<String>> hashMap, String filename) {
        try {
            FileOutputStream fileOut = new FileOutputStream("assets/" + filename);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(hashMap);
            objectOut.close();
            System.out.println("HashMap saved to " + filename);
            /*
            */
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Read hashmap from file
    private static HashMap<String, HashSet<String>> readHashMapFromFile(String filename) {
        try {
            FileInputStream fileIn = new FileInputStream("assets/" + filename);
            ObjectInputStream objectIn = new ObjectInputStream(fileIn);
            HashMap<String, HashSet<String>> hashMap = (HashMap<String, HashSet<String>>) objectIn.readObject();
            objectIn.close();
            return hashMap;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public void addToIndex(String term, String url) {
        HashSet<String> urls = invertedIndex.get(term);
        if (urls == null) {
            urls = new HashSet<String>();
            invertedIndex.put(term,  urls);
        }
        urls.add(url);
    }

    //FUNÇÕES AINDA NÃO USADAS

    public void addPageLinks(String url, ArrayList<String> links) {
    	pageLinks.put(url, links);
    }

    public void urlConnections(String url) {
    	Integer num = pageLinkCounts.getOrDefault(url, 0);
        pageLinkCounts.put(url, num + 1);
    }

    public ArrayList<String> getPagesWithLinkTo(String url){
        ArrayList<String> pagesWithLink = new ArrayList<String>();
        for (String pageUrl : pageLinks.keySet()) {
            ArrayList<String> links = pageLinks.get(pageUrl);
            if (links.contains(url)) {
                pagesWithLink.add(pageUrl);
            }
        }
        return pagesWithLink;
    }

    public static void main(String[] args) {
        String multicastAddress = "230.0.0.0";
        int multicastPort = 12345;

        for (int i = 1; i <= N_BARRELS; i++) {
            try {
                Barrel barrel = new Barrel(multicastAddress, multicastPort, i);
                Thread thread = new Thread(barrel);
                thread.start();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}