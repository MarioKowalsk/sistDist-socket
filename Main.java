import java.net.*;
import java.io.*;

public class Main {
    public static void main(String args[]) {
        System.out.println("Main");
        //Peer p = new Peer(args[0]);
        //p.multiCastSend(args[1]);
        Process process = new Process(args[0]);
    }
}

class Process extends Thread {
    Peer ListeningGroup;

    public Process(String addr) {
        ListeningGroup = new Peer(addr);
        this.start();
    }

    public void run() {
        while (true) {
            //ListeningGroup.multiCastSend("Ola");
            ListeningGroup.multiCastListen();
        }
    }
}

class Coordenator {
    
}

class Peer{
    MulticastSocket s = null;
    NetworkInterface netIf;
    InetSocketAddress g;

    public Peer(String addr){
        try{
            InetAddress group = InetAddress.getByName(addr);
            s = new MulticastSocket(6789);
            g = new InetSocketAddress(group, 6789);
            netIf = NetworkInterface.getByName("le0");
            s.joinGroup(g, netIf);
        }catch (SocketException e){System.out.println("Socket 42: " + e.getMessage());
		}catch (IOException e){System.out.println("IO: " + e.getMessage());
        }
    }

    public void multiCastSend(String message){
        try{
            byte [] m = message.getBytes();
            DatagramPacket messageOut = new DatagramPacket(m, m.length, g);
            s.send(messageOut);	
        }catch (SocketException e){System.out.println("Socket 52: " + e.getMessage());
		}catch (IOException e){System.out.println("IO: " + e.getMessage());
        }
    }

    public void multiCastListen(){
        try{
            byte[] buffer = new byte[1000];
            DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);
            s.receive(messageIn);
            System.out.println("Received:" + new String(messageIn.getData()));
        }catch (SocketException e){System.out.println("Socket 52: " + e.getMessage());
		}catch (IOException e){System.out.println("IO: " + e.getMessage());
        }
    }
}