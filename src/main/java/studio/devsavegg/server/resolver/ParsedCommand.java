package studio.devsavegg.server.resolver;

import java.util.List;

public record ParsedCommand(
        ClientCommandType command,
        String commandString,
        List<String> args,
        String message
) {
}