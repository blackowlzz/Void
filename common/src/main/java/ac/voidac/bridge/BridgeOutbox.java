package ac.voidac.bridge;

import ac.voidac.bridge.protocol.MessageType;
import org.jetbrains.annotations.NotNull;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class BridgeOutbox {
    interface Transport {
        boolean deliver(@NotNull MessageType type, byte @NotNull [] payload);
    }

    private record Frame(MessageType type, byte[] payload) {
    }

    private final Transport transport;
    private final int maxPending;
    private final Deque<Frame> pending = new ConcurrentLinkedDeque<>();
    private final ThreadPoolExecutor writer;

    BridgeOutbox(@NotNull Transport transport, int maxPending, int maxQueuedWrites, @NotNull String threadName) {
        this.transport = transport;
        this.maxPending = maxPending;
        this.writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(maxQueuedWrites), task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    void submit(@NotNull MessageType type, byte @NotNull [] payload) {
        Frame frame = new Frame(type, payload);
        try {
            writer.execute(() -> write(frame));
        } catch (RejectedExecutionException saturated) {
            park(frame);
        }
    }

    void flush() {
        try {
            writer.execute(this::drainPending);
        } catch (RejectedExecutionException ignored) {
        }
    }

    void clear() {
        pending.clear();
    }

    int pendingCount() {
        return pending.size();
    }

    void shutdown(long awaitMillis) {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(awaitMillis, TimeUnit.MILLISECONDS)) writer.shutdownNow();
        } catch (InterruptedException interrupted) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void write(Frame frame) {
        if (!isDurable(frame.type())) {
            deliver(frame);
            return;
        }
        drainPending();
        if (pending.isEmpty() && deliver(frame)) return;
        park(frame);
    }

    private void drainPending() {
        Frame next;
        while ((next = pending.pollFirst()) != null) {
            if (!deliver(next)) {
                pending.addFirst(next);
                return;
            }
        }
    }

    private boolean deliver(Frame frame) {
        try {
            return transport.deliver(frame.type(), frame.payload());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private void park(Frame frame) {
        if (!isDurable(frame.type())) return;
        while (pending.size() >= maxPending) pending.pollFirst();
        pending.addLast(frame);
    }

    private static boolean isDurable(MessageType type) {
        return type == MessageType.BAN || type == MessageType.UNBAN;
    }
}
