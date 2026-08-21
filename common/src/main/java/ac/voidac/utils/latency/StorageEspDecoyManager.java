package ac.voidac.utils.latency;

import ac.voidac.VoidAPI;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.utils.collisions.CollisionData;
import ac.voidac.utils.collisions.datatypes.CollisionBox;
import ac.voidac.platform.api.player.PlatformPlayer;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.nmsutil.Materials;
import ac.voidac.utils.math.VoidMath;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StorageEspDecoyManager {
    private static final String PERMISSION = "void.storageesp";
    private static final int MAX_OCCLUDING_COUNT = 2;
    private static final int ALWAYS_SHOW_RADIUS = 8;
    private static final int RAYCAST_RADIUS = 80;
    private static final int HIDE_ON_SPAWN_DISTANCE = 32;
    private static final int VISIBLE_RECHECK_INTERVAL_TICKS = 5;

    private final VoidPlayer player;
    private final long sessionSeed;
    private volatile StateType[] decoyStates;
    private final ConcurrentHashMap<Long, List<DecoyPlacement>> decoyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<DecoyPlacement>> activeDecoyCache = new ConcurrentHashMap<>();
    private final Object stateLock = new Object();

    private volatile boolean active;
    private volatile boolean configEnabled;
    private volatile boolean permissionEnabled;
    private volatile boolean includeClientSideBlocks;
    private volatile int autoFlagThreshold = 16;
    private volatile int decoysPerChunk = 1;
    private volatile int lastVisibilityTick = Integer.MIN_VALUE;

    public StorageEspDecoyManager(VoidPlayer player) {
        this.player = player;
        this.sessionSeed = mixSeed(player.uuid.getMostSignificantBits(), player.uuid.getLeastSignificantBits(), player.joinTime);
        this.decoyStates = new StateType[0];
    }

    public void reload(ConfigManager config) {
        boolean wasActive = isActive();

        if (wasActive) {
            restoreAllDecoys();
        }

        synchronized (stateLock) {
            configEnabled           = config.getBooleanElse("storage-esp-decoys.enabled", false);
            includeClientSideBlocks = config.getBooleanElse("storage-esp-decoys.include-client-side-blocks", false);
            autoFlagThreshold       = config.getIntElse("storage-esp-decoys.auto-flag-threshold", 16);
            decoysPerChunk          = config.getIntElse("storage-esp-decoys.decoys-per-chunk", 1);
            decoyStates             = configEnabled ? loadDecoyStates(includeClientSideBlocks) : new StateType[0];
            decoyCache.clear();
            activeDecoyCache.clear();
            lastVisibilityTick = Integer.MIN_VALUE;
            active = false;
        }

        refreshActiveState();
    }

    public void syncFromPlatformPlayer(PlatformPlayer platformPlayer) {
        permissionEnabled = platformPlayer != null && platformPlayer.hasPermission(PERMISSION);
        refreshActiveState();
    }

    public void syncFromCurrentPermissions() {
        PlatformPlayer platformPlayer = player.platformPlayer;
        permissionEnabled = platformPlayer != null && platformPlayer.hasPermission(PERMISSION);
        refreshActiveState();
    }

    public void tickVisibility() {
        if (!isActive() || decoysPerChunk <= 0 || decoyStates.length == 0) {
            return;
        }

        int currentTick = VoidAPI.INSTANCE.getTickManager().currentTick;
        if (lastVisibilityTick != Integer.MIN_VALUE && currentTick - lastVisibilityTick < VISIBLE_RECHECK_INTERVAL_TICKS) {
            return;
        }

        lastVisibilityTick = currentTick;
        refreshVisibleDecoys();
    }

    public void forceVisibilityRefresh() {
        if (!isActive() || decoysPerChunk <= 0 || decoyStates.length == 0) {
            return;
        }

        lastVisibilityTick = VoidAPI.INSTANCE.getTickManager().currentTick;
        refreshVisibleDecoys();
    }

    public void clearVisibilityCache() {
        activeDecoyCache.clear();
        lastVisibilityTick = Integer.MIN_VALUE;
    }

    public List<Vector3i> getDecoyPositionsForChunk(int chunkX, int chunkZ) {
        if (!isActive() || decoysPerChunk <= 0 || decoyStates.length == 0) {
            return List.of();
        }

        tickVisibility();

        List<Vector3i> positions = new ObjectArrayList<>();
        for (DecoyPlacement placement : getVisibleDecoysForChunk(chunkX, chunkZ)) {
            positions.add(placement.position());
        }
        return List.copyOf(positions);
    }

    public int getDecoyBlockId(int x, int y, int z) {
        if (!isActive() || decoysPerChunk <= 0 || decoyStates.length == 0) {
            return -1;
        }

        tickVisibility();

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        for (DecoyPlacement placement : getVisibleDecoysForChunk(chunkX, chunkZ)) {
            Vector3i position = placement.position();
            if (position.getX() == x && position.getY() == y && position.getZ() == z) {
                return placement.blockId();
            }
        }

        return -1;
    }

    public void onFlag() {
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDecoyPosition(int x, int y, int z) {
        if (!isActive()) return false;

        tickVisibility();

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        for (DecoyPlacement placement : getVisibleDecoysForChunk(chunkX, chunkZ)) {
            Vector3i position = placement.position();
            if (position.getX() == x && position.getY() == y && position.getZ() == z) {
                return true;
            }
        }

        return false;
    }

    public void applyChunkDecoys(int chunkX, int chunkZ) {
        if (!isActive()) return;

        player.runSafely(() -> {
            if (!isActive()) return;
            for (DecoyPlacement placement : getVisibleDecoysForChunk(chunkX, chunkZ)) {
                sendPlacement(placement.position(), placement.blockId());
            }
        });
    }

    public void restoreChunkDecoys(int chunkX, int chunkZ) {
        if (isActive()) return;

        player.runSafely(() -> {
            if (isActive()) return;
            for (DecoyPlacement placement : getDecoysForChunk(chunkX, chunkZ)) {
                int realBlockId = player.compensatedWorld.getBlock(placement.position()).getGlobalId();
                sendPlacement(placement.position(), realBlockId);
            }
        });
    }

    public void reapplyBlock(int x, int y, int z) {
        if (!isActive()) return;

        player.runSafely(() -> {
            if (!isActive()) return;
            sendDecoyIfPresent(x, y, z);
        });
    }

    public void reapplyBlocks(List<Vector3i> positions) {
        if (!isActive() || positions.isEmpty()) return;

        player.runSafely(() -> {
            if (!isActive()) return;
            for (Vector3i position : positions) {
                sendDecoyIfPresent(position.getX(), position.getY(), position.getZ());
            }
        });
    }

    public void shutdown() {
        synchronized (stateLock) {
            active = false;
            configEnabled = false;
            permissionEnabled = false;
        }
        activeDecoyCache.clear();
        lastVisibilityTick = Integer.MIN_VALUE;
    }

    public void restoreAllDecoys() {
        player.runSafely(() -> {
            for (long chunkKey : player.compensatedWorld.chunks.keySet()) {
                int chunkX = (int) (chunkKey >> 32);
                int chunkZ = (int) chunkKey;
                for (DecoyPlacement placement : getDecoysForChunk(chunkX, chunkZ)) {
                    int realBlockId = player.compensatedWorld.getBlock(placement.position()).getGlobalId();
                    sendPlacement(placement.position(), realBlockId);
                }
            }
        });
    }

    private void refreshActiveState() {
        boolean desired = isDesiredActive();
        boolean wasActive;
        synchronized (stateLock) {
            wasActive = active;
            if (wasActive == desired) {
                return;
            }
            active = desired;
        }

        if (desired) {
            tickVisibility();
        } else if (wasActive) {
            player.runSafely(() -> {
                if (isActive()) return;
                for (long chunkKey : player.compensatedWorld.chunks.keySet()) {
                    restoreChunkDecoys((int) (chunkKey >> 32), (int) chunkKey);
                }
            });
        }
    }

    private boolean isDesiredActive() {
        // Active when the owner has enabled it in config AND the player
        // doesn't hold the void.storageesp bypass permission.
        return configEnabled && !permissionEnabled;
    }

    public void onChunkUnload(int chunkX, int chunkZ) {
        long chunkKey = CompensatedWorld.chunkPositionToLong(chunkX, chunkZ);
        activeDecoyCache.remove(chunkKey);
    }

    private List<DecoyPlacement> getDecoysForChunk(int chunkX, int chunkZ) {
        if (decoysPerChunk <= 0 || decoyStates.length == 0) {
            return List.of();
        }

        long chunkKey = CompensatedWorld.chunkPositionToLong(chunkX, chunkZ);
        return decoyCache.computeIfAbsent(chunkKey, ignored -> buildDecoys(chunkX, chunkZ));
    }

    private List<DecoyPlacement> getVisibleDecoysForChunk(int chunkX, int chunkZ) {
        if (decoysPerChunk <= 0 || decoyStates.length == 0) {
            return List.of();
        }

        long chunkKey = CompensatedWorld.chunkPositionToLong(chunkX, chunkZ);
        List<DecoyPlacement> visible = activeDecoyCache.get(chunkKey);
        if (visible != null) {
            return visible;
        }

        visible = computeVisibleDecoysForChunk(chunkX, chunkZ);
        if (visible.isEmpty()) {
            activeDecoyCache.remove(chunkKey);
        } else {
            activeDecoyCache.put(chunkKey, visible);
        }

        return visible;
    }

    private void refreshVisibleDecoys() {
        Set<Long> loadedChunks = new HashSet<>(player.compensatedWorld.chunks.keySet());
        activeDecoyCache.keySet().removeIf(chunkKey -> !loadedChunks.contains(chunkKey));

        for (long chunkKey : loadedChunks) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;

            List<DecoyPlacement> allDecoys = getDecoysForChunk(chunkX, chunkZ);
            if (allDecoys.isEmpty()) {
                activeDecoyCache.remove(chunkKey);
                continue;
            }

            List<DecoyPlacement> previousVisible = activeDecoyCache.get(chunkKey);
            List<DecoyPlacement> nextVisible = computeVisibleDecoysForChunk(chunkX, chunkZ);

            for (DecoyPlacement placement : allDecoys) {
                boolean shouldShow = containsPlacement(nextVisible, placement.position());
                boolean wasVisible = containsPlacement(previousVisible, placement.position());
                if (shouldShow != wasVisible) {
                    int blockId = shouldShow ? placement.blockId() : player.compensatedWorld.getBlock(placement.position()).getGlobalId();
                    sendPlacement(placement.position(), blockId);
                }
            }

            if (nextVisible.isEmpty()) {
                activeDecoyCache.remove(chunkKey);
            } else {
                activeDecoyCache.put(chunkKey, List.copyOf(nextVisible));
            }
        }
    }

    private boolean shouldShowDecoy(Vector3i position) {
        int px = position.getX();
        int py = position.getY();
        int pz = position.getZ();

        double eyeX = player.x;
        double eyeY = player.y + player.getEyeHeight();
        double eyeZ = player.z;

        double targetX = px + 0.5D;
        double targetY = py + 0.5D;
        double targetZ = pz + 0.5D;

        double distanceSquared = VoidMath.square(targetX - eyeX) + VoidMath.square(targetY - eyeY) + VoidMath.square(targetZ - eyeZ);

        // Too close: the player can physically reach and see this position.
        // A legitimate player would notice the block is fake when they try to open it.
        // Hide the decoy so only ESP users (looking through walls at range) are deceived.
        if (distanceSquared <= VoidMath.square(ALWAYS_SHOW_RADIUS)) {
            return false;
        }

        // Beyond raycast range, not worth the packet overhead.
        if (distanceSquared > VoidMath.square(RAYCAST_RADIUS)) {
            return false;
        }

        // Re-verify the decoy is still fully buried *right now*, against the live world.
        // buildDecoys() runs this check once and caches the result forever, so a decoy
        // can end up exposed when:
        //   - the initial build ran while neighbouring chunks were not yet loaded
        //     (border faces read as air and were mis-judged), or
        //   - the world changed afterwards (a player mined toward it, or mined the
        //     decoy block itself), leaving a fake shulker floating in the open.
        // A decoy only deceives ESP, never a legit player, when its own block is
        // solid/opaque AND all six faces are backed by real solid/opaque blocks.
        // Anything less is visible by line-of-sight, so we hide it (show the real block).
        if (!isSolidAndOpaque(px, py, pz) || !isSurroundedBySolidOpaqueBlocks(px, py, pz)) {
            return false;
        }

        // Only show when the decoy is behind enough solid blocks that a legitimate
        // player cannot see it with line-of-sight, but an ESP user would reveal it.
        return countOccludingBlocks(eyeX, eyeY, eyeZ, targetX, targetY, targetZ, position) > MAX_OCCLUDING_COUNT;
    }

    private List<DecoyPlacement> computeVisibleDecoysForChunk(int chunkX, int chunkZ) {
        List<DecoyPlacement> allDecoys = getDecoysForChunk(chunkX, chunkZ);
        if (allDecoys.isEmpty()) {
            return List.of();
        }

        List<DecoyPlacement> visible = new ObjectArrayList<>(allDecoys.size());
        for (DecoyPlacement placement : allDecoys) {
            if (shouldShowDecoy(placement.position())) {
                visible.add(placement);
            }
        }

        return List.copyOf(visible);
    }

    private int countOccludingBlocks(double startX, double startY, double startZ, double endX, double endY, double endZ, Vector3i targetPosition) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double deltaZ = endZ - startZ;

        if (deltaX == 0.0D && deltaY == 0.0D && deltaZ == 0.0D) {
            return 0;
        }

        int currentX = VoidMath.floor(startX);
        int currentY = VoidMath.floor(startY);
        int currentZ = VoidMath.floor(startZ);
        int targetX = targetPosition.getX();
        int targetY = targetPosition.getY();
        int targetZ = targetPosition.getZ();

        int stepX = deltaX > 0.0D ? 1 : deltaX < 0.0D ? -1 : 0;
        int stepY = deltaY > 0.0D ? 1 : deltaY < 0.0D ? -1 : 0;
        int stepZ = deltaZ > 0.0D ? 1 : deltaZ < 0.0D ? -1 : 0;

        double nextBoundaryX = stepX > 0 ? currentX + 1.0D : currentX;
        double nextBoundaryY = stepY > 0 ? currentY + 1.0D : currentY;
        double nextBoundaryZ = stepZ > 0 ? currentZ + 1.0D : currentZ;

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(deltaX);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(deltaY);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(deltaZ);

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs((nextBoundaryX - startX) / deltaX);
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs((nextBoundaryY - startY) / deltaY);
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs((nextBoundaryZ - startZ) / deltaZ);

        int occludingCount = 0;
        while (occludingCount <= MAX_OCCLUDING_COUNT && (currentX != targetX || currentY != targetY || currentZ != targetZ)) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    currentX += stepX;
                    tMaxX += tDeltaX;
                } else {
                    currentZ += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                currentY += stepY;
                tMaxY += tDeltaY;
            } else {
                currentZ += stepZ;
                tMaxZ += tDeltaZ;
            }

            if (currentX == targetX && currentY == targetY && currentZ == targetZ) {
                break;
            }

            if (isOccludingBlock(currentX, currentY, currentZ)) {
                occludingCount++;
            }
        }

        return occludingCount;
    }

    private boolean isOccludingBlock(int x, int y, int z) {
        WrappedBlockState block = player.compensatedWorld.getBlock(x, y, z);
        if (block == null || block.getType().isAir()) {
            return false;
        }

        CollisionBox box = CollisionData.getData(block.getType()).getMovementCollisionBox(player, player.getClientVersion(), block, x, y, z);
        return box != null && box.isFullBlock();
    }

    private boolean isSolidAndOpaque(int x, int y, int z) {
        WrappedBlockState block = player.compensatedWorld.getBlock(x, y, z);
        if (block == null || block.getType().isAir()) return false;
        StateType type = block.getType();
        if (Materials.isGlassBlock(type) || Materials.isGlassPane(type)) return false;
        if (type == StateTypes.WATER || type == StateTypes.LAVA) return false;
        CollisionBox box = CollisionData.getData(type).getMovementCollisionBox(player, player.getClientVersion(), block, x, y, z);
        return box != null && box.isFullBlock();
    }

    private boolean isSurroundedBySolidOpaqueBlocks(int x, int y, int z) {
        return isSolidAndOpaque(x + 1, y, z)
            && isSolidAndOpaque(x - 1, y, z)
            && isSolidAndOpaque(x, y + 1, z)
            && isSolidAndOpaque(x, y - 1, z)
            && isSolidAndOpaque(x, y, z + 1)
            && isSolidAndOpaque(x, y, z - 1);
    }

    private boolean containsPlacement(List<DecoyPlacement> placements, Vector3i position) {
        if (placements == null || placements.isEmpty()) {
            return false;
        }

        for (DecoyPlacement placement : placements) {
            Vector3i other = placement.position();
            if (other.getX() == position.getX() && other.getY() == position.getY() && other.getZ() == position.getZ()) {
                return true;
            }
        }

        return false;
    }

    private List<DecoyPlacement> buildDecoys(int chunkX, int chunkZ) {
        if (decoysPerChunk <= 0 || decoyStates.length == 0) {
            return List.of();
        }

        int count = decoysPerChunk;
        Random random = new Random(mixSeed(sessionSeed, chunkX, chunkZ));
        int minY = player.compensatedWorld.getMinHeight();
        int maxY = player.compensatedWorld.getMaxHeight() - 1;
        int lowerY = minY + 4;
        int upperY = Math.max(lowerY + 1, maxY - 4);
        int ySpan = upperY - lowerY + 1;

        // Stratified placement: divide the full world height into bands equal to
        // decoysPerChunk. One decoy is placed in each band, guaranteeing coverage
        // at every elevation so an ESP user is never out of decoy range regardless
        // of the player's current Y.
        int bandHeight = Math.max(1, ySpan / count);

        Set<Long> usedPositions = new HashSet<>();
        List<DecoyPlacement> placements = new ObjectArrayList<>(count);

        for (int band = 0; band < count; band++) {
            int bandLower = lowerY + band * bandHeight;
            int bandUpper = (band == count - 1) ? upperY : Math.min(upperY, bandLower + bandHeight - 1);
            int localSpan = Math.max(1, bandUpper - bandLower + 1);

            for (int attempt = 0; attempt < 8; attempt++) {
                int x = (chunkX << 4) + random.nextInt(16);
                int y = bandLower + random.nextInt(localSpan);
                int z = (chunkZ << 4) + random.nextInt(16);
                Vector3i position = new Vector3i(x, y, z);

                if (!usedPositions.add(position.getSerializedPosition())) {
                    continue;
                }

                if (!isSolidAndOpaque(x, y, z) || !isSurroundedBySolidOpaqueBlocks(x, y, z)) {
                    continue;
                }

                StateType type = decoyStates[random.nextInt(decoyStates.length)];
                int blockId = type.createBlockState(CompensatedWorld.blockVersion).getGlobalId();
                placements.add(new DecoyPlacement(position, blockId));
                break;
            }
        }

        return List.copyOf(placements);
    }

    private void sendLoadedChunkDecoys(int chunkX, int chunkZ) {
        for (DecoyPlacement placement : getVisibleDecoysForChunk(chunkX, chunkZ)) {
            sendPlacement(placement.position(), placement.blockId());
        }
    }

    private void resendLoadedChunkDecoys() {
        player.runSafely(() -> {
            if (!isActive()) return;
            for (long chunkKey : player.compensatedWorld.chunks.keySet()) {
                sendLoadedChunkDecoys((int) (chunkKey >> 32), (int) chunkKey);
            }
        });
    }

    private void sendDecoyIfPresent(int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        for (DecoyPlacement placement : getVisibleDecoysForChunk(chunkX, chunkZ)) {
            Vector3i position = placement.position();
            if (position.getX() == x && position.getY() == y && position.getZ() == z) {
                sendPlacement(position, placement.blockId());
                return;
            }
        }
    }

    private void sendPlacement(Vector3i position, int blockId) {
        try {
            PacketEvents.getAPI().getProtocolManager().sendPacketSilently(player.user.getChannel(), new WrapperPlayServerBlockChange(position, blockId));
        } catch (Exception ignored) {
        }
    }

    private StateType[] loadDecoyStates(boolean includeClientSideBlocks) {
        Set<StateType> decoys = Materials.getAntiEspBlocks(includeClientSideBlocks);
        if (decoys.isEmpty()) {
            return new StateType[]{StateTypes.CHEST};
        }

        return decoys.toArray(new StateType[0]);
    }

    private static long mixSeed(long seed, long chunkX, long chunkZ) {
        long value = seed ^ ((long) chunkX * 0x9E3779B97F4A7C15L) ^ ((long) chunkZ * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private record DecoyPlacement(Vector3i position, int blockId) {
    }
}
