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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BroadcastServiceImpl implements BroadcastService {

    // --- Configuration ---
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 100;
    private static final long SHUTDOWN_AWAIT_SECONDS = 5;

    private final ExecutorService workerPool = Executors.newVirtualThreadPerTaskExecutor();

    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 4)
    );

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
            submitSendTask(channel, jsonPayload);
        }
    }

    @Override
    public void sendDirectMessage(String fromClientId, String targetClientId, String message) {
        long timestamp = System.currentTimeMillis();

        ServerPayload targetPayload = new DirectMessagePayload(
                fromClientId,
                fromClientId,
                message,
                timestamp
        );
        String targetJson = serialize(targetPayload);
        Channel targetChannel = clientRegistry.getChannel(targetClientId);
        submitSendTask(targetChannel, targetJson);

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

        System.out.println("[BroadcastService] Shutting down retry scheduler...");
        retryScheduler.shutdown();

        try {
            if (!workerPool.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("[BroadcastService] Worker pool did not terminate, forcing shutdown...");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            if (!retryScheduler.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("[BroadcastService] Retry scheduler did not terminate, forcing shutdown...");
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
            sendWithRetry(channel, jsonPayload, MAX_RETRIES);
        });
    }

    private void sendWithRetry(Channel channel, String jsonPayload, int retriesRemaining) {
        if (!channel.isOpen()) {
            System.err.println("[BroadcastWorker] Send cancelled, channel closed for: " + channel.remoteAddress());
            return;
        }

        try {
            channel.writeAndFlush(new TextWebSocketFrame(jsonPayload))
                    .addListener(future -> {
                        if (future.isSuccess()) {
                            return;
                        }

                        Throwable cause = future.cause();

                        // 1. Check if we're out of retries or the channel is definitely dead
                        if (retriesRemaining <= 0 || !channel.isOpen()) {
                            System.err.println("[BroadcastWorker] FINAL FAILED to send message to " + channel.remoteAddress() + ". Giving up. Cause: " + cause.getMessage());
                            return;
                        }

                        // 2. Calculate exponential backoff
                        int retriesUsed = MAX_RETRIES - retriesRemaining;
                        long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, retriesUsed);

                        //System.out.println("[BroadcastWorker] Send failed to " + channel.remoteAddress() + ", retrying in " + delayMs + "ms (" + retriesRemaining + " retries left)");

                        // 3. Schedule the next attempt
                        retryScheduler.schedule(() -> {
                            workerPool.submit(() -> {
                                sendWithRetry(channel, jsonPayload, retriesRemaining - 1);
                            });
                        }, delayMs, TimeUnit.MILLISECONDS);
                    });
        }
        catch (Throwable t) {
            if (t instanceof Error) {
                System.err.println("[BroadcastWorker] FATAL ERROR during send to " + channel.remoteAddress() + ": " + t.getMessage());
                t.printStackTrace();
                throw (Error) t;
            }

            System.err.println("[BroadcastWorker] Exception while trying to send message to " + channel.remoteAddress() + ": " + t.getMessage());
        }
    }
}