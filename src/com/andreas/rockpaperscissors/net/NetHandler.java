package com.andreas.rockpaperscissors.net;

import com.andreas.rockpaperscissors.util.Logger;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class NetHandler<T> {
    private final static NetHandler instance = new NetHandler();

    private ServerSocket serverSocket;
    private final List<Connection> connections = new ArrayList<>();

    private int sendMessageCounter;
    private ArrayList<Peer> knownPeers = new ArrayList<>();
    private HashMap<Peer, Long> lastTimeHeardFromPeer = new HashMap<>();
    private HashMap<Peer, Integer> seenMessages = new HashMap<>();
    private Peer peer;

    public void start(){
        startAccepting();
        startSendingHeartbeats();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    private Delegate delegate;
    public interface Delegate<T> {

        void onNewMessage(T message);
        void peerNotResponding(String uniqueName);
    }

    public void setUniqueName(String uniqueName){
        this.peer = new Peer(uniqueName);
    }


    public static NetHandler getInstance() {
        return instance;
    }

    private class HeartbeatSender extends TimerTask{
        @Override
        public void run() {
            try {
                sendHeartbeat();
            } catch (IOException e) {
                Logger.log("Failed to send heartbeat");
            }
        }
    }
    private class HeartbeatCounter extends TimerTask{
        @Override
        public void run() {
            checkIfHeartbeatsHaveBeenReceived();
        }
    }

    private void checkIfHeartbeatsHaveBeenReceived() {
        for (int i = 0; i < knownPeers.size(); i++){
            Peer peer = knownPeers.get(i);
            if (peer == null)
                return;
            long now = System.currentTimeMillis();
            long lastHeartbeat = lastTimeHeardFromPeer.get(peer);
            if (now - lastHeartbeat > 3000){
                if (delegate != null)
                    delegate.peerNotResponding(peer.getName());
                seenMessages.remove(peer);
                lastTimeHeardFromPeer.remove(peer);
                knownPeers.remove(peer);
                i--;
            }
        }
    }

    private NetHandler() {

    }
    private void startSendingHeartbeats(){
        Timer timer = new Timer(true);
        timer.schedule(new HeartbeatSender(), 0, 1000);
        timer.schedule(new HeartbeatCounter(), 0, 500);
    }
    private void sendHeartbeat() throws IOException {
        NetMessage<T> netMessage = new NetMessage<>(NetMessageType.HEARTBEAT);
        sendNetMessage(netMessage);

    }


    public InetAddress getLocalHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }
    public int getLocalPort(){
        return serverSocket.getLocalPort();
    }

    void removeConnection(Connection connection){
        synchronized (connections){
            connections.remove(connection);
        }
    }

    void addConnection(Socket socket) throws IOException {
        connections.add(new Connection(socket));
        Logger.log("Added connection.");
    }

    public void connectTo(String host, int port) throws IOException {
        Logger.log("NetHandler should connect to " + host + ", " + port);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port));
        synchronized (connections){
            connections.add(new Connection(socket));
        }
    }


    public void createServerSocket(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        Logger.log("Listening on port " + port);
    }

    private void startAccepting() {
        new AcceptService(serverSocket).start();
    }

    private void broadcast(NetMessage<T> netMessage) throws IOException {
        handleIncomingMessage(netMessage);
        synchronized (connections) {
            for (Connection connection : connections)
                connection.send(netMessage);
        }
    }


    void handleIncomingMessage(NetMessage netMessage) throws IOException {
        if (!isNewMessage(netMessage))
            return;
        broadcast(netMessage);
        Logger.log("Received message " + netMessage.getType() + ", "
                + netMessage.getSender() + ", "
                + netMessage.getNumber() + ", "
                + netMessage.getContent());
        switch (netMessage.getType()) {
            case MESSAGE:
                if (delegate != null)
                    delegate.onNewMessage(netMessage.getContent());
                break;
            case HEARTBEAT:
                long now = System.currentTimeMillis();
                lastTimeHeardFromPeer.put(netMessage.getSender(), now);
                if (!knownPeers.contains(netMessage.getSender()))
                    knownPeers.add(netMessage.getSender());
                break;
            default:
                Logger.log("Received unknown message");
                break;
        }
    }

    private boolean isNewMessage(NetMessage netMessage) {
        boolean isNew = false;
        if (seenMessages.containsKey(netMessage.getSender())){
            int oldNumber = seenMessages.get(netMessage.getSender());
            if (oldNumber < netMessage.getNumber())
                isNew = true;
        }else{
            isNew = true;
        }
        if (isNew)
            seenMessages.put(netMessage.getSender(), netMessage.getNumber());
        return isNew;
    }



    public void sendMessage(T message) throws IOException {
        NetMessage<T> netMessage = new NetMessage<>(NetMessageType.MESSAGE);
        netMessage.setContent(message);
        sendNetMessage(netMessage);
    }
    private void sendNetMessage(NetMessage<T> netMessage) throws IOException {
        netMessage.setNumber(sendMessageCounter++);
        netMessage.setSender(peer);
        broadcast(netMessage);
    }

}