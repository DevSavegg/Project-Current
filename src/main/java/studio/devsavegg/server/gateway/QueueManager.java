package studio.devsavegg.server.gateway;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public record QueueManager(
        BlockingQueue<ClientCommand> connectionQueue,
        BlockingQueue<ClientCommand> managementQueue,
        List<BlockingQueue<ClientCommand>> messageQueues
) {
    public int getMessageQueueCount() {
        return messageQueues.size();
    }

    public BlockingQueue<ClientCommand> getMessageQueue(int index) {
        return messageQueues.get(index);
    }
}