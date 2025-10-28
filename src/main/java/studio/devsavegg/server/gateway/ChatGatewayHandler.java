package studio.devsavegg.server.gateway;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class ChatGatewayHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final BlockingQueue<ClientCommand> controlQueue;

    public ChatGatewayHandler(BlockingQueue<ClientCommand> controlQueue) {
        this.controlQueue = controlQueue;
    }

    /**
     * Called when the WebSocket handshake is complete and the channel is active.
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete handshake) {
            System.out.println("[Gateway] Client connected: " + ctx.channel().remoteAddress());

            String uri = handshake.requestUri();
            QueryStringDecoder decoder = new QueryStringDecoder(uri);
            Map<String, List<String>> params = decoder.parameters();

            String initialUsername = getParam(params, "username");
            // String password = getParam(params, "password"); // For future use

            ClientCommand connectCommand = new ClientCommand(ctx.channel(), CommandType.CONNECT, initialUsername);
            putCommand(connectCommand);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * Called when a new message (a TextWebSocketFrame) is received.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String message = frame.text();

        ClientCommand messageCommand = new ClientCommand(ctx.channel(), CommandType.MESSAGE, message);
        putCommand(messageCommand);
    }

    /**
     * Called when a channel becomes inactive (client disconnected).
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[Gateway] Client disconnected: " + ctx.channel().remoteAddress());

        ClientCommand disconnectCommand = new ClientCommand(ctx.channel(), CommandType.DISCONNECT, null);
        putCommand(disconnectCommand);
    }

    /**
     * Called if an exception occurs.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("[Gateway] Unhandled exception caught:");
        cause.printStackTrace();

        ClientCommand disconnectCommand = new ClientCommand(ctx.channel(), CommandType.DISCONNECT, null);
        putCommand(disconnectCommand);

        ctx.close();
    }

    /**
     * Helper to put a command in the queue, handling potential interruptions.
     */
    private void putCommand(ClientCommand command) {
        try {
            controlQueue.put(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Gateway] Failed to enqueue command; queue thread interrupted.");
        }
    }

    /**
     * Helper to safely get the first value of a query parameter.
     */
    private String getParam(Map<String, List<String>> params, String key) {
        if (params.containsKey(key) && !params.get(key).isEmpty()) {
            return params.get(key).getFirst();
        }
        return null;
    }
}