import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class Barrel extends UnicastRemoteObject implements IBarrel, Runnable {
    private int id;
    private IGatewayBrl gw;
    private Set<String> stopWords;
    private final int multicastPort;
    private final String multicastAddress;
    private HashMap<String, HashSet<String>> invertedIndex;
    private boolean running;
    private HashMap<String, HashSet<String>> pageLinks;
    private HashMap<String, HashSet<String>> linkedPage;
    private HashMap<String, LinkedHashSet<String>> title_citation;
    private MulticastSocket multicastSocket;
    private static String SERVER_IP_ADDRESS;

    public Barrel(String multicastAddress, int multicastPort) throws RemoteException {
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        invertedIndex = new HashMap<>();
        running = true;
        pageLinks = new HashMap<>();
        linkedPage = new HashMap<>();
        title_citation = new HashMap<>();
        stopWords = new HashSet<>();
        loadStopWords("assets/stop_words.txt");
        try {
            multicastSocket = new MulticastSocket(multicastPort);
        } catch (IOException e) {
            System.err.println("Error creating multicast socket: " + e.getMessage());
            System.exit(1);
        }
        // handle SIGINT
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }));
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
        String sep_words_aux[] = s.split(" ");
        ArrayList<String> sep_words = new ArrayList<>();
        for (String sep_word : sep_words_aux) {
            sep_word = sep_word.replaceAll("\\p{Punct}", "");
            if (!isStopWord(sep_word)) {
                normalizeWord(sep_word);
                sep_words.add(sep_word);
                HashMap<String, Integer> searchCount = readHashMapFromFileTop10("top10.dat");
                if(searchCount == null){
                    searchCount = new HashMap<>();
                }
                if(!searchCount.containsKey(sep_word)){
                    searchCount.put(sep_word, 1);
                }else{
                    searchCount.put(sep_word, searchCount.get(sep_word) + 1);
                }
                saveHashMapToFileTop10(searchCount, "top10.dat");
                System.out.println(searchCount);
            }
        }
        
        List<String> links_search = new ArrayList<>(), links_search_aux = new ArrayList<>(), linksToRemove = new ArrayList<>();;
        int words_count = 0;
        for (String sep_word : sep_words) {
            for(String key : invertedIndex.keySet()){
                if(key.equals(sep_word)){
                    words_count++;
                    for (String link : invertedIndex.get(sep_word)) {
                        links_search_aux.add(link);
                    }
                    if(!links_search.isEmpty()){
                        for (String link : links_search) {
                            if(!links_search_aux.contains(link)){
                                linksToRemove.add(link);
                            }
                        }
                        links_search.removeAll(linksToRemove);
                    }else{
                        links_search.addAll(links_search_aux);
                    }
                    links_search_aux.clear();
                    break;
                }
            }
        }

        if (words_count != sep_words.size()) {
            return "";
        }

        // Ordena links_search em ordem decrescente com base no número de valores associados a cada URL
        links_search.sort(Comparator.comparingInt(url -> {
            HashSet<String> values = linkedPage.get(url);
            return values != null ? values.size() : 0;
        }).reversed());
        
        String string_links = "";
        for (String link : links_search) {
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
            string_links += link + "\n*";
        }
        return string_links;
    }

    @Override
    public String findSubLinks(String s) throws RemoteException {
        HashSet<String> links = linkedPage.get(s);
        String string_links = "";
        if (links != null) {
            for (String link : links) {
                string_links += link + "\n";
            }
        }
        return string_links;
    }

    @Override
    public String getTop10Searches() throws RemoteException {
        String string_links = "";
        HashMap<String, Integer> searchCount = readHashMapFromFileTop10("top10.dat");
        List<Map.Entry<String, Integer>> entryList = new ArrayList<>(searchCount.entrySet());
        Comparator<Map.Entry<String, Integer>> comparator = Comparator.comparingInt(Map.Entry::getValue);
        entryList.sort(comparator.reversed());
        for (int i = 0; i < entryList.size(); i++) {
            string_links += entryList.get(i).getKey() + " - " + entryList.get(i).getValue() + "\n";
        }
        return string_links;
    }

    @Override
    public int getId() throws RemoteException {
        return id;
    }

    private boolean isStopWord(String word) {
        return stopWords.contains(normalizeWord(word));
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

    public void run() {
        // Connect to the Gateway
        try {
            gw = (IGatewayBrl) Naming.lookup("rmi://" + SERVER_IP_ADDRESS + ":1099/gw");
            System.out.println("Barrel connected to Gateway.");
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
            synchronized (gw) {
                id = gw.AddBrl(this);
            }
            System.out.println("Barrel " + id + " bound to Gateway.");
        } catch (RemoteException e) {
            System.out.println("Error adding barrel to Gateway: " + e.getMessage());
            return;
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
        synchronized (getLockObject(filename)) {
            try {
                FileOutputStream fileOut = new FileOutputStream("assets/" + filename);
                ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
                objectOut.writeObject(hashMap);
                objectOut.close();
                
            } catch (Exception ex) {
                System.out.println("Error writing file: " + ex.getMessage());
            }
        }
    }

    // Read hashmap from file
    private static HashMap<String, HashSet<String>> readHashMapFromFile(String filename) {
        synchronized (getLockObject(filename)) {
            try {
                FileInputStream fileIn = new FileInputStream("assets/" + filename);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);
                @SuppressWarnings("unchecked")
                HashMap<String, HashSet<String>> hashMap = (HashMap<String, HashSet<String>>) objectIn.readObject();
                objectIn.close();
                return hashMap;
            } catch (Exception ex) {
                System.out.println("Error reading file: " + ex.getMessage());
                return null;
            }
        }
    }

    private static void saveHashMapToFileTop10(HashMap<String, Integer> hashMap, String filename) {
        synchronized (getLockObject(filename)) {
            try {
                FileOutputStream fileOut = new FileOutputStream("assets/" + filename);
                ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
                objectOut.writeObject(hashMap);
                objectOut.close();
            } catch (IOException ex) {
                System.out.println("Error writing file: " + ex.getMessage());
            }
        }
    }

    private static HashMap<String, Integer> readHashMapFromFileTop10(String filename) {
        synchronized (getLockObject(filename)) {
            try {
                FileInputStream fileIn = new FileInputStream("assets/" + filename);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);
                @SuppressWarnings("unchecked")
                HashMap<String, Integer> hashMap = (HashMap<String, Integer>) objectIn.readObject();
                objectIn.close();
                return hashMap;
            } catch (Exception ex) {
                System.out.println("Error reading file: " + ex.getMessage());
                return null;
            }
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
            SERVER_IP_ADDRESS = prop.getProperty("server_ip");
            return Integer.parseInt(prop.getProperty("barrels"));
        } catch (IOException ex) {
            System.out.println("Failed to load config file: " + ex.getMessage());
            System.exit(1);
            return 0;
        }
    }

    private static Object getLockObject(String filename) {
        return filename.hashCode();
    }

    private void shutdown() {
        try {
            if (multicastSocket != null) {
                multicastSocket.close();
            }
            System.out.println("Barrel " + id + " shutting down...\n");
            // Notify the Gateway about the shutdown
            if (gw != null) {
                gw.rmvBrl(this, id);
                gw.BrlMessage("Barrel " + id + " shutting down.");
            }
        } catch (RemoteException e) {
            System.out.println("Error occurred during shutdown: " + e.getMessage());
        }
    }

    // TODO: se um barrel novo for adicionado, ele sincroniza-se com os outros barrels (EXTRA)

    public static void main(String[] args) {
        String multicastAddress = "230.0.0.0";
        int multicastPort = 12345;

        int nBarrels = loadConfig();

        for (int i = 1; i <= nBarrels; i++) {
            try {
                Barrel barrel = new Barrel(multicastAddress, multicastPort);
                Thread thread = new Thread(barrel);
                thread.start();
            } catch (RemoteException e) {
                System.out.println("Error creating Barrel: " + e.getMessage());
            }
        }
    }
}