package studio.devsavegg.server.broadcaster;

public enum ServerPayloadType {
    /** A system message (e.g., welcome, error, user join/left). */
    SYSTEM,
    /** A user chat message sent to a room. */
    CHAT,
    /** A direct message between two users. */
    DM
}