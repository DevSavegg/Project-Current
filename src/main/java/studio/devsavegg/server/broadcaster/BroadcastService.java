package studio.devsavegg.server.broadcaster;

import io.netty.channel.Channel;

public interface BroadcastService {

    /**
     * Sends a chat message (from a user) to all members of a specific room.
     * @param fromClientId The ID of the client who sent the message.
     * @param roomId The ID of the room to broadcast to.
     * @param message The raw message text.
     */
    void broadcastChatMessage(String fromClientId, String roomId, String message);

    /**
     * Sends a direct message (DM) from one user to another.
     * Also sends a copy back to the sender for confirmation.
     * @param fromClientId The ID of the client sending the message.
     * @param targetClientId The ID of the recipient.
     * @param message The raw message text.
     */
    void sendDirectMessage(String fromClientId, String targetClientId, String message);

    /**
     * Sends a system message to a single client.
     * @param channel The client's channel to send to.
     * @param message The system message.
     */
    void sendSystemMessage(Channel channel, String message);

    // --- Overloaded version for more context ---
    /**
     * Sends a detailed system message to a single client.
     * @param channel The client's channel to send to.
     * @param subType A specific identifier (e.g., "WELCOME").
     * @param message The system message.
     */
    void sendSystemMessage(Channel channel, String subType, String message);


    /**
     * Sends a system message to all members of a specific room.
     * @param roomId The ID of the room to broadcast to.
     * @param message The system message.
     */
    void broadcastSystemMessageToRoom(String roomId, String message);

    // --- Overloaded version for more context ---
    /**
     * Sends a detailed system message to all members of a specific room.
     * @param roomId The ID of the room to broadcast to.
     * @param subType A specific identifier (e.g., "USER_JOIN").
     * @param message The system message.
     * @param details Extra data, like the user who joined.
     */
    void broadcastSystemMessageToRoom(String roomId, String subType, String message, java.util.Map<String, Object> details);

    // --- NEW METHOD ---
    /**
     * Sends a structured error message to a single client.
     * @param channel The client's channel.
     * @param errorCode A custom or HTTP-like error code.
     * @param command The command that failed (if applicable).
     * @param message The displayable error message.
     */
    void sendError(Channel channel, int errorCode, String command, String message);

    /**
     * Shuts down the broadcast worker pool.
     */
    void shutdown();
}