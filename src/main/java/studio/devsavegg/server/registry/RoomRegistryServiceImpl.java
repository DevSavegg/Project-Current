package studio.devsavegg.server.registry;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RoomRegistryServiceImpl implements RoomRegistryService{
    private record Room(String id, String name, Set<String> members) {}
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> inviteCodes = new ConcurrentHashMap<>();

    @Override
    public String createRoom(String ownerClientId, String roomName) {
        String roomId = "room-" + generateId();
        String inviteCode = generateId(8);

        Set<String> members = ConcurrentHashMap.newKeySet();
        members.add(ownerClientId);

        Room newRoom = new Room(roomId, roomName, members);
        rooms.put(roomId, newRoom);
        inviteCodes.put(inviteCode, roomId);

        System.out.println("[RoomRegistry] Room created: " + roomName + " (ID: " + roomId + ", Code: " + inviteCode + ")");
        return inviteCode;
    }

    @Override
    public String joinRoom(String clientId, String inviteCode) {
        String roomId = inviteCodes.get(inviteCode);
        if (roomId == null) {
            return null;
        }

        Room room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.members().add(clientId);
        System.out.println("[RoomRegistry] Client " + clientId + " joined room: " + room.name());
        return room.id();
    }

    @Override
    public void leaveRoom(String clientId, String roomId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.members().remove(clientId);
            System.out.println("[RoomRegistry] Client " + clientId + " left room: " + room.name());
        }
    }

    @Override
    public void removeClientFromAllRooms(String clientId) {
        for (Room room : rooms.values()) {
            room.members().remove(clientId);
        }
        System.out.println("[RoomRegistry] Client " + clientId + " removed from all rooms.");
    }

    @Override
    public boolean isClientInRoom(String clientId, String roomId) {
        Room room = rooms.get(roomId);
        return room != null && room.members().contains(clientId);
    }

    @Override
    public String getRoomId(String inviteCode) {
        return inviteCodes.get(inviteCode);
    }

    @Override
    public String getRoomName(String roomId) {
        Room room = rooms.get(roomId);
        return (room != null) ? room.name() : null;
    }

    @Override
    public Set<String> getRoomMembers(String roomId) {
        Room room = rooms.get(roomId);
        return (room != null) ? room.members() : null;
    }

    @Override
    public String getOrCreateDMSession(String clientId1, String clientId2) {
        String dmId;
        if (clientId1.compareTo(clientId2) < 0) {
            dmId = "dm-" + clientId1 + "-" + clientId2;
        } else {
            dmId = "dm-" + clientId2 + "-" + clientId1;
        }

        return rooms.computeIfAbsent(dmId, id -> {
            System.out.println("[RoomRegistry] Creating DM session: " + id);
            Set<String> members = ConcurrentHashMap.newKeySet();
            members.add(clientId1);
            members.add(clientId2);
            String dmName = "DM: " + clientId1 + " / " + clientId2;
            return new Room(id, dmName, members);
        }).id();
    }

    @Override
    public String getOtherDMUser(String dmContextId, String myClientId) {
        Room dmSession = rooms.get(dmContextId);
        if (dmSession == null) {
            return null;
        }

        for (String member : dmSession.members()) {
            if (!member.equals(myClientId)) {
                return member;
            }
        }
        return null;
    }

    // --- Helpers ---

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    private String generateId(int length) {
        return UUID.randomUUID().toString().substring(0, Math.min(length, 36));
    }
}
