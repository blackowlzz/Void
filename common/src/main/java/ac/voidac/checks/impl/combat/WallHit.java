package ac.voidac.checks.impl.combat;

import ac.voidac.api.config.ConfigManager;
import ac.voidac.checks.Check;
import ac.voidac.checks.CheckData;
import ac.voidac.checks.type.PacketCheck;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.collisions.HitboxData;
import ac.voidac.utils.collisions.datatypes.ComplexCollisionBox;
import ac.voidac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.voidac.utils.data.HitData;
import ac.voidac.utils.math.Vector3dm;
import ac.voidac.utils.nmsutil.ReachUtils;
import ac.voidac.utils.nmsutil.WorldRayTrace;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.util.Vector3d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;

@CheckData(name = "WallHit", configName = "WallHit", stableKey = "void.combat.wallhit", setback = 20)
public class WallHit extends Check implements PacketCheck {
    private final SimpleCollisionBox[] boxes = new SimpleCollisionBox[ComplexCollisionBox.DEFAULT_MAX_COLLISION_BOX_SIZE];
    private LongOpenHashSet changedBlocks = new LongOpenHashSet();
    private LongOpenHashSet previousChanges = new LongOpenHashSet();
    private int overflowTicks;
    private boolean blockImpossibleHits;
    private int confirmedEntity = -1;
    private int confirmationTicks;
    private long confirmationDeadline;

    public WallHit(VoidPlayer player) {
        super(player);
    }

    @Override
    public void onReload(ConfigManager config) {
        blockImpossibleHits = config.getBooleanElse("WallHit.block-impossible-hits",
                config.getBooleanElse("Reach.block-impossible-hits", true));
    }

    void advanceTick() {
        LongOpenHashSet expired = previousChanges;
        previousChanges = changedBlocks;
        changedBlocks = expired;
        changedBlocks.clear();
        if (overflowTicks > 0) overflowTicks--;
        if (confirmationTicks > 0) confirmationTicks--;
    }

    void confirmObstruction(int entityId) {
        confirmedEntity = entityId;
        confirmationTicks = 40;
        confirmationDeadline = System.nanoTime() + 2_000_000_000L;
    }

    void clearConfirmation(int entityId) {
        if (confirmedEntity == entityId) confirmationTicks = 0;
    }

    boolean canCancelConfirmedAttack(int entityId) {
        return blockImpossibleHits && shouldModifyPackets() && canCheck()
                && confirmedEntity == entityId && confirmationTicks > 0
                && System.nanoTime() - confirmationDeadline < 0;
    }

    public void handleBlockChange(int x, int y, int z) {
        if (!isEnabled()) return;
        if (Math.abs(x + 0.5 - player.x) > 8 || Math.abs(y + 0.5 - player.y) > 10
                || Math.abs(z + 0.5 - player.z) > 8) return;
        if (changedBlocks.size() >= 512) {
            overflowTicks = 2;
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    changedBlocks.add(blockKey(x + dx, y + dy, z + dz));
                }
            }
        }
    }

    boolean canCheck() {
        return isEnabled() && !player.disableVoid && overflowTicks == 0
                && !player.compensatedWorld.isNearHardEntity(player.boundingBox.copy().expand(6));
    }

    @Nullable HitData findObstruction(Vector3dm eyes, Vector3dm end) {
        double distanceSquared = eyes.distanceSquared(end);
        if (distanceSquared < 1.0E-8 || distanceSquared > 36) return null;
        double blockRange = player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        if (blockRange <= 0) return null;
        Vector3dm rayEnd = distanceSquared > blockRange * blockRange
                ? eyes.clone().add(end.clone().subtract(eyes).multiply(blockRange / Math.sqrt(distanceSquared))) : end;
        double margin = player.getMovementThreshold();
        HitData hit = WorldRayTrace.traverseBlocks(player,
                new Vector3d(eyes.getX(), eyes.getY(), eyes.getZ()),
                new Vector3d(rayEnd.getX(), rayEnd.getY(), rayEnd.getZ()), (state, position) -> {
                    if (recentlyChanged(position.x, position.y, position.z)) return null;
                    int count = HitboxData.getBlockHitbox(player, null, player.getClientVersion(), state,
                            false, position.x, position.y, position.z).downCast(boxes);
                    HitData closest = null;
                    double closestDistance = distanceSquared - 1.0E-7;
                    for (int i = 0; i < count; i++) {
                        SimpleCollisionBox box = boxes[i];
                        if (box.isFullBlock()) {
                            box = mergeWallBlocks(box, position.x, position.y, position.z, eyes, rayEnd);
                        }
                        if (ReachUtils.isVecInside(box, eyes)) {
                            return new HitData(position, eyes.clone(), null, state);
                        }
                        box.expand(-margin);
                        if (box.minX >= box.maxX || box.minY >= box.maxY || box.minZ >= box.maxZ) continue;
                        var intercept = ReachUtils.calculateIntercept(box, eyes, rayEnd);
                        if (intercept.first() == null) continue;
                        double distance = eyes.distanceSquared(intercept.first());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closest = new HitData(position, intercept.first(), intercept.second(), state);
                        }
                    }
                    return closest;
                });
        return hit != null && eyes.distanceSquared(hit.blockHitLocation()) > 1.0E-8 ? hit : null;
    }

    boolean shouldCancel(SimpleCollisionBox target, double x, double y, double z) {
        return cancellationDecision(target, x, y, z) == CancellationDecision.BLOCK;
    }

    CancellationDecision cancellationDecision(SimpleCollisionBox target, double x, double y, double z) {
        if (!blockImpossibleHits) return CancellationDecision.CONFIG_DISABLED;
        if (!shouldModifyPackets()) return CancellationDecision.PACKET_MODIFICATION_DISABLED;
        if (!canCheck()) return CancellationDecision.UNCERTAIN_WORLD;
        Vector3dm center = new Vector3dm((target.minX + target.maxX) * 0.5,
                (target.minY + target.maxY) * 0.5, (target.minZ + target.maxZ) * 0.5);
        for (double height : player.getPossibleEyeHeights()) {
            Vector3dm eyes = new Vector3dm(x, y + height, z);
            if (ReachUtils.isVecInside(target, eyes)) return CancellationDecision.INSIDE_TARGET;
            HitData hit = findObstruction(eyes, center);
            if (hit == null) return CancellationDecision.NO_OBSTRUCTION;
            var position = hit.position();
            int count = HitboxData.getBlockHitbox(player, null, player.getClientVersion(), hit.state(),
                    false, position.x, position.y, position.z).downCast(boxes);
            boolean covered = false;
            for (int i = 0; i < count; i++) {
                SimpleCollisionBox box = boxes[i];
                if (box.isFullBlock()) {
                    box = mergeWallBlocks(box, position.x, position.y, position.z, eyes, center);
                }
                box.expand(-player.getMovementThreshold());
                if (box.minX >= box.maxX || box.minY >= box.maxY || box.minZ >= box.maxZ) continue;
                if (coversTarget(box, eyes, target,
                        player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE))) {
                    covered = true;
                    break;
                }
            }
            if (!covered) return CancellationDecision.INCOMPLETE_COVERAGE;
        }
        return CancellationDecision.BLOCK;
    }

    enum CancellationDecision {
        NOT_CHECKED, CONFIG_DISABLED, PACKET_MODIFICATION_DISABLED, UNCERTAIN_WORLD,
        INSIDE_TARGET, NO_OBSTRUCTION, INCOMPLETE_COVERAGE, BLOCK, CONFIRMED_RAY
    }

    private SimpleCollisionBox mergeWallBlocks(SimpleCollisionBox box, int x, int y, int z,
                                               Vector3dm eyes, Vector3dm end) {
        double dx = Math.abs(end.getX() - eyes.getX());
        double dy = Math.abs(end.getY() - eyes.getY());
        double dz = Math.abs(end.getZ() - eyes.getZ());
        boolean horizontal = dy > dx && dy > dz;
        int firstX = horizontal ? 1 : 0;
        int firstY = horizontal ? 0 : 1;
        int secondX = !horizontal && dz >= dx ? 1 : 0;
        int secondZ = 1 - secondX;
        int firstMin = 0;
        int firstMax = 0;
        while (firstMin > -4 && isStableFullBlock(x + (firstMin - 1) * firstX,
                y + (firstMin - 1) * firstY, z)) firstMin--;
        while (firstMax < 4 && isStableFullBlock(x + (firstMax + 1) * firstX,
                y + (firstMax + 1) * firstY, z)) firstMax++;
        int secondMin = 0;
        int secondMax = 0;
        while (secondMin > -4 && isStableStrip(x + (secondMin - 1) * secondX, y,
                z + (secondMin - 1) * secondZ, firstX, firstY, firstMin, firstMax)) secondMin--;
        while (secondMax < 4 && isStableStrip(x + (secondMax + 1) * secondX, y,
                z + (secondMax + 1) * secondZ, firstX, firstY, firstMin, firstMax)) secondMax++;
        return new SimpleCollisionBox(box.minX + firstMin * firstX + secondMin * secondX,
                box.minY + firstMin * firstY, box.minZ + secondMin * secondZ,
                box.maxX + firstMax * firstX + secondMax * secondX,
                box.maxY + firstMax * firstY, box.maxZ + secondMax * secondZ);
    }

    private boolean isStableStrip(int x, int y, int z, int dx, int dy, int min, int max) {
        for (int offset = min; offset <= max; offset++) {
            if (!isStableFullBlock(x + offset * dx, y + offset * dy, z)) return false;
        }
        return true;
    }

    private boolean isStableFullBlock(int x, int y, int z) {
        return !recentlyChanged(x, y, z) && HitboxData.getBlockHitbox(player, null, player.getClientVersion(),
                player.compensatedWorld.getBlock(x, y, z), false, x, y, z).isFullBlock();
    }

    private boolean recentlyChanged(int x, int y, int z) {
        long key = blockKey(x, y, z);
        return changedBlocks.contains(key) || previousChanges.contains(key);
    }

    static boolean coversTarget(SimpleCollisionBox obstruction, Vector3dm eyes, SimpleCollisionBox target) {
        return coversTarget(obstruction, eyes, target, Double.POSITIVE_INFINITY);
    }

    private static boolean coversTarget(SimpleCollisionBox obstruction, Vector3dm eyes, SimpleCollisionBox target, double blockRange) {
        if (obstruction.isIntersected(target) || ReachUtils.isVecInside(obstruction, eyes)) return false;
        for (int corner = 0; corner < 8; corner++) {
            Vector3dm end = new Vector3dm((corner & 1) == 0 ? target.minX : target.maxX,
                    (corner & 2) == 0 ? target.minY : target.maxY,
                    (corner & 4) == 0 ? target.minZ : target.maxZ);
            var intercept = ReachUtils.calculateIntercept(obstruction, eyes, end).first();
            if (intercept == null || eyes.distanceSquared(intercept) >= blockRange * blockRange) return false;
        }
        return true;
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }
}
