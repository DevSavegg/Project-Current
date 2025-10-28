package studio.devsavegg.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import studio.devsavegg.server.broadcaster.BroadcastService;
import studio.devsavegg.server.broadcaster.BroadcastServiceImpl;
import studio.devsavegg.server.friend.FriendService;
import studio.devsavegg.server.friend.FriendServiceImpl;
import studio.devsavegg.server.gateway.ChatServerInitializer;
import studio.devsavegg.server.gateway.ClientCommand;
import studio.devsavegg.server.registry.ClientRegistryService;
import studio.devsavegg.server.registry.ClientRegistryServiceImpl;
import studio.devsavegg.server.registry.RoomRegistryService;
import studio.devsavegg.server.registry.RoomRegistryServiceImpl;
import studio.devsavegg.server.resolver.CommandParser;
import studio.devsavegg.server.resolver.ResolverService;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerMain {
    private final int port;

    public ServerMain(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        BlockingQueue<ClientCommand> controlQueue = new LinkedBlockingQueue<>();

        // --- Instantiate Services ---
        CommandParser commandParser = new CommandParser();
        ClientRegistryService clientRegistry = new ClientRegistryServiceImpl();
        RoomRegistryService roomRegistry = new RoomRegistryServiceImpl();
        BroadcastService broadcastService = new BroadcastServiceImpl(clientRegistry, roomRegistry);
        FriendService friendService = new FriendServiceImpl();

        // --- Instantiate Resolver Service ---
        ResolverService resolverService = new ResolverService(
                controlQueue,
                commandParser,
                clientRegistry,
                roomRegistry,
                broadcastService,
                friendService
        );
        Thread resolverThread = new Thread(resolverService, "Resolver-Thread");
        resolverThread.start();

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChatServerInitializer(controlQueue))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            System.out.println("[ServerMain] Chat Server starting on port " + port);
            ChannelFuture f = b.bind(port).sync();

            f.channel().closeFuture().sync();
        } finally {
            System.out.println("[ServerMain] Shutting down...");
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();

            resolverThread.interrupt();
            broadcastService.shutdown();
            System.out.println("[ServerMain] Server shutdown complete.");
        }
    }
}