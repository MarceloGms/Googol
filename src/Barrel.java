import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.MulticastSocket;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;

public class Barrel {
    private IGatewayBrl gw;
    private final int multicastPort;
    private final String multicastAddress;
    private final HashMap<String, HashSet<String>> invertedIndex;

    public Barrel(String multicastAddress, int multicastPort) {
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.invertedIndex = new HashMap<>();

        // Connect to the Gateway
        try {
            gw = (IGatewayBrl) Naming.lookup("rmi://localhost:1099/gw");
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
    }

    public void listenForMulticastMessages() {
        try (MulticastSocket multicastSocket = new MulticastSocket(multicastPort)) {
            InetAddress group = InetAddress.getByName(multicastAddress);
            multicastSocket.joinGroup(group);

            System.out.println("Listening for multicast messages...");

            while (true) {
                byte[] buffer = new byte[1000];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received message from " + packet.getAddress().getHostAddress() + ": " + message);
                
                // Process the received message as needed
                processMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
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

    private void processMessage(String message) {
        // Implement your message processing logic here
        // For example, you can parse the message and take appropriate actions
        // Example: System.out.println("Processed message: " + message);
    }

    public static void main(String[] args) {
        // Example usage
        String multicastAddress = "230.0.0.0"; // Example multicast address
        int multicastPort = 12345; // Example multicast port

        Barrel barrel = new Barrel(multicastAddress, multicastPort);
        barrel.listenForMulticastMessages();
    }
}