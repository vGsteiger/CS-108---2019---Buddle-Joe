package net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import net.packets.chat.PacketChatMessageToServer;
import net.packets.lobby.*;
import net.packets.login_logout.PacketDisconnect;
import net.packets.login_logout.PacketLogin;
import net.packets.name.PacketSetName;

/**
 * The client-side interface to communicate with the server.
 *
 * Provides a simple console-based Interface to communicate.
 *
 * Will start and manage the client network logic.
 *
 * @see ClientLogic
 */
public class StartNetworkOnlyClient {
    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    private static String serverIP;
    private static int serverPort;
    private static ClientLogic clientLogic;

    /**
     * Start the client logic and pass ip + port
     * @see ClientLogic
     */
    private StartNetworkOnlyClient(){
        try {
            clientLogic = new ClientLogic(serverIP, serverPort);
        } catch (IOException e){
            System.out.println("Buffer Reader does not exist. Can't find a server at the specified location.");
        } catch (NumberFormatException e1) {
            System.out.println("Port can only be a number");
        }

    }

    /**
     * Provide a console Interface for the user. Will process console input and create/send packets.
     * A primitive version of a GUI.
     *
     * It is not possible to send commands to the server directly.
     *
     * @throws IOException when the socket fails
     */
    private static void TakeInputAndAct() throws IOException{
        while (true){
            String inputMessage = br.readLine();
            if(inputMessage.equals("help")) {
                System.out.println("You can use these commands:\n" +
                        "ping - Display your average ping to the server over the last 10 seconds\n"+
                        "login <username> - Login attempt with the server\n" +
                        "name <username> - Change your username\n" +
                        "C <message> - Chat with users in your lobby\n" +
                        "lobbies - Get a list of all lobbies on the server\n"+
                        "info - Get info about the lobby you are currently in\n"+
                        "create <lobby name> - Create lobby with specified name\n"+
                        "join <lobby name> - Join lobby with specified name\n"+
                        "leave - Leave your current lobby\n"+
                        "connect - reconnect if the socket has been closed, display connection info otherwise\n" +
                        "disconnect - Disconnect from the server\n" +
                        "help - Display this message");
            } else if (inputMessage.equals("ping")) {
                System.out.println("Ping to the server over the last 10 packets: "
                        + clientLogic.getPingManager().getPing() + " ms");
            } else if (inputMessage.startsWith("name ") && inputMessage.length() > 5) {
                PacketSetName p = new PacketSetName(inputMessage.substring(5));
                p.sendToServer();
            } else if (inputMessage.equals("lobbies")) {
                PacketGetLobbies p = new PacketGetLobbies();
                p.sendToServer();
            } else if (inputMessage.startsWith("join ") && inputMessage.length() > 5) {
                PacketJoinLobby p = new PacketJoinLobby(inputMessage.substring(5));
                p.sendToServer();
            } else if(inputMessage.equals("leave")) {
                PacketLeaveLobby p = new PacketLeaveLobby();
                p.sendToServer();
            } else if (inputMessage.startsWith("create ") && inputMessage.length() > 7) {
                PacketCreateLobby p = new PacketCreateLobby(inputMessage.substring(7));
                p.sendToServer();
            } else if(inputMessage.startsWith("login ") && inputMessage.length() > 6) {
                PacketLogin p = new PacketLogin(inputMessage.substring(6));
                p.sendToServer();
            } else if(inputMessage.equals("info")) {
                PacketGetLobbyInfo p = new PacketGetLobbyInfo();
                p.sendToServer();
            } else if(inputMessage.startsWith("C ") && inputMessage.length() > 2) {
                PacketChatMessageToServer p = new PacketChatMessageToServer(inputMessage.substring(2));
                p.sendToServer();
            } else if (inputMessage.equals("disconnect")) {
                PacketDisconnect p = new PacketDisconnect();
                p.sendToServer();
                ClientLogic.getServer().close();
            } else if (inputMessage.equals("connect")) {
                if(ClientLogic.getServer().isClosed()) {
                    //Try to reconnect to the server
                    new ClientLogic(serverIP, serverPort);
                    if(ClientLogic.getServer().isClosed()) {
                        System.out.println("Connection could not be re-established. Exiting program.");
                        System.exit(-1);
                    } else {
                        System.out.println("Connection to the server re-established. Socket status: "+ClientLogic.getServer());
                        try {
                            firstLogin();
                        } catch (IOException | StringIndexOutOfBoundsException e){
                            System.out.println("Server disconnected.");
                        }
                    }
                } else {
                    System.out.println("You are still connected to the server. Socket status: "+ClientLogic.getServer());
                }
            }
        }
    }

    /**
     * Start the User Interface. You can pass an IP and Port to try and connect to. Otherwise defaults will be used.
     *
     * @param args "<server ip>:<server port>" will be validated and set to default values in case of errors
     */
    public static void main(String[] args) {

        serverIP = "127.0.0.1";
        serverPort = 11337;

        //Take ip and port from commandline and validate them
        if(args.length == 1) {
            String[] IpPort = args[0].split(":");
            if (IpPort.length == 2){
                //Validate IP
                try {
                    serverIP = IpPort[0];
                    String[] parts = serverIP.split(".");
                    if (parts.length != 4) {
                        throw new IllegalArgumentException("IPv4 not formatted properly.");
                    }
                    for (String part : parts) {
                        //This throws NumberFormatException, which is subclass of IllegalArgumentException
                        int i = Integer.parseInt(part);
                        if(i < 0 || i > 255) {
                            throw new IllegalArgumentException("IPv4 not formatted properly.");
                        }
                    }
                } catch (IllegalArgumentException e) {
                    serverIP = "127.0.0.1";
                }
                //Validate Port
                try {
                    serverPort = Integer.parseInt(IpPort[1]);
                    if (serverPort <= 0 || serverPort > 65535) { //0 can't be used for TCP connections
                        throw new IllegalArgumentException("Port out of range.");
                    }
                } catch (IllegalArgumentException e) {
                    serverPort = 11337;
                }
            }
        }

        //Start Interface
        new StartNetworkOnlyClient();
        try {
            firstLogin();
        } catch (IOException | StringIndexOutOfBoundsException e){
            System.out.println("Server disconnected.");
        }
        try {
            TakeInputAndAct();
        } catch (IOException e ) {
            System.out.println("Buffer Reader does not exist");
        }

    }

    /**
     * Welcome message on the first login that asks for a username and provides a suggestion based on the system name.
     *
     * Will create and send a login packet.
     *
     * @throws IOException when buffer reader fails
     */
    private static void firstLogin() throws IOException {
        System.out.println("Welcome player! What name would you like to give yourself? " + "\n" +
                "Your System says, that you are " + System.getProperty("user.name") +
                "." + "\n" + "Would you like to choose that name? Type Yes or " +
                "the username you would like to choose.\n");
        String answer = br.readLine();
        if(answer.trim().toLowerCase().equals("yes")){
            PacketLogin p = new PacketLogin(System.getProperty("user.name"));
            p.sendToServer();
        } else {
            PacketLogin p = new PacketLogin(answer);
            p.sendToServer();
        }
    }

    /**
     * Kill the Interface and exit the program.
     */
    void kill() {
        System.exit(0);
    }
}