import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String args[]) {
        System.out.println("Main");
        // Peer p = new Peer(args[0]);
        // p.multiCastSend(args[1]);
        try {
            new Collaborator(args[0]);
        } catch (IOException e) { e.printStackTrace();
        }
        /*Message message = new Message(12345, false, true, false, "Olá");
        message.print();
        message.encode();
        message.decode(message.bytes);
        message.print();*/
        /*try {
            byte[] buffer = new byte[1000];
            DatagramPacket p = new DatagramPacket(buffer, buffer.length);
            DatagramSocket receiveSocket = new DatagramSocket(5505, InetAddress.getByName("127.0.0.1"));
            receiveSocket.setSoTimeout(5000);
            while (true) {
            try {
                receiveSocket.receive(p);
            } catch (SocketTimeoutException ste) {
                System.out.println("### Timed out after 5 seconds");
            }
            }
        } catch (IOException e) { e.printStackTrace();
        }*/
    }
}

class Collaborator extends Thread {
    Peer group;
    Peer self;
    int ID;
    ConcurrentHashMap<Integer, InetSocketAddress> pool = new ConcurrentHashMap<>();
    AtomicBoolean electionRunning = new AtomicBoolean();

    public Collaborator(String groupAddress) throws IOException {
        group = new Peer(6789, groupAddress, 6789);
        self = new Peer();
        ID = self.getPort();
        new Thread(() -> listenUnicast()); //fix
        this.start();
    }

    public void election() {
        System.out.println("Election!");
    }

    public void startElection() {
        if (!electionRunning.getAndSet(true)) {
            try {
                election();
            } finally {
                electionRunning.set(false);
            }
        }
    }

    public void addPeer(Message message) throws IOException {
        InetSocketAddress destination = new InetSocketAddress(message.sourceAddress.getAddress(), message.sourceID);
        boolean newpeer = pool.putIfAbsent(message.sourceID, destination) == null;
        System.out.println("newpeer: " + newpeer);
        if (newpeer && !message.isCoordenator && !message.isElection && !message.isReply) { // todo: check if message.announcement
            System.out.println("Greet!");
            self.send(new Message(ID, false, true, false, ""), destination);
        }
    }

    public void listenUnicast() {
        System.out.println("Unicast listening!");
        try {
            while (true) {
                Message message = self.receive();
                addPeer(message);
                message.print();
                if (message.isElection && !message.isReply) {
                    startElection();
                }
            }
        } catch (IOException e) { e.printStackTrace();
        }
    }

    public void run() {
        try {
            group.send(new Message(ID));
            while (true) {
                Message message = group.receive();
                addPeer(message);
                //timer
                message.print();
            }
        } catch (IOException e) { e.printStackTrace();
        }
    }
}

class Coordenator {

}

class Message {
    InetSocketAddress sourceAddress;
    int sourceID = 0;
    boolean isCoordenator = false;
    boolean isElection = false;
    boolean isReply = false;
    String body = "";
    byte[] bytes;

    public Message() {
        bytes = new byte[1000];
    }
    
    public Message(int id) {
        this();
        sourceID = id;
    }
    
    public Message(int id, boolean election, boolean reply, boolean coordenator, String message) {
        this(id);
        isElection = election;
        isReply = reply;
        isCoordenator = coordenator;
        body = message;
    }

    public byte[] encode() {
        byte flags = (byte)((isCoordenator  ? 1<<0 : 0) +
                            (isElection     ? 1<<1 : 0) +
                            (isReply        ? 1<<2 : 0));
                    
        System.out.println("Flags: " + flags);
        
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.put(flags)
              .putInt(sourceID)
              .put(body.getBytes(Charset.forName("UTF-8")));
        
        return bytes;
    }

    public void decode(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        byte flags = buffer.get();
        isCoordenator = (flags & 1<<0) > 0;
        isElection =    (flags & 1<<1) > 0;
        isReply =       (flags & 1<<2) > 0;
        sourceID = buffer.getInt();
        body = new String(
            Arrays.copyOfRange(payload, buffer.position(), payload.length),
            Charset.forName("UTF-8")
        );

        System.out.println("Buffer pos: " + buffer.position() + " Length: " + payload.length);
    }

    public void print() {
        System.out.println("sourceAddress: " + sourceAddress);
        System.out.println("isCoordenator: " + isCoordenator);
        System.out.println("isElection: " + isElection);
        System.out.println("isReply: " + isReply);
        System.out.println("sourceID: " + sourceID);
        System.out.println("body: " + body);
    }
}

class Peer {
    DatagramSocket socket = null;
    InetSocketAddress address;

    public Peer(int listenPort, String multicastAddr, int destPort) throws IOException {
        address = new InetSocketAddress(multicastAddr, destPort);
        socket = new DatagramSocket(null); //MulticastSocket(6789);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(listenPort));
        socket.joinGroup(address, NetworkInterface.getByName("le0"));
    }

    public Peer(int listenPort) throws IOException {
        socket = new DatagramSocket(listenPort);
    }

    public Peer() throws IOException {
        this(0);
    }

    public void send(Message message, InetSocketAddress destination) throws IOException {
        message.encode();
        socket.send(new DatagramPacket(message.bytes, message.bytes.length, destination));
    }

    public void send(Message message) throws IOException {
        send(message, address);
    }

    public Message receive() throws IOException {
        Message message = new Message();
        DatagramPacket messageIn = new DatagramPacket(message.bytes, message.bytes.length);
        socket.receive(messageIn);
        message.decode(messageIn.getData());
        message.sourceAddress = (InetSocketAddress)messageIn.getSocketAddress();
        return message;
    }

    public int getPort() {
        return socket.getLocalPort();
    }
}