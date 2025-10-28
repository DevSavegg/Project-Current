package studio.devsavegg.server.resolver;

import io.netty.channel.Channel;
import studio.devsavegg.server.broadcaster.BroadcastService;
import studio.devsavegg.server.gateway.ClientCommand;
import studio.devsavegg.server.registry.ClientRegistryService;
import studio.devsavegg.server.registry.RoomRegistryService;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

public class ResolverService implements Runnable {
    private final BlockingQueue<ClientCommand> controlQueue;
    private final CommandParser commandParser;
    private final ClientRegistryService clientRegistry;
    private final RoomRegistryService roomRegistry;
    private final BroadcastService broadcastService;

    public ResolverService(BlockingQueue<ClientCommand> controlQueue,
                           CommandParser commandParser,
                           ClientRegistryService clientRegistry,
                           RoomRegistryService roomRegistry,
                           BroadcastService broadcastService) {
        this.controlQueue = controlQueue;
        this.commandParser = commandParser;
        this.clientRegistry = clientRegistry;
        this.roomRegistry = roomRegistry;
        this.broadcastService = broadcastService;
    }

    @Override
    public void run() {
        System.out.println("[ResolverService] Started.");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ClientCommand command = controlQueue.take();

                switch (command.commandType()) {
                    case CONNECT:
                        handleConnect(command.channel());
                        break;
                    case DISCONNECT:
                        handleDisconnect(command.channel());
                        break;
                    case MESSAGE:
                        handleClientMessage(command.channel(), command.payload());
                        break;
                }
            } catch (Exception e) {
                System.err.println("[ResolverService] CRITICAL ERROR processing command: " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("[ResolverService] Stopped.");
    }

    private void handleConnect(Channel channel) {
        String clientId = "user-" + UUID.randomUUID().toString().substring(0, 8);

        clientRegistry.registerClient(clientId, channel);

        broadcastService.sendSystemMessage(channel, "Welcome! Your ID is: " + clientId);
        broadcastService.sendSystemMessage(channel, "Available commands: /create <name>, /join <invite_code>, /dm <user_id>, /say <message>");
        System.out.println("[ResolverService] Client connected: " + clientId);
    }

    private void handleDisconnect(Channel channel) {
        String clientId = clientRegistry.getClientId(channel);
        if (clientId == null) {
            System.err.println("[ResolverService] Disconnect from unknown channel: " + channel.remoteAddress());
            return;
        }

        String currentContextId = clientRegistry.getClientContext(clientId);

        roomRegistry.removeClientFromAllRooms(clientId);
        clientRegistry.unregisterClient(clientId);

        System.out.println("[ResolverService] Client disconnected: " + clientId);

        if (currentContextId != null && currentContextId.startsWith("room-")) {
            broadcastService.broadcastSystemMessageToRoom(currentContextId, "User '" + clientId + "' has left.");
        }
    }

    private void handleClientMessage(Channel channel, String rawMessage) {
        String clientId = clientRegistry.getClientId(channel);
        if (clientId == null) {
            broadcastService.sendSystemMessage(channel, "Error: You are not registered. Please reconnect.");
            return;
        }

        ParsedCommand command = commandParser.parse(rawMessage);
        if (command == null || command.command() == ClientCommandType.UNKNOWN) {
            broadcastService.sendSystemMessage(channel, "Error: Unknown command. Type /help for commands.");
            return;
        }

        // --- Process the command ---
        switch (command.command()) {
            case CREATE_ROOM:
                handleCreateRoom(clientId, command.args().getFirst());
                break;
            case JOIN_ROOM:
                handleJoinRoom(clientId, command.args().getFirst());
                break;
            case DM:
                handleDirectMessage(clientId, command.args().getFirst());
                break;
            case SAY:
                handleSay(clientId, command.message());
                break;
            case LIST:
                handleList(clientId, command.args());
                break;
            case ADD_FRIEND:
                handleAddFriend(clientId, command.args().getFirst());
                break;

            case USER_INFO:
                handleUserInfo(clientId, command.args());
                break;

            case ROOM_INFO:
                handleRoomInfo(clientId, command.args());
                break;
            default:
                broadcastService.sendSystemMessage(channel, "Error: Command not yet implemented.");
        }
    }

    private void handleCreateRoom(String clientId, String roomName) {
        if (roomName == null || roomName.isBlank()) {
            broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "Usage: /create <room_name>");
            return;
        }

        String inviteCode = roomRegistry.createRoom(clientId, roomName);
        String roomId = roomRegistry.getRoomId(inviteCode);

        clientRegistry.setClientContext(clientId, roomId);

        broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId),
                "Room '" + roomName + "' created! Invite code: " + inviteCode);
        broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId),
                "You have automatically joined '" + roomName + "'.");
    }

    private void handleJoinRoom(String clientId, String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "Usage: /join <invite_code>");
            return;
        }

        Channel clientChannel = clientRegistry.getChannel(clientId);
        String roomId = roomRegistry.joinRoom(clientId, inviteCode);

        if (roomId == null) {
            broadcastService.sendSystemMessage(clientChannel, "Error: Invalid invite code.");
            return;
        }

        clientRegistry.setClientContext(clientId, roomId);
        String roomName = roomRegistry.getRoomName(roomId);

        broadcastService.sendSystemMessage(clientChannel, "Successfully joined room: '" + roomName + "'");
        broadcastService.broadcastSystemMessageToRoom(roomId, "User '" + clientId + "' has joined the room.");
    }

    private void handleDirectMessage(String clientId, String targetClientId) {
        if (targetClientId == null || targetClientId.isBlank()) {
            broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "Usage: /dm <user_id>");
            return;
        }

        if (clientId.equals(targetClientId)) {
            broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "You cannot send a DM to yourself.");
            return;
        }

        if (!clientRegistry.isClientOnline(targetClientId)) {
            broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "Error: User '" + targetClientId + "' is not online.");
            return;
        }

        String dmContextId = roomRegistry.getOrCreateDMSession(clientId, targetClientId);

        clientRegistry.setClientContext(clientId, dmContextId);

        broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "Started DM session with '" + targetClientId + "'.");
    }

    private void handleSay(String clientId, String message) {
        if (message == null || message.isBlank()) {
            broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "Usage: /say <message>");
            return;
        }

        String contextId = clientRegistry.getClientContext(clientId);

        if (contextId == null) {
            broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "Error: You are not in a room. Use /join or /dm first.");
            return;
        }

        if (contextId.startsWith("room-")) {
            broadcastService.broadcastChatMessage(clientId, contextId, message);
        } else if (contextId.startsWith("dm-")) {
            String targetClientId = roomRegistry.getOtherDMUser(contextId, clientId);
            if (targetClientId == null || !clientRegistry.isClientOnline(targetClientId)) {
                broadcastService.sendSystemMessage(clientRegistry.getChannel(clientId), "Error: The other user has disconnected. Your message was not sent.");
                return;
            }
            broadcastService.sendDirectMessage(clientId, targetClientId, message);
        }
    }

    private void handleList(String clientId, List<String> args) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        if (args.isEmpty()) {
            broadcastService.sendSystemMessage(clientChannel, "Listing all rooms: (Not yet implemented)");
            return;
        }

        String listType = args.getFirst();
        if ("users".equalsIgnoreCase(listType)) {
            String contextId = clientRegistry.getClientContext(clientId);
            if (contextId == null) {
                broadcastService.sendSystemMessage(clientChannel, "Error: You are not in a room.");
                return;
            }

            Set<String> members = roomRegistry.getRoomMembers(contextId);
            if (members == null) {
                broadcastService.sendSystemMessage(clientChannel, "Error: Could not find members for your current room.");
                return;
            }

            broadcastService.sendSystemMessage(clientChannel, "Users in this room: " + members);
        } else {
            broadcastService.sendSystemMessage(clientChannel, "Usage: /list or /list users");
        }
    }

    private void handleAddFriend(String clientId, String targetClientId) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        if (targetClientId == null || targetClientId.isBlank()) {
            broadcastService.sendSystemMessage(clientChannel, "Usage: /add_friend <user_id>");
            return;
        }

        if (clientId.equals(targetClientId)) {
            broadcastService.sendSystemMessage(clientChannel, "You cannot add yourself as a friend.");
            return;
        }

        if (!clientRegistry.isClientOnline(targetClientId)) {
            broadcastService.sendSystemMessage(clientChannel, "Error: User '" + targetClientId + "' is not online.");
            return;
        }

        // --- Logic for adding a friend here ---

        broadcastService.sendSystemMessage(clientChannel, "Friend request sent to '" + targetClientId + "'. (Not yet implemented)");
    }

    private void handleUserInfo(String clientId, List<String> args) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        String targetClientId = clientId;

        if (!args.isEmpty()) {
            targetClientId = args.getFirst();
        }

        if (!clientRegistry.isClientOnline(targetClientId)) {
            broadcastService.sendSystemMessage(clientChannel, "Error: User '" + targetClientId + "' is not online.");
            return;
        }

        String currentContext = clientRegistry.getClientContext(targetClientId);

        String info = String.format("--- Info for %s ---\n", targetClientId);
        info += String.format("Status: %s\n", "Online");
        info += String.format("Current Context: %s\n", (currentContext != null ? currentContext : "None"));

        broadcastService.sendSystemMessage(clientChannel, info);
    }

    private void handleRoomInfo(String clientId, List<String> args) {
        Channel clientChannel = clientRegistry.getChannel(clientId);
        String roomId;

        if (args.isEmpty()) {
            roomId = clientRegistry.getClientContext(clientId);
            if (roomId == null || !roomId.startsWith("room-")) {
                broadcastService.sendSystemMessage(clientChannel, "Error: You are not currently in a room. Use /room_info <room_id>");
                return;
            }
        } else {
            roomId = args.getFirst();
        }

        String roomName = roomRegistry.getRoomName(roomId);
        if (roomName == null) {
            broadcastService.sendSystemMessage(clientChannel, "Error: Room '" + roomId + "' not found.");
            return;
        }

        Set<String> members = roomRegistry.getRoomMembers(roomId);

        String info = String.format("--- Info for Room '%s' ---\n", roomName);
        info += String.format("ID: %s\n", roomId);
        info += String.format("Member Count: %d\n", (members != null ? members.size() : 0));
        info += String.format("Members: %s\n", members);
        broadcastService.sendSystemMessage(clientChannel, info);
    }
}
