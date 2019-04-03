package net.packets.items;

import entities.items.Dynamite;
import entities.items.Item;
import entities.items.ItemMaster;
import entities.items.Torch;
import game.Game;
import net.ServerLogic;
import net.lobbyhandling.Lobby;
import net.packets.Packet;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketSpawnItem extends Packet {

  private static final Logger logger = LoggerFactory.getLogger(PacketSpawnItem.class);
  private int owner;
  private Vector3f position;
  private int type;

  private String[] dataArray;

  /**
   * Created by the client to tell the server he spawned an item. Contains a dummy variable for
   * owner (will be set by the server).
   *
   * @param type item type according to {@link ItemMaster.ItemTypes}
   * @param position position of the item
   */
  public PacketSpawnItem(ItemMaster.ItemTypes type, Vector3f position) {
    super(Packet.PacketTypes.SPAWN_ITEM);
    setData("0║" + type.getItemId() + "║" + position.x + "║" + position.y + "║" + position.z);
    // No need to validate. No user input
  }

  /**
   * Server receives packet, validates it and is then ready to broadcast it to the lobby. Will pass
   * on the same data but set the owner of the id equal to the owner of the packet.
   *
   * @param clientId clientId who sent the packet
   * @param data string that contains type and position of item
   */
  public PacketSpawnItem(int clientId, String data) {
    super(PacketTypes.SPAWN_ITEM);
    setClientId(clientId);
    position = new Vector3f();
    dataArray = data.split("║");
    dataArray[0] = "" + clientId;
    validate(); // Validate and assign in one step
    setData(clientId + "║" + type + "║" + position.x + "║" + position.y + "║" + position.z);
  }

  /**
   * Client receives packet and creates an item owned by someone else.
   *
   * @param data string that contains owner, type and position of an item
   */
  public PacketSpawnItem(String data) {
    super(Packet.PacketTypes.SPAWN_ITEM);
    setData(data);
    dataArray = data.split("║");
    validate(); // Validate and assign in one step
  }

  @Override
  public void validate() {
    if (dataArray.length != 5) {
      addError("Invalid item data.");
      return;
    }
    try {
      owner = Integer.parseInt(dataArray[0]);
    } catch (NumberFormatException e) {
      addError("Invalid item owner.");
    }
    try {
      position =
          new Vector3f(
              Float.parseFloat(dataArray[2]),
              Float.parseFloat(dataArray[3]),
              Float.parseFloat(dataArray[4]));
    } catch (NumberFormatException e) {
      addError("Invalid item position data.");
    }
    try {
      type = Integer.parseInt(dataArray[1]);
    } catch (NumberFormatException e) {
      addError("Invalid item type variable.");
    }
    if (type < 1) {
      addError("Invalid item type.");
    }
  }

  /**
   * Server and client logic for item spawning.
   *
   * <p>The server will check the type of the item and if the player is in a lobby. Then the item
   * will be broadcast.
   *
   * <p>The client will do nothing if he owns the item (the item should already be spawned in that
   * case). If the client doesn't own the item, he will create it at the specified position with the
   * ownership flag set to false. Item specific flags / actions can be triggered from here as well.
   */
  @Override
  public void processData() {
    ItemMaster.ItemTypes itemType = ItemMaster.ItemTypes.getItemTypeById(type);
    if (itemType == null) {
      addError("Invalid item id.");
    }

    if (getClientId() > 0) {
      // Server side
      Lobby lobby = ServerLogic.getLobbyForClient(getClientId());
      if (lobby == null) {
        addError("Client is not in a lobby.");
      }

      if (!hasErrors()) {
        this.sendToLobby(lobby.getLobbyId());
      } else {
        logger.error(
            "Validation errors while sending Spawn Item Packet to server. " + createErrorMessage());
      }
    } else {
      // Client side
      if (!hasErrors()) {
        if (owner == Game.getActivePlayer().getClientId()) {
          return;
        }
        Item item = ItemMaster.generateItem(itemType, position);
        item.setOwned(false);
        if (item instanceof Torch) {
          ((Torch) item).checkForBlock(); // Attach to a block if placed on one.
        } else if (item instanceof Dynamite) {
          ((Dynamite) item).setActive(true); // Start ticking
        }
      } else {
        logger.error(
            "Validation errors while sending Spawn Item Packet to client. " + createErrorMessage());
      }
    }
  }
}