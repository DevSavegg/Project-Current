package studio.devsavegg.server.broadcaster;

public record ErrorPayload(
        int errorCode,
        String command,
        String message
) implements ServerPayload {}