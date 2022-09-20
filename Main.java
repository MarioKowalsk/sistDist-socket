import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String args[]) {
        try {
            new Collaborator(args[0]);
        } catch (IOException e) { e.printStackTrace();
        }
    }
}

class Asker extends Thread {
    AtomicBoolean enabled = new AtomicBoolean(true);
    AtomicBoolean asking = new AtomicBoolean(false);
    Semaphore mutex = new Semaphore(0);
    Results result;

    enum Results {
        BUSY, YES, NO, COORDENATOR
    }

    public Asker() {
        this.start();
    }

    public void run() {
        while (true) {
            Scanner sc = new Scanner(System.in);
            String in = sc.next().toLowerCase();
            result = in.matches(".*c.*") ? Results.COORDENATOR :
                     in.matches(".*y.*") ? Results.YES :
                     in.matches(".*n.*") ? Results.NO  :
                     Results.BUSY;
            if (asking.get()) {
                mutex.release();
            }
        }
    }

    public void setEnabled(boolean value) {
        if (!value && asking.get()) {
            mutex.release();
        }
        enabled.set(value);
    }

    public Asker.Results ask() {
        if (!enabled.get()) {
            return Results.BUSY;
        }
        System.out.print("Start election or become coordenator? [Yes/no/coordenator/y/n/c]: ");
        if (!asking.getAndSet(true)) {
            try {
                mutex.acquire();
            } catch (InterruptedException e) { e.printStackTrace();
            }
            mutex.drainPermits();
            asking.set(false);
        }
        return result;
    }
}

class Collaborator extends Thread {
    static final int TIMEOUT_DT1 = 4000;
    static final int TIMEOUT_DT2 = 6000;
    static final int TIMEOUT_DT3 = 8000;
    Peer group;
    Peer self = new Peer();
    int ID = 0;
    int coordenador_eleito = 0;
    ConcurrentHashMap<Integer, InetSocketAddress> pool = new ConcurrentHashMap<>();
    AtomicBoolean electionRunning = new AtomicBoolean();
    AtomicBoolean coordenatorEligible = new AtomicBoolean();
    Coordenator coordenator;
    Timer timeout = new Timer();
    TimerTask electionTimeout = new ElectionTimeout();
    TimerTask electionDoneTimeout = new ElectionDoneTimeout();

    Asker asker = new Asker();

    public Collaborator(String groupAddress) throws IOException {
        group = new Peer(6789, groupAddress, 6789);
        ID = self.getPort();
        new Thread(() -> listenUnicast()).start();
        electionTimer(0);
        this.start();
    }

    class ElectionTimeout extends TimerTask {
        public void run() {
            try {
                startElection();
            } catch (IOException e) {e.printStackTrace();
            }
        }
    };

    class ElectionDoneTimeout extends TimerTask {
        public void run() {
            stopElection();
        }
    };

    public TimerTask rescheduleTask(TimerTask old, TimerTask newTask, long delay) {
        old.cancel();
        timeout.purge();
        timeout.schedule(newTask, delay);
        return newTask;
    }

    public void electionDoneTimer(long delay) {
        electionDoneTimeout = rescheduleTask(electionDoneTimeout, new ElectionDoneTimeout(), delay <= 0 ? TIMEOUT_DT2 : delay);
    }

    public void electionTimer(long delay) {
        electionTimeout = rescheduleTask(electionTimeout, new ElectionTimeout(), delay <= 0 ? TIMEOUT_DT1 : delay);
    }

    public void election() throws IOException {
        coordenatorEligible.set(true);
        String output = "Election: {";
        for (Map.Entry<Integer, InetSocketAddress> entry : pool.entrySet()) {
            if (entry.getKey() > ID) {
                output += String.format("[%05d]", entry.getKey());
                self.send(new Message(ID, true, false, false, ""), entry.getValue());
            }
        };
        System.out.println(output + "}");
        electionDoneTimer(0);
    }

    public void startElection() throws IOException {
        Asker.Results result = asker.ask();
        if (result == Asker.Results.NO) {
            return;
        } else if (result == Asker.Results.COORDENATOR) {
            startCoordenator();
            return;
        }
        if (!electionRunning.getAndSet(true)) {
            election();
        }
    }

    public void stopElection() {
        electionDoneTimeout.cancel();
        electionRunning.set(false);
        if (coordenatorEligible.getAndSet(false)) {
            startCoordenator();
        }
    }

    public void startCoordenator() {
        System.out.println("Coordenator!");
        if (coordenator != null) {
            coordenator.stopCoordenator();
        }
        coordenator = new Coordenator(ID, group);
        coordenator.start();
    }

    public void addPeer(Message message) throws IOException {
        InetSocketAddress destination = new InetSocketAddress(message.sourceAddress.getAddress(), message.sourceID);
        boolean newpeer = pool.putIfAbsent(message.sourceID, destination) == null;
        if (newpeer && message.sourceID != ID && !message.isCoordenator && !message.isElection && !message.isReply) {
            self.send(new Message(ID, false, true, false, ""), destination);
        }
    }

    public void listenUnicast() {
        try {
            while (true) {
                Message message = self.receive();
                addPeer(message);
                if (message.isElection) {
                    asker.setEnabled(false);
                    if (message.isReply) {
                        if (message.sourceID > ID) {
                            coordenatorEligible.set(false);
                            electionTimer(TIMEOUT_DT3);
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
                if (message.sourceID != coordenador_eleito && message.isCoordenator) {
                    coordenatorEligible.set(false);
                    asker.setEnabled(false);
                    stopElection();
                    coordenador_eleito = message.sourceID;
                    if (coordenator != null && coordenador_eleito != ID) {
                        coordenator.stopCoordenator();
                    }
                }
                if (message.sourceID == coordenador_eleito) {
                    asker.setEnabled(true);
                    electionTimer(0);
                }
            }
        } catch (IOException e) { e.printStackTrace();
        }
    }
}

class Coordenator extends Thread {
    static final int TIMEOUT_DT1 = 2000;
    Peer group;
    int ID;
    volatile boolean running = true;

    public Coordenator(int id, Peer multicast) {
        group = multicast;
        ID = id;
    }

    public void run() {
        try {
            group.send(new Message(ID, false, false, true, ""));
            while (running) {
                group.send(new Message(ID, false, false, true, "Olá"));
                sleep(TIMEOUT_DT1);
            }
        } catch (Exception e) { e.printStackTrace();
        }
    }

    public void stopCoordenator() {
        running = false;
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