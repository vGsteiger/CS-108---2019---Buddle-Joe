package net.playerhandling;

import net.ServerLogic;
import net.packets.Packet;
import net.packets.chat.PacketChatMessageToServer;
import net.packets.lobby.*;
import net.packets.login_logout.PacketDisconnect;
import net.packets.login_logout.PacketLogin;
import net.packets.name.PacketGetName;
import net.packets.name.PacketSetName;
import net.packets.pingpong.PacketPing;
import net.packets.pingpong.PacketPong;

import java.io.*;
import java.net.Socket;


/**
 * One thread for each client.
 * This thread contains and manages the input and output streams to communicate with the client.
 * Will receive messages from their client and process them.
 * Can send messages to their client.
 */
//Client and Server code can be similar, but we don't want shared classes
@SuppressWarnings("Duplicates")
public class ClientThread implements Runnable {

    private BufferedReader input;
    private PrintWriter output;
    private final int clientId;
    private final Socket socket;
    private PingManager pingManager;

    /**
     * Create input and output streams to communicate with the client over the specified socket.
     * Also start the ping manager to survey the connection.
     *
     * @param clientSocket TCP connection socket to the server
     * @param clientId     unique identifier of the client
     */
    public ClientThread(Socket clientSocket, int clientId) {
        this.clientId = clientId;
        this.socket = clientSocket;
        System.out.println("Client details: " + clientSocket.toString());
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

        } catch (IOException e) {
            System.err.println("Streams not set up for Client.");
        }
        pingManager = new PingManager(clientId);
        new Thread(pingManager).start();
    }

    /**
     * Called when the client thread is started.
     * <p>
     * Contains the logic of the client -> server communication.
     * Receive and process messages.
     */
    @Override
    public void run() {
        while (true) {
            try {

                String in = input.readLine();

                //Something went wrong on the client side
                if(in == null){
                    continue;
                }

                //Message too short
                if(in.length() < 5){
                    System.out.println(in+" is not a valid message from the client.");
                    continue;
                }

                String code = in.substring(0,5);
                //There is a whitespace between code and data which we deliberately ignore here
                String data;
                //Check if the message has a data component
                if(in.length() < 7) {
                    data = "";
                } else {
                    data = in.substring(6);
                }

                //Print command to server console if it is not a ping/pong command
                if(!code.equals(Packet.PacketTypes.PING.getPacketCode())
                        && !code.equals(Packet.PacketTypes.PONG.getPacketCode())) {
                    System.out.println("Client #" + clientId + " sent command '" + code + "'.");
                }
                Packet p = null;
                switch (Packet.lookupPacket(code)) {
                    case LOGIN:
                        PacketLogin login = new PacketLogin(clientId, data);
                        login.processData();
                        if (!login.hasErrors()) {
                            System.out.println("Player " + ServerLogic.getPlayerList().getUsername(clientId) + " has connected.");
                        }
                        break;
                    case GET_NAME:
                        p = new PacketGetName(clientId, data);
                        break;
                    case SET_NAME:
                        p = new PacketSetName(clientId, data);
                        break;
                    case DISCONNECT:
                        p = new PacketDisconnect(clientId);
                        break;
                    case GET_LOBBIES:
                        p = new PacketGetLobbies(clientId);
                        break;
                    case CREATE_LOBBY:
                        p = new PacketCreateLobby(clientId, data);
                        break;
                    case CREATE_LOBBY_STATUS:
                        p = new PacketJoinLobby(clientId, data);
                        break;
                    case JOIN_LOBBY:
                        p = new PacketJoinLobby(clientId, data);
                        break;
                    case JOIN_LOBBY_STATUS:
                        p = new PacketJoinLobbyStatus(clientId, data);
                        break;
                    case GET_LOBBY_INFO:
                        p = new PacketGetLobbyInfo(clientId);
                        break;
                    case LEAVE_LOBBY:
                        p = new PacketLeaveLobby(clientId);
                        break;
                    case CHAT_MESSAGE_TO_SERVER:
                        p = new PacketChatMessageToServer(clientId, data);
                        break;
                    case PING:
                        p = new PacketPing(clientId, data);
                        break;
                    case PONG:
                        p = new PacketPong(clientId, data);
                        break;
                }
                if(p != null) {
                    p.processData();
                }

            } catch (IOException | NullPointerException e) {
                System.out.println("Client " + clientId + " left");
                try {
                    socket.close();
                    break;
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    /**
     * The packet generates the final message string and sends it to the stream.
     *
     * @param packet packet to send to the client
     */
    public void sendToClient(Packet packet) {
        output.println(packet.toString());
        output.flush();
    }

    public int getClientId() {
        return clientId;
    }

    public PingManager getPingManager() {
        return pingManager;
    }

    /**
     * Close the connection to the client
     */
    public void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}