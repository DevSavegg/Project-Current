package studio.devsavegg.server.friend;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FriendServiceImpl implements FriendService {
    private final Map<String, Friendship> friendships = new ConcurrentHashMap<>();

    // Map<ClientId, Set<FriendId>>
    private final Map<String, Set<String>> clientFriends = new ConcurrentHashMap<>();
    // Map<ClientId, Set<RequesterId>>
    private final Map<String, Set<String>> clientPendingIn = new ConcurrentHashMap<>();
    // Map<ClientId, Set<TargetId>>
    private final Map<String, Set<String>> clientPendingOut = new ConcurrentHashMap<>();

    private Set<String> getSet(Map<String, Set<String>> map, String clientId) {
        return map.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet());
    }

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

            if (existingFs != null && existingFs.status() == FriendshipStatus.PENDING) {
                String oldRequester = existingFs.requesterId();
                String oldTarget = oldRequester.equals(existingFs.userA()) ? existingFs.userB() : existingFs.userA();
                getSet(clientPendingOut, oldRequester).remove(oldTarget);
                getSet(clientPendingIn, oldTarget).remove(oldRequester);
            }

            String userA = (requesterId.compareTo(targetId) < 0) ? requesterId : targetId;
            String userB = (requesterId.compareTo(targetId) < 0) ? targetId : requesterId;
            return new Friendship(userA, userB, requesterId, FriendshipStatus.PENDING);
        });

        if (newFs.status() == FriendshipStatus.PENDING && newFs.requesterId().equals(requesterId)) {
            getSet(clientPendingOut, requesterId).add(targetId);
            getSet(clientPendingIn, targetId).add(requesterId);
            return true;
        }

        return false;
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

        if (updatedFs != null && updatedFs.status() == FriendshipStatus.ACCEPTED) {
            getSet(clientPendingOut, requesterId).remove(acceptorId);
            getSet(clientPendingIn, acceptorId).remove(requesterId);

            getSet(clientFriends, requesterId).add(acceptorId);
            getSet(clientFriends, acceptorId).add(requesterId);
            return true;
        }

        return false;
    }

    @Override
    public boolean rejectOrCancelRequest(String removerId, String otherId) {
        String key = getCompositeKey(removerId, otherId);

        Friendship fs = friendships.get(key);
        if (fs != null && fs.status() == FriendshipStatus.PENDING) {
            if (friendships.remove(key, fs)) {
                String requester = fs.requesterId();
                String target = requester.equals(fs.userA()) ? fs.userB() : fs.userA();
                getSet(clientPendingOut, requester).remove(target);
                getSet(clientPendingIn, target).remove(requester);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean removeFriend(String removerId, String friendId) {
        String key = getCompositeKey(removerId, friendId);

        Friendship fs = friendships.get(key);
        if (fs != null && fs.status() == FriendshipStatus.ACCEPTED) {
            if (friendships.remove(key, fs)) {
                getSet(clientFriends, removerId).remove(friendId);
                getSet(clientFriends, friendId).remove(removerId);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean blockUser(String blockerId, String targetId) {
        String key = getCompositeKey(blockerId, targetId);

        String userA = (blockerId.compareTo(targetId) < 0) ? blockerId : targetId;
        String userB = (blockerId.compareTo(targetId) < 0) ? targetId : blockerId;

        Friendship blockedFs = new Friendship(userA, userB, blockerId, FriendshipStatus.BLOCKED);

        Friendship oldFs = friendships.put(key, blockedFs);
        if (oldFs != null) {
            if (oldFs.status() == FriendshipStatus.ACCEPTED) {
                getSet(clientFriends, userA).remove(userB);
                getSet(clientFriends, userB).remove(userA);
            }
            if (oldFs.status() == FriendshipStatus.PENDING) {
                String requester = oldFs.requesterId();
                String target = requester.equals(userA) ? userB : userA;
                getSet(clientPendingOut, requester).remove(target);
                getSet(clientPendingIn, target).remove(requester);
            }
        }

        return true;
    }

    @Override
    public Set<String> listFriends(String clientId) {
        Set<String> friends = clientFriends.get(clientId);
        return (friends != null) ? friends : Collections.emptySet();
    }

    @Override
    public Set<String> listPendingIncomingRequests(String clientId) {
        Set<String> requests = clientPendingIn.get(clientId);
        return (requests != null) ? requests : Collections.emptySet();
    }

    @Override
    public Set<String> listPendingOutgoingRequests(String clientId) {
        Set<String> requests = clientPendingOut.get(clientId);
        return (requests != null) ? requests : Collections.emptySet();
    }
}