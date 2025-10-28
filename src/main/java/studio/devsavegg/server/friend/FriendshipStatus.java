package studio.devsavegg.server.friend;

/**
 * Defines the state of a relationship between two users.
 */
public enum FriendshipStatus {
    PENDING,  // A request has been sent
    ACCEPTED, // Users are friends
    BLOCKED   // One user has blocked the other
}
