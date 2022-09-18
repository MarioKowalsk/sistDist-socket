import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String args[]) {
        try {
            new Collaborator(args[0]);
        } catch (IOException e) { e.printStackTrace();
        }
    }
}

class Collaborator extends Thread {
    Peer group;
    Peer self;
    int ID;
    ConcurrentHashMap<Integer, InetSocketAddress> pool = new ConcurrentHashMap<>();
    AtomicBoolean electionRunning = new AtomicBoolean();
    Timer timeout = new Timer();
    ElectionTimeout electionTimeout;

    class ElectionTimeout extends TimerTask {
        public void run() {
            startElection();
        }
    };

    public Collaborator(String groupAddress) throws IOException {
        group = new Peer(6789, groupAddress, 6789);
        self = new Peer();
        ID = self.getPort();
        new Thread(() -> listenUnicast()).start();
        scheduleElection();
        this.start();
    }

    public void election() {
        System.out.print("Election: ");
        pool.forEach((k, v) -> {System.out.printf("[%05d]", k);});
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

    public void scheduleElection() {
        if (electionTimeout != null) {
            electionTimeout.cancel();
        }
        electionTimeout = new ElectionTimeout();
        timeout.purge();
        timeout.schedule(electionTimeout, 5000);
    }

    public void addPeer(Message message) throws IOException {
        InetSocketAddress destination = new InetSocketAddress(message.sourceAddress.getAddress(), message.sourceID);
        boolean newpeer = pool.putIfAbsent(message.sourceID, destination) == null;
        if (newpeer && message.sourceID != ID && !message.isCoordenator && !message.isElection && !message.isReply) { // todo: check if message.announcement instead
            self.send(new Message(ID, false, true, false, ""), destination);
        }
    }

    public void listenUnicast() {
        try {
            while (true) {
                Message message = self.receive();
                addPeer(message);
                //message.print("<-");
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
                if (message.isCoordenator) {
                    scheduleElection();
                }
                //timer
                //message.print("<-");
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
    }

    public void print(String direction) {
        System.out.printf("%s [%05d][%s%s%s] %s\n", //%-21s sourceAddress,
        direction,
        sourceID,
        isCoordenator ? "Coordenator" : "           ",
        isElection ? "Election" : "        ",
        isReply ? "Reply" : "     ",
        body);
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
        message.print("->");
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
        message.print("<-");
        return message;
    }

    public int getPort() {
        return socket.getLocalPort();
    }
}