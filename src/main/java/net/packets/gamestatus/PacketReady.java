package net.packets.gamestatus;

import game.History;
import net.ServerLogic;
import net.lobbyhandling.Lobby;
import net.packets.Packet;
import net.playerhandling.Player;

/**
 * A packed that is send from the client to the server, to inform him that the client is ready to
 * start a round. Packet-Code: READY
 *
 * @author Sebastian Schlachter
 */
public class PacketReady extends Packet {

  /**
   * Constructor that is used by the Server to build the Packet.
   *
   * @param clientId ClientId of the client that has sent this packet.
   */
  public PacketReady(int clientId) {
    // server builds
    super(PacketTypes.READY);
    setClientId(clientId);
    validate();
  }

  /**
   * Constructor that will be used by the Client to build the Packet. Which can then be send to the
   * Server. There are no parameters necessary here since the Packet has no real content(only a
   * Type, "READY").
   */
  public PacketReady() {
    // client builds
    super(PacketTypes.READY);
    validate();
  }

  @Override
  public void validate() {
    // No data to validate since it is a Empty Packet
  }

  @Override
  public void processData() {
    if (isLoggedIn() && isInALobby()) {
      Player player = ServerLogic.getPlayerList().getPlayer(getClientId());
      int lobbyId = player.getCurLobbyId();
      Lobby lobby = ServerLogic.getLobbyList().getLobby(lobbyId);
      // check ob sender der ersteller ist.
      if (getClientId() == lobby.getCreaterPlayerId()) {
        lobby.setStatus("running");
        History.openRemove(lobby.getLobbyId());
        History.runningAdd(lobby.getLobbyId(), lobby.getLobbyName());
        new PacketStartRound().sendToLobby(lobby.getLobbyId());
        // TODO: only start if all players are ready
      } else {
        // TODO: set ready for the sender
      }
    }
  }
}