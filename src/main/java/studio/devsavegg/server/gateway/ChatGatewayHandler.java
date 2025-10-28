package studio.devsavegg.server.gateway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

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
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            System.out.println("[Gateway] Client connected: " + ctx.channel().remoteAddress());

            ClientCommand connectCommand = new ClientCommand(ctx.channel(), CommandType.CONNECT, null);
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
        System.err.println("[Gateway] Error caught: " + cause.getMessage());

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
            Thread.currentThread().interrupt(); // Reset interrupt status
            System.err.println("[Gateway] Failed to enqueue command; queue thread interrupted.");
        }
    }
}