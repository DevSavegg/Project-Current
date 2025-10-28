package studio.devsavegg.server.gateway;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;

import java.util.concurrent.BlockingQueue;

public class ChatServerInitializer extends ChannelInitializer<SocketChannel> {
    private static final String WEBSOCKET_PATH = "/chat";
    private final BlockingQueue<ClientCommand> controlQueue;

    public ChatServerInitializer(BlockingQueue<ClientCommand> controlQueue) {
        this.controlQueue = controlQueue;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // --- HTTP Handlers ---
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536)); // 64kb max

        // --- WebSocket Handlers ---
        pipeline.addLast(new WebSocketServerCompressionHandler());
        pipeline.addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true));

        // --- Application Logic Handler ---
        pipeline.addLast(new ChatGatewayHandler(controlQueue));
    }
}
