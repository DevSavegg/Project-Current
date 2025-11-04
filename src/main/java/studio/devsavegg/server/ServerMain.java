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
import studio.devsavegg.server.gateway.QueueManager;
import studio.devsavegg.server.registry.ClientRegistryService;
import studio.devsavegg.server.registry.ClientRegistryServiceImpl;
import studio.devsavegg.server.registry.RoomRegistryService;
import studio.devsavegg.server.registry.RoomRegistryServiceImpl;
import studio.devsavegg.server.resolver.CommandParser;
import studio.devsavegg.server.resolver.ResolverService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerMain {
    private final int port;

    // --- Define the number of resolver threads ---
    // Management threads (for /create, /join, etc.)
    // Message threads (for /say, /dm)
    private static final int MANAGEMENT_RESOLVER_THREADS = 2;
    private static final int MESSAGE_RESOLVER_THREADS = 4;

    public ServerMain(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        // --- Create all queues ---
        BlockingQueue<ClientCommand> connectionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<ClientCommand> managementQueue = new LinkedBlockingQueue<>();

        List<BlockingQueue<ClientCommand>> messageQueues = new ArrayList<>(MESSAGE_RESOLVER_THREADS);
        for (int i = 0; i < MESSAGE_RESOLVER_THREADS; i++) {
            messageQueues.add(new LinkedBlockingQueue<>());
        }

        // --- Create the QueueManager ---
        QueueManager queueManager = new QueueManager(
                connectionQueue,
                managementQueue,
                messageQueues
        );

        // --- Instantiate Services ---
        CommandParser commandParser = new CommandParser();
        ClientRegistryService clientRegistry = new ClientRegistryServiceImpl();
        RoomRegistryService roomRegistry = new RoomRegistryServiceImpl();
        BroadcastService broadcastService = new BroadcastServiceImpl(clientRegistry, roomRegistry);
        FriendService friendService = new FriendServiceImpl();

        // --- Instantiate the ResolverService ---
        ResolverService resolverService = new ResolverService(
                commandParser,
                clientRegistry,
                roomRegistry,
                broadcastService,
                friendService
        );

        // --- Create thread pools for all resolver types ---
        ExecutorService connectionResolverPool = Executors.newFixedThreadPool(1,
                r -> new Thread(r, "Connection-Resolver-Thread-0"));

        ExecutorService managementResolverPool = Executors.newFixedThreadPool(MANAGEMENT_RESOLVER_THREADS,
                r -> new Thread(r, "Management-Resolver-Thread-" + r.hashCode()));

        ExecutorService messageResolverPool = Executors.newFixedThreadPool(MESSAGE_RESOLVER_THREADS,
                r -> new Thread(r, "Message-Resolver-Thread-" + r.hashCode()));

        // Start the resolver threads
        connectionResolverPool.submit(new ResolverService.ConnectionWorker(connectionQueue, resolverService));

        for (int i = 0; i < MANAGEMENT_RESOLVER_THREADS; i++) {
            managementResolverPool.submit(new ResolverService.ManagementWorker(managementQueue, resolverService));
        }

        for (int i = 0; i < MESSAGE_RESOLVER_THREADS; i++) {
            messageResolverPool.submit(new ResolverService.MessageWorker(messageQueues.get(i), resolverService));
        }

        // --- Start Netty ---
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(0);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChatServerInitializer(queueManager, commandParser, clientRegistry))
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            System.out.println("[ServerMain] Chat Server starting on port " + port);
            ChannelFuture f = b.bind(port).sync();

            f.channel().closeFuture().sync();
        } finally {
            System.out.println("[ServerMain] Shutting down...");
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();

            // Shutdown all resolver pools
            connectionResolverPool.shutdownNow();
            managementResolverPool.shutdownNow();
            messageResolverPool.shutdownNow();

            broadcastService.shutdown();
            System.out.println("[ServerMain] Server shutdown complete.");
        }
    }
}