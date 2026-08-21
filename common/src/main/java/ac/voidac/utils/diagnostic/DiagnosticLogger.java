package ac.voidac.utils.diagnostic;

import ac.voidac.player.VoidPlayer;
import ac.voidac.predictionengine.UncertaintyHandler;
import ac.voidac.utils.data.VectorData;
import ac.voidac.utils.math.Vector3dm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import java.util.OptionalInt;

/**
 * Per-player movement diagnostic recorder.
 *
 * Writes one structured block per prediction tick to a log file.
 * File is located at: plugins/Void/diagnostic/<PlayerName>-<timestamp>.log
 *
 * All file I/O is offloaded to a background daemon thread via a bounded queue.
 * Flags from any check accumulate in a per-tick buffer and are flushed together
 * with the prediction data at the end of each prediction tick.
 *
 * HOW TO READ THE LOG:
 *   PASS lines with offset near 0.000E+0: clean, legitimate movement.
 *   PASS lines with offset near threshold: borderline, watch for patterns.
 *   FLAG lines: check UNCERT row to distinguish false flag vs cheat.
 *     - If lerp=Y, slime=Y, or edge=Y: likely a false flag from an engine edge case.
 *     - If all UNCERT fields are N and offset >> threshold: likely a cheat.
 *   Compare ACTUAL vs PREDICTED vectors to understand the engine error direction.
 *   003=Y means the 0.03 movement optimization fired; prediction is less certain this tick.
 */
public final class DiagnosticLogger {

    private static final String POISON_PILL = "\0CLOSE";
    private static final int LINE_WIDTH = 82;
    private static final String HDR_FILL = "═".repeat(LINE_WIDTH);
    private static final String SEC_FILL = "─".repeat(60);
    private static final ThreadLocal<SimpleDateFormat> TIME_FMT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss.SSS"));

    private final BlockingQueue<String> queue;
    private final Thread writerThread;
    private final AtomicLong tickCounter = new AtomicLong(0);

    // Flags accumulated during the current tick, flushed by recordTick()
    // Written on the prediction thread only; no sync needed.
    private final StringBuilder pendingFlags = new StringBuilder(128);

    public DiagnosticLogger(File pluginDataFolder, VoidPlayer player, int queueCapacity) {
        this.queue = new ArrayBlockingQueue<>(Math.max(256, queueCapacity));

        File dir = new File(pluginDataFolder, "diagnostic");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File file = new File(dir, player.getName() + "-" + ts + ".log");

        writerThread = new Thread(() -> {
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), 65536)) {
                // Write file header
                w.write("# VoidAC Diagnostic Log");
                w.newLine();
                w.write("# Player: " + player.getName() + " | Started: " + new Date());
                w.newLine();
                w.write("# Client: " + player.getClientVersion().getReleaseName()
                        + " | UUID: " + player.uuid);
                w.newLine();
                w.write("#");
                w.newLine();
                w.write("# READING GUIDE:");
                w.newLine();
                w.write("#   PASS + offset~0    = clean movement");
                w.newLine();
                w.write("#   PASS + offset~threshold = borderline (watch for streaks)");
                w.newLine();
                w.write("#   FLAG + UNCERT all N = likely a real cheat");
                w.newLine();
                w.write("#   FLAG + lerp/slime/edge=Y = likely an engine false flag");
                w.newLine();
                w.write("#   003=Y               = 0.03 optimization active, prediction less certain");
                w.newLine();
                w.write("#   tp=Y                = teleport in last 5 ticks, skip flag analysis");
                w.newLine();
                w.newLine();

                while (true) {
                    String line = queue.take();
                    if (line == POISON_PILL) break;
                    w.write(line);
                }
                w.flush();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Don't crash the server for a diagnostic tool
            }
        }, "void-diag-" + player.getName());
        writerThread.setDaemon(true);
        writerThread.start();
    }

    /**
     * Called from OffsetHandler after prediction completes.
     * Emits the full tick block including any flags accumulated since last call.
     */
    public void recordTick(VoidPlayer player, double offset, boolean flaggedBySim,
                           String simVerbose, boolean checked) {
        long tick = tickCounter.incrementAndGet();
        UncertaintyHandler u = player.uncertaintyHandler;
        Vector3dm actual = player.actualMovement;
        VectorData pred = player.predictedVelocity;

        StringBuilder sb = new StringBuilder(640);

        // ─── Header ────────────────────────────────────────────────────────────────
        String hdr = "═ T#" + String.format("%08d", tick)
                + " | " + TIME_FMT.get().format(new Date())
                + " | ping=" + String.format("%3d", player.getTransactionPing()) + "ms ";
        sb.append(hdr);
        int pad = LINE_WIDTH - hdr.length();
        if (pad > 0) sb.append(HDR_FILL, 0, pad);
        sb.append('\n');

        // ─── Player ────────────────────────────────────────────────────────────────
        sb.append("  PLAYER  ").append(player.getName())
          .append(" | ").append(player.getClientVersion().getReleaseName())
          .append(" | GM:").append(player.gamemode.name().toLowerCase())
          .append('\n');

        // ─── Position ──────────────────────────────────────────────────────────────
        sb.append("  POS     ")
          .append(String.format("x=%+011.5f  y=%+010.5f  z=%+011.5f", player.x, player.y, player.z))
          .append('\n');

        // ─── Look ──────────────────────────────────────────────────────────────────
        sb.append("  LOOK    ")
          .append(String.format("yaw=%+08.3f  pitch=%+08.3f", player.yaw, player.pitch))
          .append('\n');

        // ─── State ─────────────────────────────────────────────────────────────────
        sb.append("  STATE   ")
          .append("ground=").append(flag(player.onGround))
          .append("  sneak=").append(flag(player.isSneaking))
          .append("  sprint=").append(flag(player.isSprinting))
          .append("  swim=").append(flag(player.isSwimming))
          .append("  climb=").append(flag(player.isClimbing))
          .append("  fly=").append(flag(player.isFlying))
          .append("  glide=").append(flag(player.isGliding))
          .append("  veh=").append(flag(player.inVehicle()))
          .append("  water=").append(flag(player.wasTouchingWater))
          .append("  lava=").append(flag(player.wasTouchingLava))
          .append('\n');

        // speed and the effects feeding it. a beacon desyncing here is invisible
        // in the offset alone, you need the number the engine actually used
        sb.append("  SPEED   ")
          .append("attr=").append(player.speed)
          .append("  speed=").append(effectLevel(player, PotionTypes.SPEED))
          .append("  slow=").append(effectLevel(player, PotionTypes.SLOWNESS))
          .append("  jump=").append(effectLevel(player, PotionTypes.JUMP_BOOST))
          .append("  levit=").append(effectLevel(player, PotionTypes.LEVITATION))
          .append('\n');

        // ─── Uncertainty ───────────────────────────────────────────────────────────
        int entCount = u.collidingEntities.isEmpty() ? 0
                : u.collidingEntities.get(u.collidingEntities.size() - 1);
        sb.append("  UNCERT  ")
          .append("ents=").append(entCount)
          .append("  slime=").append(flag(u.isSteppingOnSlime || u.wasSteppingOnSlime))
          .append("  ice=").append(flag(u.isSteppingOnIce))
          .append("  honey=").append(flag(u.isSteppingOnHoney))
          .append("  lerp=").append(flag(u.lastHardCollidingLerpingEntity.hasOccurredSince(3)))
          .append("  003=").append(flag(u.lastMovementWasZeroPointZeroThree))
          .append("  003unk=").append(flag(u.lastMovementWasUnknown003VectorReset))
          .append("  tp=").append(flag(u.lastTeleportTicks.hasOccurredSince(5)))
          .append("  edge=").append(flag(u.stuckOnEdge.hasOccurredSince(2)))
          .append("  scaf=").append(flag(u.isSteppingNearScaffolding))
          .append("  shulk=").append(flag(u.isSteppingNearShulker))
          .append("  glitch=").append(flag(u.isOrWasNearGlitchyBlock))
          .append('\n');

        // ─── Uncertainty margins ───────────────────────────────────────────────────
        if (u.xNegativeUncertainty != 0 || u.xPositiveUncertainty != 0
                || u.yNegativeUncertainty != 0 || u.yPositiveUncertainty != 0
                || u.zNegativeUncertainty != 0 || u.zPositiveUncertainty != 0) {
            sb.append("  MARGINS ")
              .append(String.format("x=[%.4f,+%.4f]  y=[%.4f,+%.4f]  z=[%.4f,+%.4f]",
                      u.xNegativeUncertainty, u.xPositiveUncertainty,
                      u.yNegativeUncertainty, u.yPositiveUncertainty,
                      u.zNegativeUncertainty, u.zPositiveUncertainty))
              .append('\n');
        }

        // ─── Prediction ────────────────────────────────────────────────────────────
        sb.append("  ─ PREDICTION ").append(SEC_FILL).append('\n');

        sb.append("  actual    ")
          .append(String.format("dx=%+.7f  dy=%+.7f  dz=%+.7f", actual.getX(), actual.getY(), actual.getZ()))
          .append('\n');

        sb.append("  predicted ")
          .append(String.format("dx=%+.7f  dy=%+.7f  dz=%+.7f", pred.vector.getX(), pred.vector.getY(), pred.vector.getZ()))
          .append("  [").append(pred.vectorType).append(']')
          .append('\n');

        double dX = actual.getX() - pred.vector.getX();
        double dY = actual.getY() - pred.vector.getY();
        double dZ = actual.getZ() - pred.vector.getZ();
        sb.append("  offset    ")
          .append(String.format("dx=%+.3E  dy=%+.3E  dz=%+.3E  |total|=%.4E",
                  dX, dY, dZ, offset))
          .append('\n');

        if (!checked) {
            sb.append("  SKIP  (player exempt from prediction: fly/spectator/glide)").append('\n');
        } else if (flaggedBySim) {
            sb.append("  !! SIM-FLAG  ").append(simVerbose).append('\n');
        } else {
            sb.append("  PASS").append('\n');
        }

        // ─── Buffered flags from other checks ──────────────────────────────────────
        if (pendingFlags.length() > 0) {
            sb.append(pendingFlags);
            pendingFlags.setLength(0);
        }

        sb.append('\n'); // blank line between ticks

        offer(sb.toString());
    }

    /**
     * Called from Check.flag() for every check, on the prediction thread.
     * Buffered until the next recordTick() flush.
     */
    public void recordFlag(String checkName, double oldVl, double newVl, String verbose) {
        pendingFlags.append("  >> FLAG:")
                    .append(checkName)
                    .append("  vl=").append(String.format("%.1f", oldVl))
                    .append("->").append(String.format("%.1f", newVl));
        if (!verbose.isEmpty()) {
            pendingFlags.append("  ").append(verbose);
        }
        pendingFlags.append('\n');
    }

    /**
     * Flushes pending flags and closes the writer thread.
     * Call on player disconnect.
     */
    public void close() {
        if (pendingFlags.length() > 0) {
            offer("  (disconnect) " + pendingFlags + "\n");
            pendingFlags.setLength(0);
        }
        offer(POISON_PILL);
    }

    private void offer(String s) {
        if (!queue.offer(s)) {
            // Queue full: drop oldest to make room, never block the prediction thread
            queue.poll();
            queue.offer(s);
        }
    }

    private static char flag(boolean b) {
        return b ? 'Y' : 'N';
    }

    private static String effectLevel(VoidPlayer player, PotionType effect) {
        OptionalInt level = player.compensatedEntities.self.getPotionEffectLevel(effect);
        return level.isPresent() ? String.valueOf(level.getAsInt()) : "-";
    }
}
