package studio.devsavegg.server.gateway;

import io.netty.channel.Channel;

/**
 * A data-transfer object (record) that the Gateway
 * places into the controlQueue. This immutably holds all
 * information the Resolver thread needs to process an event.
 *
 * @param channel The client's Netty Channel (replaces javax.websocket.Session).
 * @param commandType    The raw event type (CONNECT, MESSAGE, DISCONNECT).
 * @param payload The raw string message from the client (null for CONNECT/DISCONNECT).
 * @param clientId The client's ID (to avoid registry lookups in the worker).
 * @param contextVersion The "epoch" or "version" of the client's context
 */
public record ClientCommand(
        Channel channel,
        CommandType commandType,
        String payload,
        String clientId,
        int contextVersion
) {

    public ClientCommand(Channel channel, CommandType commandType, String payload) {
        this(channel, commandType, payload, null, -1);
    }
}