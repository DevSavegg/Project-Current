package studio.devsavegg.server.gateway;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import studio.devsavegg.server.registry.ClientRegistryService;
import studio.devsavegg.server.resolver.CommandParser;

public class ChatServerInitializer extends ChannelInitializer<SocketChannel> {
    private static final String WEBSOCKET_PATH = "/chat";

    private final QueueManager queueManager;
    private final CommandParser commandParser;
    private final ClientRegistryService clientRegistry;

    public ChatServerInitializer(QueueManager queueManager,
                                 CommandParser commandParser,
                                 ClientRegistryService clientRegistry) {
        this.queueManager = queueManager;
        this.commandParser = commandParser;
        this.clientRegistry = clientRegistry;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerCompressionHandler());

        pipeline.addLast(new WebSocketServerProtocolHandler(
                WEBSOCKET_PATH,
                null,
                true,
                65536,
                true,
                true
        ));

        pipeline.addLast(new ChatGatewayHandler(queueManager, commandParser, clientRegistry));
    }
}