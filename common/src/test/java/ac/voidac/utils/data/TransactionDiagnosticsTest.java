package ac.voidac.utils.data;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionDiagnosticsTest {
    @Test
    void distinguishesNoResponseFromUnmatchedResponses() {
        TransactionDiagnostics diagnostics = new TransactionDiagnostics();
        assertTrue(diagnostics.describe(10_000_000L).contains("lastPongMs=-1"));
        diagnostics.recordPacket(PacketType.Play.Client.PONG, 10_000_000L);
        diagnostics.recordResponse(-123, false);
        String result = diagnostics.describe(15_000_000L);
        assertTrue(result.contains("lastPongMs=5"));
        assertTrue(result.contains("unmatchedResponses=1"));
        assertTrue(result.contains("lastResponseId=-123"));
    }

    @Test
    void keepAliveDoesNotRefreshPongOrMovementAge() {
        TransactionDiagnostics diagnostics = new TransactionDiagnostics();
        diagnostics.recordPacket(PacketType.Play.Client.PONG, 1_000_000L);
        diagnostics.recordResponse(-100, true);
        diagnostics.recordPacket(PacketType.Play.Client.PLAYER_POSITION, 2_000_000L);
        diagnostics.recordPacket(PacketType.Play.Client.KEEP_ALIVE, 60_000_000_000L);
        String result = diagnostics.describe(60_001_000_000L);
        assertTrue(result.contains("lastPongMs=60000"));
        assertTrue(result.contains("lastMovementMs=59999"));
        assertTrue(result.contains("lastKeepAliveMs=1"));
        assertTrue(result.contains("unmatchedResponses=0"));
    }

    @Test
    void legacyTransactionsAndTickEndAreTrackedSeparately() {
        TransactionDiagnostics diagnostics = new TransactionDiagnostics();
        diagnostics.recordPacket(PacketType.Play.Client.WINDOW_CONFIRMATION, 1_000_000L);
        diagnostics.recordResponse(-42, true);
        diagnostics.recordPacket(PacketType.Play.Client.CLIENT_TICK_END, 3_000_000L);
        String result = diagnostics.describe(4_000_000L);
        assertTrue(result.contains("lastPongMs=3"));
        assertTrue(result.contains("lastTickEndMs=1"));
        assertTrue(result.contains("lastMovementMs=-1"));
    }

    @Test
    void clientAnsweringOnlyKeepAlivesIsFrozen() {
        TransactionDiagnostics diagnostics = new TransactionDiagnostics();
        diagnostics.recordPacket(PacketType.Play.Client.PLAYER_POSITION, 1_000_000_000L);
        diagnostics.recordPacket(PacketType.Play.Client.PONG, 2_000_000_000L);
        diagnostics.recordPacket(PacketType.Play.Client.KEEP_ALIVE, 50_000_000_000L);
        assertTrue(diagnostics.isClientFrozen(61_000_000_000L));
    }

    @Test
    void gameplayPacketsEndFrozenState() {
        TransactionDiagnostics diagnostics = new TransactionDiagnostics();
        diagnostics.recordPacket(PacketType.Play.Client.PLAYER_POSITION, 1_000_000_000L);
        diagnostics.recordPacket(PacketType.Play.Client.KEEP_ALIVE, 50_000_000_000L);
        diagnostics.recordPacket(PacketType.Play.Client.ANIMATION, 60_000_000_000L);
        assertFalse(diagnostics.isClientFrozen(61_000_000_000L));
    }

    @Test
    void missingKeepAlivesMeansConnectionIsDead() {
        TransactionDiagnostics diagnostics = new TransactionDiagnostics();
        diagnostics.recordPacket(PacketType.Play.Client.PLAYER_POSITION, 1_000_000_000L);
        diagnostics.recordPacket(PacketType.Play.Client.KEEP_ALIVE, 2_000_000_000L);
        assertFalse(diagnostics.isClientFrozen(61_000_000_000L));
    }

    @Test
    void onlyPlayPacketsOtherThanResponsesAreGameplay() {
        assertTrue(TransactionDiagnostics.isGameplayPacket(PacketType.Play.Client.PLAYER_POSITION));
        assertTrue(TransactionDiagnostics.isGameplayPacket(PacketType.Play.Client.INTERACT_ENTITY));
        assertFalse(TransactionDiagnostics.isGameplayPacket(PacketType.Play.Client.PONG));
        assertFalse(TransactionDiagnostics.isGameplayPacket(PacketType.Play.Client.WINDOW_CONFIRMATION));
        assertFalse(TransactionDiagnostics.isGameplayPacket(PacketType.Play.Client.KEEP_ALIVE));
        assertFalse(TransactionDiagnostics.isGameplayPacket(PacketType.Configuration.Client.KEEP_ALIVE));
    }

    @Test
    void clientStillAnsweringPongsIsNotFrozen() {
        TransactionDiagnostics diagnostics = new TransactionDiagnostics();
        diagnostics.recordPacket(PacketType.Play.Client.PLAYER_POSITION, 1_000_000_000L);
        diagnostics.recordPacket(PacketType.Play.Client.KEEP_ALIVE, 50_000_000_000L);
        diagnostics.recordPacket(PacketType.Play.Client.PONG, 60_000_000_000L);
        assertFalse(diagnostics.isClientFrozen(61_000_000_000L));
    }
}
