import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

public class Main {
    public static void main(String args[]) {
        System.out.println("Main");
        // Peer p = new Peer(args[0]);
        // p.multiCastSend(args[1]);
        //Collaborator collaborator = new Collaborator(args[0]);
        Message message = new Message(12345, false, true, false, "Olá");
        message.print();
        message.encode();
        message.decode(message.bytes);
        message.print();
    }
}

class Collaborator extends Thread {
    Peer Group;

    public Collaborator(String addr) {
        Group = new Peer(addr);
        this.start();
    }

    public void run() {
        while (true) {
            // ListeningGroup.multiCastSend("Ola");
            //Group.multiCastListen();
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

    public Message(byte[] payload) {
        decode(payload);
    }

    public byte[] encode() {
        byte flags = (byte) ((isCoordenator ? 1<<0 : 0) +
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
            Charset.forName("UTF-8"));

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
    MulticastSocket socket = null;
    NetworkInterface networkInterface;
    InetSocketAddress address;

    public Peer(String addr) {
        try {
            InetAddress group = InetAddress.getByName(addr);
            socket = new MulticastSocket(6789);
            address = new InetSocketAddress(group, 6789);
            networkInterface = NetworkInterface.getByName("le0");
            socket.joinGroup(address, networkInterface);
        } catch (IOException e) { e.printStackTrace();
        }
    }

    public void multiCastSend(Message message) {
        try {
            message.encode();
            DatagramPacket messageOut = new DatagramPacket(message.bytes, message.bytes.length, address);
            socket.send(messageOut);
        } catch (IOException e) { e.printStackTrace();
        }
    }

    public Message multiCastListen() {
        Message message = new Message();
        try {
            //byte[] buffer = new byte[1000];
            DatagramPacket messageIn = new DatagramPacket(message.bytes, message.bytes.length);
            socket.receive(messageIn);
            System.out.println("Received:" + new String(messageIn.getData()));
            return message;
        } catch (IOException e) { e.printStackTrace();
        }
        return message;
    }
}