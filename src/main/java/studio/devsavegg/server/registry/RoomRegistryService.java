package studio.devsavegg.server.registry;

import java.util.Collection;
import java.util.Set;

public interface RoomRegistryService {

    /**
     * A simple data-transfer object for public room info.
     * @param roomId The room's unique ID.
     * @param roomName The room's display name.
     * @param memberCount The number of members currently in the room.
     */
    record RoomInfo(String roomId, String roomName, int memberCount) {}

    /**
     * Creates a new room and returns the invite code.
     * @param ownerClientId The client creating the room.
     * @param roomName The desired name for the room.
     * @return The unique invite code.
     */
    String createRoom(String ownerClientId, String roomName);

    /**
     * Adds a client to a room using an invitation code.
     * @param clientId The client joining.
     * @param inviteCode The invite code for the room.
     * @return The unique room ID if joined successfully, or null if code is invalid.
     */
    String joinRoom(String clientId, String inviteCode);

    /**
     * Removes a client from a specific room.
     * @param clientId The client to remove.
     * @param roomId The room to leave.
     */
    void leaveRoom(String clientId, String roomId);

    /**
     * Removes a client from all rooms they are currently in.
     * Used on client disconnect.
     * @param clientId The client to remove.
     */
    void removeClientFromAllRooms(String clientId);

    /**
     * Checks if a client is a member of a specific room.
     * @param clientId The client to check.
     * @param roomId The room to check.
     * @return true if the client is in the room, false otherwise.
     */
    boolean isClientInRoom(String clientId, String roomId);

    /**
     * Gets the unique ID for a room from its invite code.
     * @param inviteCode The invite code.
     * @return The room ID, or null if invalid.
     */
    String getRoomId(String inviteCode);

    /**
     * Gets the invite code for a room from its ID.
     * @param roomId The room ID.
     * @return The invite code, or null if invalid.
     */
    String getInviteCode(String roomId);

    /**
     * Gets the display name for a room from its ID.
     * @param roomId The room ID.
     * @return The room name, or null if not found.
     */
    String getRoomName(String roomId);

    /**
     * Gets the set of all client IDs in a specific room.
     * @param roomId The room ID.
     * @return A Set of client IDs, or null if room doesn't exist.
     */
    Set<String> getRoomMembers(String roomId);

    /**
     * Gets a list of all public rooms.
     * @return A collection of RoomInfo objects.
     */
    Collection<RoomInfo> getAllRooms();

    /**
     * Finds or creates a unique DM session between two users.
     * @param clientId1 The first client.
     * @param clientId2 The second client.
     * @return The unique context ID for this DM session.
     */
    String getOrCreateDMSession(String clientId1, String clientId2);

    /**
     * In a DM session, gets the *other* user's ID.
     * @param dmContextId The DM session's ID.
     * @param myClientId The ID of the user asking.
     * @return The ID of the other user in the session, or null.
     */
    String getOtherDMUser(String dmContextId, String myClientId);
}