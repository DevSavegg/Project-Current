package studio.devsavegg.server.broadcaster;

import java.util.Map;

public record SystemMessagePayload(
        String subType,
        String context,
        String message,
        Map<String, Object> details
) implements ServerPayload {}