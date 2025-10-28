package studio.devsavegg.server.resolver;

import java.util.Collections;
import java.util.List;

public class CommandParser {
    public ParsedCommand parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ParsedCommand(ClientCommandType.UNKNOWN, "", Collections.emptyList(), null);
        }

        String[] parts = payload.trim().split("\\s+");
        String commandString = parts[0];
        ClientCommandType command = ClientCommandType.fromString(commandString);

        if (parts.length == 1) {
            if (command == ClientCommandType.LIST) {
                return new ParsedCommand(command, commandString, Collections.emptyList(), null);
            }

            if (command == ClientCommandType.USER_INFO) {
                return new ParsedCommand(command, commandString, Collections.emptyList(), null);
            }

            if (command == ClientCommandType.ROOM_INFO) {
                return new ParsedCommand(command, commandString, Collections.emptyList(), null);
            }

            return new ParsedCommand(ClientCommandType.UNKNOWN, commandString, Collections.emptyList(), null);
        }

        return switch (command) {
            case SAY -> {
                // Format: CMD <message...>
                // e.g., "SAY Hello world"
                String message = joinParts(parts, 1);
                yield new ParsedCommand(command, commandString, Collections.emptyList(), message);
            }
            case DM -> {
                // Format: CMD <arg1>
                String targetUser = parts[1];
                yield new ParsedCommand(command, commandString, List.of(targetUser), null);
            }
            case CREATE_ROOM -> {
                // Format: CMD <room name...>
                String roomName = joinParts(parts, 1);
                yield new ParsedCommand(command, commandString, List.of(roomName), null);
            }
            // JOIN_ROOM <arg1>
            // ADD_FRIEND <arg1>
            // LIST <arg1>
            // ROOM_INFO <arg1>
            // USER_INFO <arg1>
            case JOIN_ROOM, ADD_FRIEND, LIST, ROOM_INFO, USER_INFO -> {
                // Format: CMD <arg1>
                // e.g., "JOIN_ROOM xyz-123"
                String arg1 = parts[1];
                yield new ParsedCommand(command, commandString, List.of(arg1), null);
            }
            default -> new ParsedCommand(ClientCommandType.UNKNOWN, commandString, Collections.emptyList(), payload);
        };
    }

    private String joinParts(String[] parts, int startIndex) {
        if (startIndex >= parts.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < parts.length; i++) {
            sb.append(parts[i]).append(" ");
        }
        return sb.toString().trim();
    }
}
