package studio.devsavegg.server.resolver;

import io.netty.channel.Channel;
import studio.devsavegg.server.broadcaster.BroadcastService;
import studio.devsavegg.server.friend.FriendService;
import studio.devsavegg.server.friend.FriendshipStatus;
import studio.devsavegg.server.gateway.ClientCommand;
import studio.devsavegg.server.registry.ClientRegistryService;
import studio.devsavegg.server.registry.RoomRegistryService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

public class ResolverService implements Runnable {
    private final BlockingQueue<ClientCommand> controlQueue;
    private final CommandParser commandParser;
    private final ClientRegistryService clientRegistry;
    private final RoomRegistryService roomRegistry;
    private final BroadcastService broadcastService;
    private final FriendService friendService;

    public ResolverService(BlockingQueue<ClientCommand> controlQueue,
                           CommandParser commandParser,
                           ClientRegistryService clientRegistry,
                           RoomRegistryService roomRegistry,
                           BroadcastService broadcastService,
                           FriendService friendService) {
        this.controlQueue = controlQueue;
        this.commandParser = commandParser;
        this.clientRegistry = clientRegistry;
        this.roomRegistry = roomRegistry;
        this.broadcastService = broadcastService;
        this.friendService = friendService;
    }

    @Override
    public void run() {
        System.out.println("[ResolverService] Started.");
        while (!Thread.currentThread().isInterrupted()) {
            ClientCommand command = null;
            try {
                command = controlQueue.take();

                switch (command.commandType()) {
                    case CONNECT:
                        handleConnect(command.channel(), command.payload());
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

                if (command != null) {
                    broadcastService.sendError(
                            command.channel(),
                            500,
                            (command.payload() != null ? command.payload() : "UNKNOWN"),
                            "An internal server error occurred while processing your request."
                    );
                }
            }
        }
        System.out.println("[ResolverService] Stopped.");
    }

    private void handleConnect(Channel channel, String initialUsername) {
        String clientId = "user-" + UUID.randomUUID().toString().substring(0, 8);

        clientRegistry.registerClient(clientId, channel);

        // --- Use initial username or default to client ID ---
        String finalUsername = (initialUsername != null && !initialUsername.isBlank())
                ? initialUsername
                : clientId;

        clientRegistry.setUsername(clientId, finalUsername);

        // --- Send personalized welcome message ---
        broadcastService.sendSystemMessage(channel, "WELCOME",
                "Welcome, " + finalUsername + "! Your ID is: " + clientId);

        broadcastService.sendSystemMessage(channel, "HELP",
                "Commands: /set_name, /create, /join, /leave_room, /dm, /say, /list, /add_friend, ...");

        Set<String> pendingRequests = friendService.listPendingIncomingRequests(clientId);
        if (!pendingRequests.isEmpty()) {
            broadcastService.sendSystemMessage(channel, "FRIEND_REQUESTS",
                    "You have pending friend requests from: " + pendingRequests);
        }

        System.out.println("[ResolverService] Client connected: " + clientId + " (Name: " + finalUsername + ")");
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
            broadcastService.broadcastSystemMessageToRoom(
                    currentContextId,
                    "USER_LEAVE",
                    "User '" + clientRegistry.getUsername(clientId) + "' (" + clientId + ") has left.", // Use username
                    Map.of("userId", clientId)
            );
        }
    }

    private void handleClientMessage(Channel channel, String rawMessage) {
        String clientId = clientRegistry.getClientId(channel);
        if (clientId == null) {
            broadcastService.sendError(channel, 401, "UNKNOWN", "You are not registered. Please reconnect.");
            return;
        }

        ParsedCommand command = commandParser.parse(rawMessage);
        if (command == null || command.command() == ClientCommandType.UNKNOWN) {
            broadcastService.sendError(channel, 400, rawMessage, "Unknown command. Type /help for commands.");
            return;
        }

        switch (command.command()) {
            case CREATE_ROOM:
                handleCreateRoom(clientId, command.args().getFirst());
                break;
            case JOIN_ROOM:
                handleJoinRoom(clientId, command.args().getFirst());
                break;
            case LEAVE_ROOM:
                handleLeaveRoom(clientId);
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
            case ACCEPT_FRIEND:
                handleAcceptFriend(clientId, command.args().getFirst());
                break;
            case REJECT_FRIEND:
                handleRejectFriend(clientId, command.args().getFirst());
                break;
            case REMOVE_FRIEND:
                handleRemoveFriend(clientId, command.args().getFirst());
                break;
            case SET_NAME:
                handleSetName(clientId, command.args().get(0));
                break;
            case USER_INFO:
                handleUserInfo(clientId, command.args());
                break;
            case ROOM_INFO:
                handleRoomInfo(clientId, command.args());
                break;

            default:
                broadcastService.sendError(channel, 501, command.command().name(), "Command not yet implemented.");
        }
    }

    private void handleCreateRoom(String clientId, String roomName) {
        Channel clientChannel = clientRegistry.getChannel(clientId);
        if (roomName == null || roomName.isBlank()) {
            broadcastService.sendError(clientChannel, 400, "CREATE_ROOM", "Usage: /create <room_name>");
            return;
        }

        String inviteCode = roomRegistry.createRoom(clientId, roomName);
        String roomId = roomRegistry.getRoomId(inviteCode);

        clientRegistry.setClientContext(clientId, roomId);

        broadcastService.sendSystemMessage(clientChannel,
                "ROOM_CREATED", "Room '" + roomName + "' created! Invite code: " + inviteCode);
        broadcastService.sendSystemMessage(clientChannel,
                "USER_JOIN", "You have automatically joined '" + roomName + "'.");
    }

    private void handleJoinRoom(String clientId, String inviteCode) {
        Channel clientChannel = clientRegistry.getChannel(clientId);
        if (inviteCode == null || inviteCode.isBlank()) {
            broadcastService.sendError(clientChannel, 400, "JOIN_ROOM", "Usage: /join <invite_code>");
            return;
        }

        String roomId = roomRegistry.joinRoom(clientId, inviteCode);

        if (roomId == null) {
            broadcastService.sendError(clientChannel, 404, "JOIN_ROOM", "Error: Invalid invite code.");
            return;
        }

        clientRegistry.setClientContext(clientId, roomId);
        String roomName = roomRegistry.getRoomName(roomId);

        broadcastService.sendSystemMessage(clientChannel, "USER_JOIN", "Successfully joined room: '" + roomName + "'");
        broadcastService.broadcastSystemMessageToRoom(
                roomId,
                "USER_JOIN",
                "User '" + clientRegistry.getUsername(clientId) + "' (" + clientId + ") has joined the room.", // Use username
                Map.of("userId", clientId)
        );
    }

    private void handleLeaveRoom(String clientId) {
        Channel clientChannel = clientRegistry.getChannel(clientId);
        String contextId = clientRegistry.getClientContext(clientId);

        if (contextId == null || !contextId.startsWith("room-")) {
            broadcastService.sendError(clientChannel, 400, "LEAVE_ROOM", "You are not currently in a room.");
            return;
        }

        String roomName = roomRegistry.getRoomName(contextId);
        String username = clientRegistry.getUsername(clientId);

        roomRegistry.leaveRoom(clientId, contextId);
        clientRegistry.setClientContext(clientId, null);

        broadcastService.sendSystemMessage(clientChannel, "ROOM_LEAVE", "You have left room: '" + roomName + "'.");

        broadcastService.broadcastSystemMessageToRoom(
                contextId,
                "USER_LEAVE",
                "User '" + username + "' (" + clientId + ") has left the room.",
                Map.of("userId", clientId)
        );
    }

    private void handleDirectMessage(String clientId, String targetClientId) {
        Channel clientChannel = clientRegistry.getChannel(clientId);
        if (targetClientId == null || targetClientId.isBlank()) {
            broadcastService.sendError(clientChannel, 400, "DM", "Usage: /dm <user_id>");
            return;
        }

        if (clientId.equals(targetClientId)) {
            broadcastService.sendError(clientChannel, 400, "DM", "You cannot send a DM to yourself.");
            return;
        }

        if (!clientRegistry.isClientOnline(targetClientId)) {
            broadcastService.sendError(clientChannel, 404, "DM", "Error: User '" + targetClientId + "' is not online.");
            return;
        }

        String dmContextId = roomRegistry.getOrCreateDMSession(clientId, targetClientId);
        clientRegistry.setClientContext(clientId, dmContextId);

        broadcastService.sendSystemMessage(clientChannel, "DM_START",
                "Started DM session with '" + clientRegistry.getUsername(targetClientId) + "' (" + targetClientId + ").");
    }

    private void handleSay(String clientId, String message) {
        Channel clientChannel = clientRegistry.getChannel(clientId);
        if (message == null || message.isBlank()) {
            broadcastService.sendError(clientChannel, 400, "SAY", "Usage: /say <message>");
            return;
        }

        String contextId = clientRegistry.getClientContext(clientId);

        if (contextId == null) {
            broadcastService.sendError(clientChannel, 400, "SAY", "Error: You are not in a room. Use /join or /dm first.");
            return;
        }

        if (contextId.startsWith("room-")) {
            broadcastService.broadcastChatMessage(clientId, contextId, message);
        } else if (contextId.startsWith("dm-")) {
            String targetClientId = roomRegistry.getOtherDMUser(contextId, clientId);
            if (targetClientId == null || !clientRegistry.isClientOnline(targetClientId)) {
                broadcastService.sendError(clientChannel, 404, "SAY", "Error: The other user has disconnected. Your message was not sent.");
                return;
            }
            broadcastService.sendDirectMessage(clientId, targetClientId, message);
        }
    }

    private void handleList(String clientId, List<String> args) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        if (args.isEmpty()) {
            // /list -> list all rooms
            Collection<RoomRegistryService.RoomInfo> rooms = roomRegistry.getAllRooms();
            String roomList = rooms.stream()
                    .map(r -> String.format("  - %s (ID: %s, Members: %d)", r.roomName(), r.roomId(), r.memberCount()))
                    .collect(Collectors.joining("\n"));
            broadcastService.sendSystemMessage(clientChannel, "LIST_ROOMS", "Available Rooms:\n" + (roomList.isEmpty() ? "  (None)" : roomList));
            return;
        }

        String listType = args.getFirst().toLowerCase();
        switch (listType) {
            case "users":
                String contextId = clientRegistry.getClientContext(clientId);
                if (contextId == null) {
                    broadcastService.sendError(clientChannel, 400, "LIST", "Error: You are not in a room.");
                    return;
                }
                Set<String> members = roomRegistry.getRoomMembers(contextId);
                if (members == null) {
                    broadcastService.sendError(clientChannel, 404, "LIST", "Error: Could not find members for your current room.");
                    return;
                }
                String userList = members.stream()
                        .map(id -> "  - " + clientRegistry.getUsername(id) + " (" + id + ")")
                        .collect(Collectors.joining("\n"));
                broadcastService.sendSystemMessage(clientChannel, "LIST_USERS", "Users in this room:\n" + userList);
                break;

            case "friends":
                Set<String> friends = friendService.listFriends(clientId);
                String friendList = friends.stream()
                        .map(id -> "  - " + clientRegistry.getUsername(id) + " (" + id + ") [" + (clientRegistry.isClientOnline(id) ? "Online" : "Offline") + "]")
                        .collect(Collectors.joining("\n"));
                broadcastService.sendSystemMessage(clientChannel, "LIST_FRIENDS", "Your Friends:\n" + (friendList.isEmpty() ? "  (None)" : friendList));
                break;

            case "pending_in":
                Set<String> pendingIn = friendService.listPendingIncomingRequests(clientId);
                String pendingInList = pendingIn.stream()
                        .map(id -> "  - " + clientRegistry.getUsername(id) + " (" + id + ")")
                        .collect(Collectors.joining("\n"));
                broadcastService.sendSystemMessage(clientChannel, "LIST_PENDING_IN", "Pending Incoming Requests (use /accept <id>):\n" + (pendingInList.isEmpty() ? "  (None)" : pendingInList));
                break;

            case "pending_out":
                Set<String> pendingOut = friendService.listPendingOutgoingRequests(clientId);
                String pendingOutList = pendingOut.stream()
                        .map(id -> "  - " + clientRegistry.getUsername(id) + " (" + id + ")")
                        .collect(Collectors.joining("\n"));
                broadcastService.sendSystemMessage(clientChannel, "LIST_PENDING_OUT", "Pending Outgoing Requests (use /reject <id> to cancel):\n" + (pendingOutList.isEmpty() ? "  (None)" : pendingOutList));
                break;

            default:
                broadcastService.sendError(clientChannel, 400, "LIST", "Usage: /list [users | friends | pending_in | pending_out]");
                break;
        }
    }

    private void handleAddFriend(String clientId, String targetClientId) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        if (targetClientId == null || targetClientId.isBlank()) {
            broadcastService.sendError(clientChannel, 400, "ADD_FRIEND", "Usage: /add_friend <user_id>");
            return;
        }

        if (clientId.equals(targetClientId)) {
            broadcastService.sendError(clientChannel, 400, "ADD_FRIEND", "You cannot add yourself as a friend.");
            return;
        }

        if (!clientRegistry.isClientOnline(targetClientId)) {
            broadcastService.sendError(clientChannel, 404, "ADD_FRIEND", "Error: User '" + targetClientId + "' is not online.");
            return;
        }

        FriendshipStatus status = friendService.getFriendshipStatus(clientId, targetClientId);
        if (status == FriendshipStatus.ACCEPTED) {
            broadcastService.sendError(clientChannel, 400, "ADD_FRIEND", "You are already friends with this user.");
            return;
        }
        if (status == FriendshipStatus.PENDING) {
            broadcastService.sendError(clientChannel, 400, "ADD_FRIEND", "A friend request is already pending between you.");
            return;
        }
        if (status == FriendshipStatus.BLOCKED) {
            broadcastService.sendError(clientChannel, 403, "ADD_FRIEND", "Cannot send request; a block is in place.");
            return;
        }

        if (friendService.sendFriendRequest(clientId, targetClientId)) {
            broadcastService.sendSystemMessage(clientChannel, "FRIEND_REQUEST_SENT", "Friend request sent to '" + clientRegistry.getUsername(targetClientId) + "'.");

            Channel targetChannel = clientRegistry.getChannel(targetClientId);
            if (targetChannel != null) {
                broadcastService.sendSystemMessage(targetChannel, "FRIEND_REQUEST_REV",
                        "You have a new friend request from '" + clientRegistry.getUsername(clientId) + "' (" + clientId + "). Use /accept " + clientId);
            }
        } else {
            broadcastService.sendError(clientChannel, 500, "ADD_FRIEND", "Failed to send friend request.");
        }
    }

    private void handleAcceptFriend(String clientId, String requesterId) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        if (friendService.acceptFriendRequest(clientId, requesterId)) {
            String requesterName = clientRegistry.getUsername(requesterId);
            broadcastService.sendSystemMessage(clientChannel, "FRIEND_ADDED", "You are now friends with '" + requesterName + "'.");

            Channel requesterChannel = clientRegistry.getChannel(requesterId);
            if (requesterChannel != null) {
                broadcastService.sendSystemMessage(requesterChannel, "FRIEND_ACCEPTED",
                        "'" + clientRegistry.getUsername(clientId) + "' has accepted your friend request.");
            }
        } else {
            broadcastService.sendError(clientChannel, 404, "ACCEPT_FRIEND", "No pending request found from user '" + requesterId + "'.");
        }
    }

    private void handleRejectFriend(String clientId, String otherId) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        if (friendService.rejectOrCancelRequest(clientId, otherId)) {
            broadcastService.sendSystemMessage(clientChannel, "FRIEND_REQUEST_REMOVED", "Friend request with '" + clientRegistry.getUsername(otherId) + "' removed.");

            Channel otherChannel = clientRegistry.getChannel(otherId);
            if (otherChannel != null) {
                broadcastService.sendSystemMessage(otherChannel, "FRIEND_REQUEST_DENIED",
                        "'" + clientRegistry.getUsername(clientId) + "' has rejected/canceled the friend request.");
            }
        } else {
            broadcastService.sendError(clientChannel, 404, "REJECT_FRIEND", "No pending request found with user '" + otherId + "'.");
        }
    }

    private void handleRemoveFriend(String clientId, String friendId) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        if (friendService.removeFriend(clientId, friendId)) {
            broadcastService.sendSystemMessage(clientChannel, "FRIEND_REMOVED", "You are no longer friends with '" + clientRegistry.getUsername(friendId) + "'.");

            Channel friendChannel = clientRegistry.getChannel(friendId);
            if (friendChannel != null) {
                broadcastService.sendSystemMessage(friendChannel, "FRIEND_REMOVED",
                        "'" + clientRegistry.getUsername(clientId) + "' has removed you as a friend.");
            }
        } else {
            broadcastService.sendError(clientChannel, 404, "REMOVE_FRIEND", "You are not friends with user '" + friendId + "'.");
        }
    }

    private void handleSetName(String clientId, String newName) {
        Channel clientChannel = clientRegistry.getChannel(clientId);

        if (newName == null || newName.isBlank()) {
            broadcastService.sendError(clientChannel, 400, "SET_NAME", "Usage: /set_name <new name>");
            return;
        }

        if (newName.length() > 32) {
            broadcastService.sendError(clientChannel, 400, "SET_NAME", "Name cannot be longer than 32 characters.");
            return;
        }

        String oldName = clientRegistry.getUsername(clientId);
        clientRegistry.setUsername(clientId, newName);

        broadcastService.sendSystemMessage(clientChannel, "NAME_SET", "Your name is now: " + newName);

        String contextId = clientRegistry.getClientContext(clientId);
        if (contextId != null && contextId.startsWith("room-")) {
            broadcastService.broadcastSystemMessageToRoom(
                    contextId,
                    "NAME_CHANGE",
                    "User '" + oldName + "' is now known as '" + newName + "'.",
                    Map.of("userId", clientId, "oldName", oldName, "newName", newName)
            );
        }
    }

    private void handleUserInfo(String clientId, List<String> args) {
        Channel clientChannel = clientRegistry.getChannel(clientId);
        String targetClientId = clientId;

        if (!args.isEmpty()) {
            targetClientId = args.getFirst();
        }

        if (!clientRegistry.isClientOnline(targetClientId)) {
            broadcastService.sendError(clientChannel, 404, "USER_INFO", "Error: User '" + targetClientId + "' is not online.");
            return;
        }

        String username = clientRegistry.getUsername(targetClientId);
        String currentContext = clientRegistry.getClientContext(targetClientId);
        FriendshipStatus status = friendService.getFriendshipStatus(clientId, targetClientId);

        StringBuilder info = new StringBuilder();
        info.append(String.format("--- Info for %s (%s) ---\n", username, targetClientId));
        info.append(String.format("Status: %s\n", "Online"));
        info.append(String.format("Current Context: %s\n", (currentContext != null ? currentContext : "None")));
        if (!clientId.equals(targetClientId)) {
            info.append(String.format("Friendship: %s\n", (status != null ? status : "None")));
        }

        broadcastService.sendSystemMessage(clientChannel, "USER_INFO_RESULT", info.toString());
    }

    private void handleRoomInfo(String clientId, List<String> args) {
        Channel clientChannel = clientRegistry.getChannel(clientId);
        String roomId;

        if (args.isEmpty()) {
            roomId = clientRegistry.getClientContext(clientId);
            if (roomId == null || !roomId.startsWith("room-")) {
                broadcastService.sendError(clientChannel, 400, "ROOM_INFO", "Error: You are not currently in a room. Use /room_info <room_id>");
                return;
            }
        } else {
            roomId = args.getFirst();
        }

        String roomName = roomRegistry.getRoomName(roomId);
        if (roomName == null) {
            broadcastService.sendError(clientChannel, 404, "ROOM_INFO", "Error: Room '" + roomId + "' not found.");
            return;
        }

        Set<String> members = roomRegistry.getRoomMembers(roomId);
        String inviteCode = roomRegistry.getInviteCode(roomId);

        assert members != null;
        String info = String.format("--- Info for Room '%s' ---\n", roomName) +
                String.format("ID: %s\n", roomId) +
                String.format("Invite Code: %s\n", (inviteCode != null ? inviteCode : "N/A")) +
                String.format("Member Count: %d\n", members.size()) +
                String.format("Members: %s\n", members.stream()
                        .map(id -> clientRegistry.getUsername(id) + " (" + id + ")")
                        .collect(Collectors.toSet()));

        broadcastService.sendSystemMessage(clientChannel, "ROOM_INFO_RESULT", info);
    }
}