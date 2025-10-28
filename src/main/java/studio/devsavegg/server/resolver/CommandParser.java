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
            if (command == ClientCommandType.LIST ||
                    command == ClientCommandType.USER_INFO ||
                    command == ClientCommandType.ROOM_INFO ||
                    command == ClientCommandType.LEAVE_ROOM) {
                return new ParsedCommand(command, commandString, Collections.emptyList(), null);
            }

            return new ParsedCommand(ClientCommandType.UNKNOWN, commandString, Collections.emptyList(), null);
        }

        return switch (command) {
            case SAY -> {
                // Format: CMD <message...>
                String message = joinParts(parts, 1);
                yield new ParsedCommand(command, commandString, Collections.emptyList(), message);
            }
            case DM, JOIN_ROOM, ADD_FRIEND, ACCEPT_FRIEND, REJECT_FRIEND, REMOVE_FRIEND, LIST, ROOM_INFO, USER_INFO -> {
                // Format: CMD <arg1>
                String targetUser = parts[1];
                yield new ParsedCommand(command, commandString, List.of(targetUser), null);
            }
            case CREATE_ROOM, SET_NAME -> {
                // Format: CMD <name...>
                String arg = joinParts(parts, 1);
                yield new ParsedCommand(command, commandString, List.of(arg), null);
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