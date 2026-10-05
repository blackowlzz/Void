package ac.voidac.utils.data;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

public final class TransactionDiagnostics {
    private static final long FROZEN_SILENCE_NANOS = 5_000_000_000L;
    private static final long FROZEN_KEEP_ALIVE_WINDOW_NANOS = 30_000_000_000L;

    private volatile long lastPacketNanos;
    private volatile long lastGameplayNanos;
    private volatile long lastPongNanos;
    private volatile long lastKeepAliveNanos;
    private volatile long lastMovementNanos;
    private volatile long lastTickEndNanos;
    private volatile long unmatchedResponses;
    private volatile int lastResponseId;

    public void recordPacket(PacketTypeCommon type, long now) {
        lastPacketNanos = now;
        if (type == PacketType.Play.Client.PONG || type == PacketType.Play.Client.WINDOW_CONFIRMATION) {
            lastPongNanos = now;
            return;
        }
        if (type == PacketType.Play.Client.KEEP_ALIVE) {
            lastKeepAliveNanos = now;
            return;
        }
        if (!isGameplayPacket(type)) return;
        lastGameplayNanos = now;
        if (type == PacketType.Play.Client.CLIENT_TICK_END) {
            lastTickEndNanos = now;
        } else if (WrapperPlayClientPlayerFlying.isFlying(type) || type == PacketType.Play.Client.VEHICLE_MOVE) {
            lastMovementNanos = now;
        }
    }

    public static boolean isGameplayPacket(PacketTypeCommon type) {
        return type instanceof PacketType.Play.Client
                && type != PacketType.Play.Client.PONG
                && type != PacketType.Play.Client.WINDOW_CONFIRMATION
                && type != PacketType.Play.Client.KEEP_ALIVE;
    }

    public boolean isClientFrozen(long now) {
        return lastGameplayNanos != 0 && lastKeepAliveNanos != 0
                && now - lastGameplayNanos > FROZEN_SILENCE_NANOS
                && now - lastPongNanos > FROZEN_SILENCE_NANOS
                && now - lastKeepAliveNanos < FROZEN_KEEP_ALIVE_WINDOW_NANOS;
    }

    public void recordResponse(int id, boolean matched) {
        lastResponseId = id;
        if (!matched) unmatchedResponses++;
    }

    public String describe(long now) {
        return "lastPacketMs=" + ageMillis(lastPacketNanos, now)
                + ", lastPongMs=" + ageMillis(lastPongNanos, now)
                + ", lastKeepAliveMs=" + ageMillis(lastKeepAliveNanos, now)
                + ", lastMovementMs=" + ageMillis(lastMovementNanos, now)
                + ", lastTickEndMs=" + ageMillis(lastTickEndNanos, now)
                + ", lastGameplayMs=" + ageMillis(lastGameplayNanos, now)
                + ", unmatchedResponses=" + unmatchedResponses
                + ", lastResponseId=" + lastResponseId;
    }

    private static long ageMillis(long timestamp, long now) {
        return timestamp == 0 ? -1 : Math.max(0, (now - timestamp) / 1_000_000L);
    }
}
