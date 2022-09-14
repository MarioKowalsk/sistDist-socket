import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;

public class Main {
    public static void main(String args[]) {
        System.out.println("Main");
        // Peer p = new Peer(args[0]);
        // p.multiCastSend(args[1]);
        try {
            Collaborator collaborator = new Collaborator(args[0]);
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
    ArrayList<Peer> pool = new ArrayList<Peer>();

    public Collaborator(String groupAddress) throws IOException {
        group = new Peer(groupAddress, 6789, true);
        this.start();
    }

    public void run() {
        try {
            group.send(new Message(6789));
            while (true) {
                Message message = group.receive();
                message.print();
                // ListeningGroup.multiCastSend("Ola");
                //group.multiCastListen();
            }
        } catch (IOException e) { e.printStackTrace();
        }
    }
}

class Coordenator {

}

class Message {
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

    public Peer(String addr, int port, boolean multicast) throws IOException {
        address = new InetSocketAddress(addr, port);
        socket = new DatagramSocket(port); //MulticastSocket(6789);
        if (multicast) {
            socket.joinGroup(address, NetworkInterface.getByName("le0"));
        }
        /*else {
            socket.connect(address);
        }*/
    }

    public void send(Message message) throws IOException {
        message.encode();
        socket.send(new DatagramPacket(message.bytes, message.bytes.length, address));
    }

    public Message receive() throws IOException {
        Message message = new Message();
        DatagramPacket messageIn = new DatagramPacket(message.bytes, message.bytes.length);
        socket.receive(messageIn);
        message.decode(messageIn.getData());
        return message;
    }
}