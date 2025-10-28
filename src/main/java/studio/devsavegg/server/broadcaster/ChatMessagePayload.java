package studio.devsavegg.server.broadcaster;

public record ChatMessagePayload(
        String senderId,
        String roomName,
        String message,
        long timestamp
) implements ServerPayload {}