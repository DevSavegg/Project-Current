package studio.devsavegg.server.registry;

import io.netty.channel.Channel;

public interface ClientRegistryService {

    /**
     * Registers a new client.
     * @param clientId The unique ID for the client.
     * @param channel The client's Netty channel.
     */
    void registerClient(String clientId, Channel channel);

    /**
     * Unregisters a client (on disconnect).
     * @param clientId The ID of the client to remove.
     */
    void unregisterClient(String clientId);

    /**
     * Retrieves a client's channel by their ID.
     * @param clientId The ID of the client.
     * @return The Channel, or null if not found.
     */
    Channel getChannel(String clientId);

    /**
     * Sets a display name for the client.
     * @param clientId The client's ID.
     * @param username The new display name.
     */
    void setUsername(String clientId, String username);

    /**
     * Gets the client's display name.
     * @param clientId The client's ID.
     * @return The display name, or the client ID if not set.
     */
    String getUsername(String clientId);

    /**
     * Sets the client's "active context" (the room or DM session they are in).
     * @param clientId The client's ID.
     * @param contextId A unique identifier for the context (e.g., "room-123", "dm-abc-xyz").
     */
    void setClientContext(String clientId, String contextId);

    /**
     * Gets the client's "active context".
     * @param clientId The client's ID.
     * @return The context ID, or null if none is set.
     */
    String getClientContext(String clientId);

    /**
     * Retrieves a client's ID using their Channel.
     * @param channel The client's Netty Channel.
     * @return The client's ID, or null if not found.
     */
    String getClientId(Channel channel);

    /**
     * Checks if a client is currently registered (online).
     * @param clientId The client's ID to check.
     * @return true if the client is online, false otherwise.
     */
    boolean isClientOnline(String clientId);

    /**
     * Gets the total number of currently connected clients.
     * @return The count of online clients.
     */
    int getTotalClientCount();
}