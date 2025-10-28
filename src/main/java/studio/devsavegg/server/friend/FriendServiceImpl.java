package studio.devsavegg.server.friend;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FriendServiceImpl implements FriendService {
    private final Map<String, Friendship> friendships = new ConcurrentHashMap<>();

    /**
     * Creates a canonical, alphabetized key for two user IDs.
     */
    private String getCompositeKey(String id1, String id2) {
        if (id1.compareTo(id2) < 0) {
            return id1 + ":" + id2;
        } else {
            return id2 + ":" + id1;
        }
    }

    @Override
    public Friendship getFriendship(String clientId1, String clientId2) {
        return friendships.get(getCompositeKey(clientId1, clientId2));
    }

    @Override
    public FriendshipStatus getFriendshipStatus(String clientId1, String clientId2) {
        Friendship fs = getFriendship(clientId1, clientId2);
        return (fs != null) ? fs.status() : null;
    }

    @Override
    public boolean sendFriendRequest(String requesterId, String targetId) {
        String key = getCompositeKey(requesterId, targetId);

        Friendship newFs = friendships.compute(key, (k, existingFs) -> {
            if (existingFs != null && (existingFs.status() == FriendshipStatus.ACCEPTED || existingFs.status() == FriendshipStatus.BLOCKED)) {
                return existingFs;
            }

            String userA = (requesterId.compareTo(targetId) < 0) ? requesterId : targetId;
            String userB = (requesterId.compareTo(targetId) < 0) ? targetId : requesterId;
            return new Friendship(userA, userB, requesterId, FriendshipStatus.PENDING);
        });

        return newFs.status() == FriendshipStatus.PENDING && newFs.requesterId().equals(requesterId);
    }

    @Override
    public boolean acceptFriendRequest(String acceptorId, String requesterId) {
        String key = getCompositeKey(acceptorId, requesterId);

        Friendship updatedFs = friendships.computeIfPresent(key, (k, existingFs) -> {
            if (existingFs.status() == FriendshipStatus.PENDING && existingFs.requesterId().equals(requesterId)) {
                return new Friendship(existingFs.userA(), existingFs.userB(), existingFs.requesterId(), FriendshipStatus.ACCEPTED);
            }
            return existingFs;
        });

        return updatedFs != null && updatedFs.status() == FriendshipStatus.ACCEPTED;
    }

    @Override
    public boolean rejectOrCancelRequest(String removerId, String otherId) {
        String key = getCompositeKey(removerId, otherId);

        Friendship fs = friendships.get(key);
        if (fs != null && fs.status() == FriendshipStatus.PENDING) {
            return friendships.remove(key, fs);
        }
        return false;
    }

    @Override
    public boolean removeFriend(String removerId, String friendId) {
        String key = getCompositeKey(removerId, friendId);

        Friendship fs = friendships.get(key);
        if (fs != null && fs.status() == FriendshipStatus.ACCEPTED) {
            return friendships.remove(key, fs);
        }
        return false;
    }

    @Override
    public boolean blockUser(String blockerId, String targetId) {
        String key = getCompositeKey(blockerId, targetId);

        String userA = (blockerId.compareTo(targetId) < 0) ? blockerId : targetId;
        String userB = (blockerId.compareTo(targetId) < 0) ? targetId : blockerId;

        Friendship blockedFs = new Friendship(userA, userB, blockerId, FriendshipStatus.BLOCKED);

        friendships.put(key, blockedFs);
        return true;
    }

    @Override
    public Set<String> listFriends(String clientId) {
        return friendships.values().stream()
                .filter(fs -> fs.status() == FriendshipStatus.ACCEPTED)
                .filter(fs -> fs.userA().equals(clientId) || fs.userB().equals(clientId))
                .map(fs -> fs.userA().equals(clientId) ? fs.userB() : fs.userA())
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> listPendingIncomingRequests(String clientId) {
        return friendships.values().stream()
                .filter(fs -> fs.status() == FriendshipStatus.PENDING)
                .filter(fs -> fs.userA().equals(clientId) || fs.userB().equals(clientId))
                .map(Friendship::requesterId)
                .filter(s -> !s.equals(clientId))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> listPendingOutgoingRequests(String clientId) {
        return friendships.values().stream()
                .filter(fs -> fs.status() == FriendshipStatus.PENDING)
                .filter(fs -> fs.requesterId().equals(clientId))
                .map(fs -> fs.userA().equals(clientId) ? fs.userB() : fs.userA())
                .collect(Collectors.toSet());
    }
}