package studio.devsavegg.server.gateway;

import io.netty.channel.Channel;

/**
 * A data-transfer object (record) that the Gateway
 * places into the controlQueue. This immutably holds all
 * information the Resolver thread needs to process an event.
 *
 * @param channel The client's Netty Channel (replaces javax.websocket.Session).
 * @param type    The raw event type (CONNECT, MESSAGE, DISCONNECT).
 * @param payload The raw string message from the client (null for CONNECT/DISCONNECT).
 */
public record ClientCommand(Channel channel, CommandType commandType, String payload) {

}
