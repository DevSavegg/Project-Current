package studio.devsavegg.server.broadcaster;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import studio.devsavegg.server.registry.ClientRegistryService;
import studio.devsavegg.server.registry.RoomRegistryService;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BroadcastServiceImpl implements BroadcastService {
    private final ExecutorService workerPool = Executors.newVirtualThreadPerTaskExecutor();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ClientRegistryService clientRegistry;
    private final RoomRegistryService roomRegistry;

    public BroadcastServiceImpl(ClientRegistryService clientRegistry, RoomRegistryService roomRegistry) {
        this.clientRegistry = clientRegistry;
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void broadcastChatMessage(String fromClientId, String roomId, String message) {
        String roomName = roomRegistry.getRoomName(roomId);

        // Build specific payload
        ServerPayload payload = new ChatMessagePayload(
                fromClientId,
                roomName,
                message,
                System.currentTimeMillis()
        );

        String jsonPayload = serialize(payload);
        if (jsonPayload == null) return;

        Set<String> members = roomRegistry.getRoomMembers(roomId);
        if (members == null) return;

        for (String memberId : members) {
            Channel channel = clientRegistry.getChannel(memberId);
            submitSendTask(channel, jsonPayload); // Send JSON
        }
    }

    @Override
    public void sendDirectMessage(String fromClientId, String targetClientId, String message) {
        long timestamp = System.currentTimeMillis();

        // --- Send to target ---
        ServerPayload targetPayload = new DirectMessagePayload(
                fromClientId,
                fromClientId,
                message,
                timestamp
        );
        String targetJson = serialize(targetPayload);
        Channel targetChannel = clientRegistry.getChannel(targetClientId);
        submitSendTask(targetChannel, targetJson);

        // --- Send copy to sender ---
        ServerPayload senderPayload = new DirectMessagePayload(
                fromClientId,
                targetClientId,
                message,
                timestamp
        );
        String senderJson = serialize(senderPayload);
        Channel senderChannel = clientRegistry.getChannel(fromClientId);
        submitSendTask(senderChannel, senderJson);
    }

    @Override
    public void sendSystemMessage(Channel channel, String message) {
        sendSystemMessage(channel, "GENERIC", message);
    }
    @Override
    public void sendSystemMessage(Channel channel, String subType, String message) {
        ServerPayload payload = new SystemMessagePayload(
                subType,
                null, // No context
                message,
                Collections.emptyMap() // No details
        );
        submitSendTask(channel, serialize(payload));
    }

    @Override
    public void broadcastSystemMessageToRoom(String roomId, String message) {
        broadcastSystemMessageToRoom(roomId, "GENERIC", message, Collections.emptyMap());
    }
    @Override
    public void broadcastSystemMessageToRoom(String roomId, String subType, String message, Map<String, Object> details) {
        ServerPayload payload = new SystemMessagePayload(
                subType,
                roomRegistry.getRoomName(roomId),
                message,
                details
        );

        String jsonPayload = serialize(payload);
        if (jsonPayload == null) return;

        Set<String> members = roomRegistry.getRoomMembers(roomId);
        if (members == null) return;

        for (String memberId : members) {
            Channel channel = clientRegistry.getChannel(memberId);
            submitSendTask(channel, jsonPayload);
        }
    }

    @Override
    public void sendError(Channel channel, int errorCode, String command, String message) {
        ServerPayload payload = new ErrorPayload(
                errorCode,
                command,
                message
        );
        submitSendTask(channel, serialize(payload));
    }

    @Override
    public void shutdown() {
        System.out.println("[BroadcastService] Shutting down worker pool...");
        workerPool.shutdown();
    }

    /**
     * Helper to serialize a payload to JSON, handling errors.
     */
    private String serialize(ServerPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            System.err.println("[BroadcastService] CRITICAL: Failed to serialize payload: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void submitSendTask(Channel channel, String jsonPayload) {
        if (jsonPayload == null || channel == null || !channel.isOpen()) {
            return;
        }

        workerPool.submit(() -> {
            try {
                channel.writeAndFlush(new TextWebSocketFrame(jsonPayload))
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
