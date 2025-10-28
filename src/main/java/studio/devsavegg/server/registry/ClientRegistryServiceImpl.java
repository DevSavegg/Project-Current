package studio.devsavegg.server.registry;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistryServiceImpl implements ClientRegistryService {
    private final Map<String, Channel> clients = new ConcurrentHashMap<>();
    private final Map<Channel, String> clientChannels = new ConcurrentHashMap<>();
    private final Map<String, String> clientContexts = new ConcurrentHashMap<>();

    @Override
    public void registerClient(String clientId, Channel channel) {
        clients.put(clientId, channel);
        clientChannels.put(channel, clientId); // Add to reverse map
        System.out.println("[ClientRegistry] Client registered: " + clientId);
    }

    @Override
    public void unregisterClient(String clientId) {
        if (clientId == null) return;
        Channel channel = clients.remove(clientId);
        if (channel != null) {
            clientChannels.remove(channel);
        }
        clientContexts.remove(clientId); // Clean up
        System.out.println("[ClientRegistry] Client unregistered: " + clientId);
    }

    @Override
    public Channel getChannel(String clientId) {
        return clients.get(clientId);
    }

    @Override
    public void setClientContext(String clientId, String contextId) {
        if (contextId == null) {
            clientContexts.remove(clientId);
        } else {
            clientContexts.put(clientId, contextId);
        }
        System.out.println("[ClientRegistry] Client " + clientId + " context set to: " + contextId);
    }

    @Override
    public String getClientContext(String clientId) {
        return clientContexts.get(clientId);
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
