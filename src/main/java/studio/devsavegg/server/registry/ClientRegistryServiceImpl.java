package studio.devsavegg.server.registry;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ClientRegistryServiceImpl implements ClientRegistryService {
    private record Client(
            Channel channel,
            AtomicReference<String> username,
            AtomicReference<String> context
    ) {}

    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    private final Map<Channel, String> clientChannels = new ConcurrentHashMap<>();

    @Override
    public void registerClient(String clientId, Channel channel) {
        Client newClient = new Client(
                channel,
                new AtomicReference<>(clientId),
                new AtomicReference<>(null)
        );

        clients.put(clientId, newClient);
        clientChannels.put(channel, clientId);
        System.out.println("[ClientRegistry] Client registered: " + clientId);
    }

    @Override
    public void unregisterClient(String clientId) {
        if (clientId == null) return;

        Client client = clients.remove(clientId);

        if (client != null) {
            clientChannels.remove(client.channel());
        }
        System.out.println("[ClientRegistry] Client unregistered: " + clientId);
    }

    @Override
    public Channel getChannel(String clientId) {
        Client client = clients.get(clientId);
        return (client != null) ? client.channel() : null;
    }

    @Override
    public void setUsername(String clientId, String username) {
        Client client = clients.get(clientId);
        if (client != null) {
            client.username().set(username);
            System.out.println("[ClientRegistry] Client " + clientId + " username set to: " + username);
        }
    }

    @Override
    public String getUsername(String clientId) {
        Client client = clients.get(clientId);
        return (client != null) ? client.username().get() : clientId;
    }

    @Override
    public void setClientContext(String clientId, String contextId) {
        Client client = clients.get(clientId);
        if (client != null) {
            client.context().set(contextId);
            System.out.println("[ClientRegistry] Client " + clientId + " context set to: " + contextId);
        }
    }

    @Override
    public String getClientContext(String clientId) {
        Client client = clients.get(clientId);
        return (client != null) ? client.context().get() : null;
    }

    @Override
    public String getClientId(Channel channel) {
        return clientChannels.get(channel);
    }

    @Override
    public boolean isClientOnline(String clientId) {
        return clients.containsKey(clientId);
    }

    @Override
    public int getTotalClientCount() {
        return clients.size();
    }
}