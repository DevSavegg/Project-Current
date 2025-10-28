package studio.devsavegg.server.resolver;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ClientCommandType {
    CREATE_ROOM,
    JOIN_ROOM,
    SAY,
    DM,
    LIST,
    ADD_FRIEND,
    USER_INFO,
    ROOM_INFO,
    UNKNOWN; // Fallback for any command that isn't recognized

    private static final Map<String, ClientCommandType> commandMap =
            Stream.of(values())
                    .filter(v -> v != UNKNOWN)
                    .collect(Collectors.toMap(
                            v -> v.name().toUpperCase(),
                            Function.identity()
                    ));

    public static ClientCommandType fromString(String commandString) {
        if (commandString == null) {
            return UNKNOWN;
        }
        return commandMap.getOrDefault(commandString.toUpperCase(), UNKNOWN);
    }
}