package studio.devsavegg.server.broadcaster;

/**
 * The structured data object sent from the server to the client.
 * This record will be serialized to JSON.
 *
 * @param type    The type of message (SYSTEM, CHAT, DM).
 * @param sender  The ID of the user who sent it (null for SYSTEM).
 * @param context The context of the message (e.g., room name, or the other user's ID).
 * @param data    The main message content.
 */
public record ServerPayload(
        ServerPayloadType type,
        String sender,
        String context,
        String data
) {}