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
import java.net.SocketException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class Barrel extends UnicastRemoteObject implements IBarrel, Runnable {
    private int id;
    private IGatewayBrl gw;
    private final int multicastPort;
    private final String multicastAddress;
    private HashMap<String, HashSet<String>> invertedIndex;
    private boolean running;
    private HashMap<String, HashSet<String>> pageLinks;
    private HashMap<String, HashSet<String>> linkedPage;
    private HashMap<String, LinkedHashSet<String>> title_citation;

    private MulticastSocket multicastSocket;

    public Barrel(String multicastAddress, int multicastPort, int id) throws RemoteException {
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.id = id;
        invertedIndex = new HashMap<>();
        running = true;
        pageLinks = new HashMap<>();
        linkedPage = new HashMap<>();
        title_citation = new HashMap<>();
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

    @Override
    public String search(String s) throws RemoteException {
        String sep_words[] = s.split(" ");
        List<String> links_search = new ArrayList<>();
        for (String sep_word : sep_words) {
            sep_word = sep_word.replaceAll("\\p{Punct}", "");  
            sep_word = normalizeWord(sep_word);
            for(String key : invertedIndex.keySet()){
                if(key.equals(sep_word)){
                    links_search.add(invertedIndex.get(key).toString());
                }
            }
        }

        List<String> ord_links_search = new ArrayList<>(linkedPage.keySet());
        ord_links_search.sort((link1, link2) -> linkedPage.get(link2).size() - linkedPage.get(link1).size());
        String string_links = "";
        for (String link : ord_links_search) {
            for (Map.Entry<String, LinkedHashSet<String>> entry : title_citation.entrySet()) {
                String key = entry.getKey();
                if (key.equals(link)) {
                    HashSet<String> values = entry.getValue();
                    for (String value : values) {
                        string_links += value + "\n";
                    }
                    break;
                }
            }
            string_links += link + "\n|";
        }
        return string_links;
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

                try {
                    multicastSocket.receive(packet);
                } catch (SocketException e) {
                    return;
                }

                String message = new String(packet.getData(), 0, packet.getLength());
                //System.out.println("Barrel " + id + " received message from " + packet.getAddress().getHostAddress() + ": " + message);
                String[] parts = message.split("\n");

                String url = parts[0].replace("URL: ", "");
                String title = parts[1].replace("Title: ", "");
                String citation = parts[2].replace("Citation: ", "");
                String keywordsString = parts[3].replace("Keywords: ", "").replace("[", "").replace("]", "");
                String linksString = parts[4].replace("Links: ", "").replace("[", "").replace("]", "");

                LinkedHashSet<String> info = title_citation.get(url);
                if (info == null) {
                    info = new LinkedHashSet<String>();
                    title_citation.put(url, info);
                }
                info.add(title);
                info.add(citation);

                String[] keywords = keywordsString.split(", ");
                for (String keyword : keywords) {
                    addToIndex(keyword, url);
                }

                String[] links = linksString.split(", ");
                
                for (String link : links) {
                    addToUrls(url, link);
                }

                addToLinkedPage(url);

                saveHashMapToFile(invertedIndex, "Barrel" + id + "index.dat");
                saveHashMapToFile(linkedPage, "Barrel" + id + "linkedPage.dat");
                

            }
        } catch (Exception e) {
            try {
                System.out.println("Barrel " + id + " crashed.");
                gw.rmvBrl(this, id);
                gw.BrlMessage("Barrel " + id + " crashed.");
            } catch (RemoteException e1) {
                System.out.println("Error removing barrel from Gateway.");
            }
        }
    }

    // Save hashmap to file
    private static void saveHashMapToFile(HashMap<String, HashSet<String>> hashMap, String filename) {
        try {
            FileOutputStream fileOut = new FileOutputStream("assets/" + filename);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(hashMap);
            objectOut.close();
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Read hashmap from file
    private static HashMap<String, HashSet<String>> readHashMapFromFile(String filename) {
        try {
            FileInputStream fileIn = new FileInputStream("assets/" + filename);
            ObjectInputStream objectIn = new ObjectInputStream(fileIn);
            @SuppressWarnings("unchecked")
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
            invertedIndex.put(term, urls);
        }
        urls.add(url);
    }

    public void addToUrls(String url, String url_new) {
        HashSet<String> links = pageLinks.get(url);
        if (links == null) {
            links = new HashSet<String>();
            pageLinks.put(url, links);
        }
        links.add(url_new);
    }

    public void addToLinkedPage(String url) {
        for (String key : pageLinks.keySet()) {
            HashSet<String> links = pageLinks.get(key);
            if (links.contains(url)) {
                HashSet<String> linked = linkedPage.get(url);
                if (linked == null) {
                    linked = new HashSet<String>();
                    linkedPage.put(url, linked);
                }
                linked.add(key);
            }
        }
    }

    // normalize the word by removing accents
    private String normalizeWord(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }
    
    //FUNÇÕES AINDA NÃO USADAS

    private static int loadConfig() {
        Properties prop = new Properties();
        try (FileInputStream input = new FileInputStream("assets/config.properties")) {
            prop.load(input);
            return Integer.parseInt(prop.getProperty("barrels"));
        } catch (IOException ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    // TODO: se um barrel novo for adicionado, ele sincroniza-se com os outros barrels (EXTRA)

    public static void main(String[] args) {
        String multicastAddress = "230.0.0.0";
        int multicastPort = 12345;

        int nBarrels = loadConfig();

        for (int i = 1; i <= nBarrels; i++) {
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