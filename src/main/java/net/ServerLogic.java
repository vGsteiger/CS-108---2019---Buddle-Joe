package net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import net.highscore.ServerHighscore;
import net.highscore.ServerHighscoreSerialiser;
import net.lobbyhandling.Lobby;
import net.lobbyhandling.ServerLobbyList;
import net.packets.Packet;
import net.packets.chat.PacketChatMessageToClient;
import net.packets.lobby.PacketCurLobbyInfo;
import net.packets.lobby.PacketLobbyOverview;
import net.packets.loginlogout.PacketDisconnect;
import net.playerhandling.ClientThread;
import net.playerhandling.ServerPlayer;
import net.playerhandling.ServerPlayerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Util;

/**
 * Server Logic is responsible for managing all the connections to the clients and for managing all
 * the lobbies.
 *
 * <p>The server logic listens to and accepts new connections for clients and will create a new
 * thread for each new client.
 *
 * <p>Has a list of all connected players, a list of the players with their thread and a list of all
 * the lobbies. Any request for a player(-thread) or lobby goes through the server logic.
 */
public class ServerLogic {

  public static final Logger logger = LoggerFactory.getLogger(ServerLogic.class);

  private static ServerPlayerList playerList;
  private static ServerLobbyList lobbyList;
  private static HashMap<Integer, ClientThread> clientThreadMap;
  private static ServerSocket serverSocket;
  private static ServerHighscore serverHighscore;

  /**
   * Initialize a new Server Logic. Creates the Socket to listen on. You have to call {@link
   * #waitForPlayers()} to start listening.
   *
   * @param portValue the port on which the server listens for new connections
   * @throws IOException when creating the socket fails
   */
  ServerLogic(int portValue) throws IOException {
    playerList = new ServerPlayerList();
    clientThreadMap = new HashMap<>();
    lobbyList = new ServerLobbyList();
    serverSocket = new ServerSocket(portValue);
    logger.info("Started Server on port " + portValue);

    serverHighscore = ServerHighscoreSerialiser.readServerHighscore();
  }

  /** Constructor to mock a ServerLogic for certain Unit Tests. */
  ServerLogic() {
    playerList = new ServerPlayerList();
    clientThreadMap = new HashMap<>();
    lobbyList = new ServerLobbyList();
    try {
      serverSocket = new ServerSocket(6666);
    } catch (IOException e) {
      logger.info("Not a real server due to a Unit Test.");
    }
    logger.info("Started Server on port " + 6666);

    serverHighscore = ServerHighscoreSerialiser.readServerHighscore();
  }

  /**
   * Players are managed with their own handler class: {@link ServerPlayerList}.
   *
   * @return an instance of ServerPlayerList with all the connected players and methods to manage
   *     them
   * @see ServerPlayerList
   */
  public static ServerPlayerList getPlayerList() {
    return playerList;
  }

  /**
   * Lobbies are managed with their own handler class: {@link ServerLobbyList}.
   *
   * @return an instance of ServerLobbyList with all the existing lobbies and methods to manage them
   * @see ServerLobbyList
   */
  public static ServerLobbyList getLobbyList() {
    return lobbyList;
  }

  /**
   * Gets the correct client thread and passes the packet to that thread.
   *
   * @param receiver clientId to send the packet to
   * @param packet the packet to send
   */
  public static void sendPacketToClient(int receiver, Packet packet) {
    ClientThread ct = getThreadByClientId(receiver);
    if (ct != null) {
      ct.sendToClient(packet);
    }
  }

  /**
   * Gets the correct lobby and finds all the player threads in that lobby. Then passes the packet
   * to each thread.
   *
   * @param receiverLobby lobbyId to send the packet to
   * @param packet the packet to send
   */
  public static void sendPacketToLobby(int receiverLobby, Packet packet) {
    Lobby lobby = getLobbyList().getLobby(receiverLobby);
    if (lobby == null || lobby.isEmpty()) {
      return;
    }

    for (ServerPlayer p : lobby.getLobbyPlayers()) {
      sendPacketToClient(p.getClientId(), packet);
    }
  }

  /**
   * Communication Method to send data to all clients currently not in a lobby. Calls the
   * sendToClient Method for each player on the server that is currently not in a Lobby.
   *
   * @param packet packet to distribute
   */
  public static void sendToClientsNotInALobby(Packet packet) {
    try {
      for (ServerPlayer player : getPlayerList().getPlayers().values()) {
        if (player.getCurLobbyId() == 0) {
          sendPacketToClient(player.getClientId(), packet);
        }
      }
    } catch (NullPointerException e) {
      logger.error("Not Connected to a Server.");
    }
  }

  /**
   * Broadcast Method to send a packet to all clients on the server.
   *
   * @param packet packet to be sent to all players
   */
  public static void sendBroadcastPacket(Packet packet) {
    for (ServerPlayer player : getPlayerList().getPlayers().values()) {
      sendPacketToClient(player.getClientId(), packet);
    }
  }

  /**
   * Returns the thread where one specific client is managed.
   *
   * @param clientId unique identifier number of the client
   * @return The thread where thee communication with the client is managed
   * @see ClientThread
   */
  public static ClientThread getThreadByClientId(int clientId) {
    return clientThreadMap.get(clientId);
  }

  /**
   * Remove the player from the server and inform the other players in the lobby. Check if player
   * exist in playerlist. Check if player is in a lobby. If it is not true return. If it is true
   * remove player from the lobby and from the playerlist. Closed the thread where one specific
   * client in managed. Creates and sends a {@link PacketChatMessageToClient} to all clients in the
   * same lobby that this client left the lobby during which time. Creates and sends a {@link
   * PacketCurLobbyInfo} to all clients in the lobby to inform the players about the lobby.
   *
   * <p>This can be called directly from core net classes. Other classes should use {@link
   * PacketDisconnect} to disconnect a user.
   *
   * @param clientId ID of the player to remove
   */
  public static void removePlayer(int clientId) {

    logger.debug(
        "Removing client "
            + ServerLogic.getPlayerList().getUsername(clientId)
            + " (#"
            + clientId
            + ").");

    // check if the client exists
    ServerPlayer player = ServerLogic.getPlayerList().getPlayer(clientId);
    if (player == null) {
      logger.debug("ServerPlayer already removed.");
      return;
    }

    // Close socket
    ClientThread thread = clientThreadMap.get(clientId);
    if (thread != null) {
      thread.closeSocket();
    }

    // Check if client is in lobby and remove him
    int lobbyId = player.getCurLobbyId();
    if (lobbyId == 0) {
      // delete the player from the playerlist and threadmap then we are done
      clientThreadMap.remove(clientId);
      getPlayerList().removePlayer(clientId);
      return;
    }

    Lobby lobby = ServerLogic.getLobbyList().getLobby(lobbyId);
    if (lobby != null) {
      lobby.removePlayer(clientId);
      player.setCurLobbyId(0);
    }

    // delete the player from the playerlist and threadmap
    clientThreadMap.remove(clientId);
    getPlayerList().removePlayer(clientId);

    // Inform the lobby that a player left
    if (lobby != null) {
      // set the time when the player left the lobby
      String timestamp = Util.getFormattedTimestamp();

      // send the message "[SERVER 'TIME']'username' left lobby" to the lobby
      PacketChatMessageToClient sendMessage =
          new PacketChatMessageToClient(
              clientId, "[SERVER-" + timestamp + "] " + player.getUsername() + " disconnected.");
      sendMessage.sendToLobby(lobbyId);

      // send lobbyinfo to the other player in the lobby
      PacketCurLobbyInfo packetCurLobbyInfo = new PacketCurLobbyInfo(clientId, lobbyId);
      packetCurLobbyInfo.sendToLobby(lobbyId);
      // send LobbyOverview to clients not in a lobby
      String info = "OK║" + ServerLogic.getLobbyList().getTopTen();
      new PacketLobbyOverview(clientId, info).sendToClientsNotInALobby();

      // close the client's thread
      ClientThread ct = ServerLogic.getThreadByClientId(clientId);
      if (ct != null) {
        ct.closeSocket();
      }
    }
  }

  /**
   * Return the Lobby for a client or null if the player doesn't exist or is not in a Lobby.
   *
   * @param clientId client to get the lobby for
   * @return Lobby of the client
   */
  public static Lobby getLobbyForClient(int clientId) {
    ServerPlayer player = playerList.getPlayer(clientId);
    if (player != null) {
      return player.getLobby();
    }
    return null;
  }

  public static ServerHighscore getServerHighscore() {
    return serverHighscore;
  }

  /**
   * Method to wait for incoming players and then create and start a new thread for them.
   *
   * @throws IOException when the server socket fails
   */
  void waitForPlayers() throws IOException {
    int clientId = 1; // ServerPlayer IDs start at 1

    while (true) {
      Socket clientSocket = serverSocket.accept();
      logger.info("Client Arrived");
      logger.info("Start Thread for " + clientId);
      ClientThread thread = new ClientThread(clientSocket, clientId);
      clientThreadMap.put(clientId++, thread);
      new Thread(thread).start();
    }
  }

  /** Close the server socket and stop listening to new clients. */
  void kill() {
    try {
      serverSocket.close();
    } catch (IOException e) {
      logger.warn("Could not close ServerSocket");
    }
  }
}
