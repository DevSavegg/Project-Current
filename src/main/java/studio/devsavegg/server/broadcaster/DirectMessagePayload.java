package studio.devsavegg.server.broadcaster;

public record DirectMessagePayload(
        String senderId,
        String conversationPartnerId,
        String message,
        long timestamp
) implements ServerPayload {}