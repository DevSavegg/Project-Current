package studio.devsavegg.server.friend;

/**
 * Represents the state of a friendship or relationship.
 *
 * @param userA       The ID of the user who comes first alphabetically.
 * @param userB       The ID of the user who comes second alphabetically.
 * @param requesterId The ID of the user who sent the *last* request.
 * @param status      The current status (PENDING, ACCEPTED, BLOCKED).
 */
public record Friendship(
        String userA,
        String userB,
        String requesterId,
        FriendshipStatus status
) {}