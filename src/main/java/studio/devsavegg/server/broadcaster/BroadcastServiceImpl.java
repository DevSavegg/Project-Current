package studio.devsavegg.server.broadcaster;

import com.google.gson.Gson;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import studio.devsavegg.server.registry.ClientRegistryService;
import studio.devsavegg.server.registry.RoomRegistryService;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BroadcastServiceImpl implements BroadcastService {
    private final ExecutorService workerPool = Executors.newVirtualThreadPerTaskExecutor();
    private final Gson gson = new Gson();

    private final ClientRegistryService clientRegistry;
    private final RoomRegistryService roomRegistry;

    public BroadcastServiceImpl(ClientRegistryService clientRegistry, RoomRegistryService roomRegistry) {
        this.clientRegistry = clientRegistry;
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void broadcastChatMessage(String fromClientId, String roomId, String message) {
        String roomName = roomRegistry.getRoomName(roomId);

        ServerPayload payload = new ServerPayload(
                ServerPayloadType.CHAT,
                fromClientId,
                roomName,
                message
        );
        String jsonPayload = gson.toJson(payload);

        Set<String> members = roomRegistry.getRoomMembers(roomId);
        if (members == null) return;

        for (String memberId : members) {
            Channel channel = clientRegistry.getChannel(memberId);
            submitSendTask(channel, jsonPayload); // Send JSON
        }
    }

    @Override
    public void sendDirectMessage(String fromClientId, String targetClientId, String message) {
        ServerPayload targetPayload = new ServerPayload(
                ServerPayloadType.DM,
                fromClientId,
                fromClientId,
                message
        );
        String targetJson = gson.toJson(targetPayload);
        Channel targetChannel = clientRegistry.getChannel(targetClientId);
        submitSendTask(targetChannel, targetJson);

        ServerPayload senderPayload = new ServerPayload(
                ServerPayloadType.DM,
                fromClientId,
                targetClientId,
                message
        );
        String senderJson = gson.toJson(senderPayload);
        Channel senderChannel = clientRegistry.getChannel(fromClientId);
        submitSendTask(senderChannel, senderJson);
    }

    @Override
    public void sendSystemMessage(Channel channel, String message) {
        ServerPayload payload = new ServerPayload(
                ServerPayloadType.SYSTEM,
                null, // No sender
                null, // No context
                message
        );

        String jsonPayload = gson.toJson(payload);
        submitSendTask(channel, jsonPayload);
    }

    @Override
    public void broadcastSystemMessageToRoom(String roomId, String message) {
        ServerPayload payload = new ServerPayload(
                ServerPayloadType.SYSTEM,
                null,
                roomRegistry.getRoomName(roomId), // Context is the room name
                message
        );
        String jsonPayload = gson.toJson(payload);

        Set<String> members = roomRegistry.getRoomMembers(roomId);
        if (members == null) return;

        for (String memberId : members) {
            Channel channel = clientRegistry.getChannel(memberId);
            submitSendTask(channel, jsonPayload);
        }
    }

    @Override
    public void shutdown() {
        System.out.println("[BroadcastService] Shutting down worker pool...");
        workerPool.shutdown();
    }

    private void submitSendTask(Channel channel, String message) {
        if (channel == null || !channel.isOpen()) {
            return;
        }

        workerPool.submit(() -> {
           try {
               channel.writeAndFlush(new TextWebSocketFrame(message))
                       .addListener(future -> {
                          if (!future.isSuccess()) {
                              System.err.println("[BroadcastWorker] Failed to send message to " + channel.remoteAddress());
                              future.cause().printStackTrace();
                          }
                       });
           } catch (Exception e) {
               System.err.println("[BroadcastWorker] Exception while sending message: " + e.getMessage());
           }
        });
    }
}
