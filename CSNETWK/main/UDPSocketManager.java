package main;

import java.io.IOException;
import java.net.*;

public class UDPSocketManager {
    private DatagramSocket socket;
    private int port;
    private boolean verbose;
    private InetAddress lastSenderAddress;
    private int lastSenderPort;

    public UDPSocketManager(int port) throws SocketException {
        this.port = port;
        this.socket = new DatagramSocket(port);
        this.socket.setSoTimeout(1000);
        this.verbose = false;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void sendMessage(String message, InetAddress address, int destPort) throws IOException {
        byte[] buffer = message.getBytes("UTF-8");
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, destPort);
        socket.send(packet);
        if (verbose) {
            System.out.println("[SENT] To " + address.getHostAddress() + ":" + destPort);
        }
    }

    public String receiveMessage() throws IOException {
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.receive(packet);
            this.lastSenderAddress = packet.getAddress();
            this.lastSenderPort = packet.getPort();
            String received = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
            if (verbose) {
                System.out.println("[RECV] From " + packet.getAddress().getHostAddress() +
                        ":" + packet.getPort() + " - " + received);
            }
            return received;
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            if (verbose)
                System.out.println("[INFO] Socket closed");
        }
    }

    public int getPort() {
        return port;
    }

    public InetAddress getLastSenderAddress() {
        return lastSenderAddress;
    }

    public int getLastSenderPort() {
        return lastSenderPort;
    }
}