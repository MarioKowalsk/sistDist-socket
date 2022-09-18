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
    Coordenator coordenator;
    Timer timeout = new Timer();
    TimerTask electionTimeout;
    TimerTask coordenatorTimeout;

    class ElectionTimeout extends TimerTask {
        public void run() {
            try {
                startElection();
            } catch (IOException e) {e.printStackTrace();
            }
        }
    };

    class CoordenatorTimeout extends TimerTask {
        public void run() {
            System.out.println("Coordenator!");
            coordenator = new Coordenator(ID, group);
            coordenator.start();
        }
    };

    public Collaborator(String groupAddress) throws IOException {
        group = new Peer(6789, groupAddress, 6789);
        self = new Peer();
        ID = self.getPort();
        new Thread(() -> listenUnicast()).start();
        electionTimer();
        this.start();
    }

    public TimerTask rescheduleTask(TimerTask old, TimerTask newTask, long delay) {
        if (old != null) {
            old.cancel();
        }
        timeout.purge();
        timeout.schedule(newTask, delay);
        return newTask;
    }

    public void coordenatorTimer() {
        coordenatorTimeout = rescheduleTask(coordenatorTimeout, new CoordenatorTimeout(), 5000);
    }

    public void electionTimer() {
        electionTimeout = rescheduleTask(electionTimeout, new ElectionTimeout(), 2500);
    }

    public void election() throws IOException {
        String output = "Election: ";
        for (Map.Entry<Integer, InetSocketAddress> entry : pool.entrySet()) {
            if (entry.getKey() > ID) {
                output += String.format("[%05d]", entry.getKey());
                self.send(new Message(ID, true, false, false, ""), entry.getValue());
            }
        };
        System.out.println(output);
        coordenatorTimer();
    }

    public void startElection() throws IOException {
        if (!electionRunning.getAndSet(true)) {
            try {
                election();
            } finally {
                electionRunning.set(false); // move to a timer
            }
        }
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
                if (message.isElection) {
                    if (message.isReply) {
                        if (message.sourceID > ID && coordenatorTimeout != null) {
                            coordenatorTimeout.cancel();
                        }
                    } else {
                        self.send(new Message(ID, true, true, false, ""), message.sourceAddress);
                        startElection();
                    }
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
                    electionTimer();
                }
            }
        } catch (IOException e) { e.printStackTrace();
        }
    }
}

class Coordenator extends Thread {
    Peer group;
    int ID;
    volatile boolean running = true;

    public Coordenator(int id, Peer multicast) {
        group = multicast;
        ID = id;
    }

    public void run() {
        try {
            while (running) {
                group.send(new Message(ID, false, false, true, "Olá"));
                sleep(1000);
            }
        } catch (Exception e) { e.printStackTrace();
        }
    }
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