package studio.devsavegg.server.broadcaster;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SystemMessagePayload.class, name = "SYSTEM"),
        @JsonSubTypes.Type(value = ChatMessagePayload.class, name = "CHAT"),
        @JsonSubTypes.Type(value = DirectMessagePayload.class, name = "DM"),
        @JsonSubTypes.Type(value = ErrorPayload.class, name = "ERROR")
})
public sealed interface ServerPayload
        permits SystemMessagePayload, ChatMessagePayload, DirectMessagePayload, ErrorPayload {
}