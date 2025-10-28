package studio.devsavegg.server.friend;

import java.util.Set;

public interface FriendService {
    /**
     * Creates a new friend request.
     * @param requesterId The client sending the request.
     * @param targetId The client receiving the request.
     * @return true if the request was sent, false if it failed.
     */
    boolean sendFriendRequest(String requesterId, String targetId);

    /**
     * Accepts a pending friend request.
     * @param acceptorId The client accepting the request.
     * @param requesterId The client who sent the request.
     * @return true if the request was accepted, false if it failed.
     */
    boolean acceptFriendRequest(String acceptorId, String requesterId);

    /**
     * Rejects or cancels a pending friend request.
     * @param removerId The client rejecting or canceling.
     * @param otherId The other user in the request.
     * @return true if the request was removed, false if it failed.
     */
    boolean rejectOrCancelRequest(String removerId, String otherId);

    /**
     * Removes an existing friend.
     * @param removerId The client removing the friend.
     * @param friendId The friend to remove.
     * @return true if the friend was removed.
     */
    boolean removeFriend(String removerId, String friendId);

    /**
     * Blocks another user. This is a one-way action.
     * @param blockerId The client initiating the block.
     * @param targetId The client to block.
     * @return true if the block was successful.
     */
    boolean blockUser(String blockerId, String targetId);

    /**
     * Gets the set of client IDs who are friends with the user.
     * @param clientId The user.
     * @return A Set of friend IDs.
     */
    Set<String> listFriends(String clientId);

    /**
     * Gets the set of client IDs who have sent a friend request *to* this user.
     * @param clientId The user.
     * @return A Set of requester IDs.
     */
    Set<String> listPendingIncomingRequests(String clientId);

    /**
     * Gets the set of client IDs who this user has sent a friend request *to*.
     * @param clientId The user.
     * @return A Set of target IDs.
     */
    Set<String> listPendingOutgoingRequests(String clientId);

    /**
     * Gets the friendship status between two users.
     * @param clientId1 First user.
     * @param clientId2 Second user.
     * @return The FriendshipStatus, or null if no relationship exists.
     */
    FriendshipStatus getFriendshipStatus(String clientId1, String clientId2);

    /**
     * Gets the full Friendship object between two users.
     * @param clientId1 First user.
     * @param clientId2 Second user.
     * @return The Friendship object, or null.
     */
    Friendship getFriendship(String clientId1, String clientId2);
}
