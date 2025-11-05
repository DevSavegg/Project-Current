package studio.devsavegg.server.gateway;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import studio.devsavegg.server.registry.ClientRegistryService;
import studio.devsavegg.server.resolver.CommandParser;
import studio.devsavegg.server.resolver.ParsedCommand;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class ChatGatewayHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final QueueManager queueManager;
    private final CommandParser commandParser;
    private final ClientRegistryService clientRegistry;

    public ChatGatewayHandler(QueueManager queueManager,
                              CommandParser commandParser,
                              ClientRegistryService clientRegistry) {
        this.queueManager = queueManager;
        this.commandParser = commandParser;
        this.clientRegistry = clientRegistry;
    }

    /**
     * Called when the WebSocket handshake is complete.
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete handshake) {
            //System.out.println("[Gateway] Client connected: " + ctx.channel().remoteAddress());

            String uri = handshake.requestUri();
            QueryStringDecoder decoder = new QueryStringDecoder(uri);
            String initialUsername = getParam(decoder.parameters(), "username");

            ClientCommand connectCommand = new ClientCommand(ctx.channel(), CommandType.CONNECT, initialUsername);
            putCommand(queueManager.connectionQueue(), connectCommand);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * Called when a new message is received.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String message = frame.text();

        String clientId = clientRegistry.getClientId(ctx.channel());
        if (clientId == null) {
            System.err.println("[Gateway] Message received from un-registered client (channel: " + ctx.channel().id() + "). Discarding.");
            return;
        }

        int contextVersion = clientRegistry.getContextVersion(clientId);

        ParsedCommand parsedCommand = commandParser.parse(message);

        ClientCommand clientCommand = new ClientCommand(
                ctx.channel(),
                CommandType.MESSAGE,
                message,
                clientId,
                contextVersion
        );

        switch (parsedCommand.command()) {
            case SAY, DM:
                int shardIndex = Math.abs(clientId.hashCode() % queueManager.getMessageQueueCount());
                putCommand(queueManager.getMessageQueue(shardIndex), clientCommand);
                break;

            case CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, LIST,
                 ADD_FRIEND, ACCEPT_FRIEND, REJECT_FRIEND, REMOVE_FRIEND,
                 SET_NAME, USER_INFO, ROOM_INFO, UNKNOWN:
            default:
                putCommand(queueManager.managementQueue(), clientCommand);
                break;
        }
    }

    /**
     * Called when a client disconnects.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //System.out.println("[Gateway] Client disconnected: " + ctx.channel().remoteAddress());

        ClientCommand disconnectCommand = new ClientCommand(ctx.channel(), CommandType.DISCONNECT, null);
        putCommand(queueManager.connectionQueue(), disconnectCommand);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("[Gateway] Unhandled exception caught:");
        cause.printStackTrace();

        ClientCommand disconnectCommand = new ClientCommand(ctx.channel(), CommandType.DISCONNECT, null);
        putCommand(queueManager.connectionQueue(), disconnectCommand);
        ctx.close();
    }

    private void putCommand(BlockingQueue<ClientCommand> queue, ClientCommand command) {
        try {
            queue.put(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Gateway] Failed to enqueue command; queue thread interrupted.");
        }
    }

    private String getParam(Map<String, List<String>> params, String key) {
        if (params.containsKey(key) && !params.get(key).isEmpty()) {
            return params.get(key).getFirst();
        }
        return null;
    }
}