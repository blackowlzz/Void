package ac.voidac.utils.latency;

import ac.voidac.VoidAPI;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.collisions.CollisionData;
import ac.voidac.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AntiEspManager {
    private final VoidPlayer player;
    private final AntiEspTerrain terrain;
    private volatile boolean enabled;
    private volatile boolean terrainMasking;
    private volatile boolean entityVisibility;
    private volatile boolean blockEntityVisibility;
    private volatile int thresholdY;
    private volatile int replacementBlockId;
    private volatile int undergroundRevealRadius;
    private volatile int proximityRadius;
    private volatile int lineOfSightDistance;
    private volatile int entityRecheckIntervalTicks;
    private volatile int chunkRefreshCooldownTicks;
    private int lastEntityTick = Integer.MIN_VALUE;
    private final Int2ObjectOpenHashMap<EntitySpawn> entitySpawns = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<List<EntityData<?>>> metadata = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<List<Equipment>> equipment = new Int2ObjectOpenHashMap<>();
    private final IntOpenHashSet hiddenEntities = new IntOpenHashSet();

    public AntiEspManager(VoidPlayer player) {
        this.player = player;
        this.terrain = new AntiEspTerrain(player);
    }

    public void reload(ConfigManager config) {
        enabled = config.getBooleanElse("anti-esp.enabled", false);
        terrainMasking = config.getBooleanElse("anti-esp.terrain-masking", true);
        entityVisibility = config.getBooleanElse("anti-esp.entity-visibility", true);
        blockEntityVisibility = config.getBooleanElse("anti-esp.block-entity-visibility", true);
        thresholdY = config.getIntElse("anti-esp.hide-below-y", 0);
        String replacement = config.getStringElse("anti-esp.replacement-block", "air");
        boolean supportsDeepslate = CompensatedWorld.blockVersion.isNewerThanOrEquals(ClientVersion.V_1_17);
        replacementBlockId = ("air".equalsIgnoreCase(replacement) ? StateTypes.AIR
                : supportsDeepslate && "deepslate".equalsIgnoreCase(replacement) ? StateTypes.DEEPSLATE : StateTypes.STONE)
                .createBlockState(CompensatedWorld.blockVersion).getGlobalId();
        undergroundRevealRadius = clamp(config.getIntElse("anti-esp.underground-reveal-radius", 2), 1, 3);
        proximityRadius = clamp(config.getIntElse("anti-esp.proximity-radius", 16), 1, 64);
        lineOfSightDistance = clamp(config.getIntElse("anti-esp.line-of-sight-distance", 96), proximityRadius, 96);
        entityRecheckIntervalTicks = clamp(config.getIntElse("anti-esp.entity-recheck-interval-ticks", 2), 1, 20);
        chunkRefreshCooldownTicks = clamp(config.getIntElse("anti-esp.chunk-refresh-cooldown-ticks", 4), 1, 20);
        lastEntityTick = Integer.MIN_VALUE;
    }

    public void tick() {
        tick(player.x, player.y, player.z);
    }

    public void tick(double x, double y, double z) {
        configureTerrain();
        terrain.tick(x, y, z, VoidAPI.INSTANCE.getTickManager().currentTick);
        if (!isEnabled()) {
            revealAllEntities();
            return;
        }
        updateEntitiesIfDue();
    }

    private void configureTerrain() {
        terrain.configure(shouldMaskTerrain(), thresholdY, replacementBlockId,
                undergroundRevealRadius, chunkRefreshCooldownTicks);
    }

    public boolean shouldMaskTerrain() {
        return isEnabled() && terrainMasking;
    }

    public boolean shouldHideBlockEntity(Vector3i position) {
        return shouldHideBlockEntity(position.getX(), position.getY(), position.getZ());
    }

    public boolean shouldHideBlockEntity(int x, int y, int z) {
        return blockEntityVisibility && shouldMaskBlock(x, y, z);
    }

    public boolean shouldMaskBlock(int x, int y, int z) {
        return shouldMaskTerrain() && terrain.shouldMask(x, y, z);
    }

    public boolean shouldRevealSection(int chunkX, int sectionY, int chunkZ) {
        return !shouldMaskTerrain() || terrain.isVisible(chunkX, sectionY, chunkZ);
    }

    public void cacheChunk(WrapperPlayServerChunkData packet) {
        configureTerrain();
        var column = packet.getColumn();
        terrain.cache(column.getX(), column.getZ(), column.getChunks(), column.isFullChunk(), column.getTileEntities());
    }

    public void cacheLegacyChunk(int chunkX, int chunkZ, BaseChunk[] chunks) {
        configureTerrain();
        terrain.cache(chunkX, chunkZ, chunks, true, new TileEntity[0]);
    }

    public void cacheBlock(int x, int y, int z, int blockId) {
        terrain.updateBlock(x, y, z, blockId);
    }

    public void cacheBlockEntity(WrapperPlayServerBlockEntityData packet) {
        terrain.updateBlockEntity(packet);
    }

    public void exposeTerrain(int x, int y, int z) {
        terrain.expose(x, y, z);
    }

    public void flushTerrainUpdates() {
        terrain.flush();
    }

    public void flushTerrainUpdatesAfter(PacketSendEvent event) {
        if (isEnabled()) {
            event.getTasksAfterSend().add(this::flushTerrainUpdates);
        }
    }

    public void unloadChunk(int chunkX, int chunkZ) {
        terrain.unload(chunkX, chunkZ);
    }

    public void clearWorldState() {
        terrain.clear();
        entitySpawns.clear();
        metadata.clear();
        equipment.clear();
        hiddenEntities.clear();
    }

    public void handleSpawn(PacketSendEvent event, WrapperPlayServerSpawnEntity packet) {
        if (!tracksEntities() || !EntityTypes.isTypeInstanceOf(packet.getEntityType(), EntityTypes.LIVINGENTITY)) {
            return;
        }

        EntitySpawn spawn = EntitySpawn.generic(
                packet.getEntityId(), packet.getUUID(), packet.getEntityType(), packet.getPosition(),
                packet.getPitch(), packet.getYaw(), packet.getHeadYaw(), packet.getData(), packet.getVelocity()
        );
        entitySpawns.put(spawn.entityId, spawn);
        updateSpawnVisibility(event, spawn);
    }

    public void handleSpawn(PacketSendEvent event, WrapperPlayServerSpawnLivingEntity packet) {
        if (!tracksEntities()) {
            return;
        }
        EntitySpawn spawn = EntitySpawn.living(
                packet.getEntityId(), packet.getEntityUUID(), packet.getEntityType(), packet.getPosition(),
                packet.getYaw(), packet.getPitch(), packet.getHeadPitch(), packet.getVelocity(), packet.getEntityMetadata()
        );
        entitySpawns.put(spawn.entityId, spawn);
        updateSpawnVisibility(event, spawn);
    }

    public void handleSpawn(PacketSendEvent event, WrapperPlayServerSpawnPlayer packet) {
        if (!tracksEntities()) {
            return;
        }
        EntitySpawn spawn = EntitySpawn.player(
                packet.getEntityId(), packet.getUUID(), packet.getPosition(),
                packet.getYaw(), packet.getPitch(), packet.getEntityMetadata()
        );
        entitySpawns.put(spawn.entityId, spawn);
        updateSpawnVisibility(event, spawn);
    }

    public void handleMove(PacketSendEvent event, int entityId, double x, double y, double z, Float yaw, Float pitch) {
        EntitySpawn spawn = entitySpawns.get(entityId);
        if (spawn == null) {
            return;
        }

        spawn.position = new Vector3d(x, y, z);
        if (yaw != null) {
            spawn.yaw = yaw;
        }
        if (pitch != null) {
            spawn.pitch = pitch;
        }
        updateMoveVisibility(event, spawn);
    }

    public void handleMetadata(PacketSendEvent event, WrapperPlayServerEntityMetadata packet) {
        if (!entitySpawns.containsKey(packet.getEntityId())) {
            return;
        }
        metadata.put(packet.getEntityId(), List.copyOf(packet.getEntityMetadata()));
        if (hiddenEntities.contains(packet.getEntityId())) {
            event.setCancelled(true);
        }
    }

    public void handleEquipment(PacketSendEvent event, WrapperPlayServerEntityEquipment packet) {
        if (!entitySpawns.containsKey(packet.getEntityId())) {
            return;
        }
        equipment.put(packet.getEntityId(), List.copyOf(packet.getEquipment()));
        if (hiddenEntities.contains(packet.getEntityId())) {
            event.setCancelled(true);
        }
    }

    public void handleDestroy(int[] entityIds) {
        for (int entityId : entityIds) {
            entitySpawns.remove(entityId);
            metadata.remove(entityId);
            equipment.remove(entityId);
            hiddenEntities.remove(entityId);
        }
    }

    public boolean isEnabled() {
        return enabled && !player.noModifyPacketPermission;
    }

    public boolean isTracking(int entityId) {
        return entitySpawns.containsKey(entityId);
    }

    private boolean tracksEntities() {
        return entityVisibility && isEnabled();
    }

    public int getThresholdY() {
        return thresholdY;
    }

    public int getReplacementBlockId() {
        return replacementBlockId;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void updateEntitiesIfDue() {
        int currentTick = VoidAPI.INSTANCE.getTickManager().currentTick;
        if (lastEntityTick == Integer.MIN_VALUE || currentTick - lastEntityTick >= entityRecheckIntervalTicks) {
            lastEntityTick = currentTick;
            updateEntityVisibility();
        }
    }

    private static boolean isVisionBlocking(StateType type) {
        var collision = CollisionData.getData(type);
        return type.isBlocking()
                && collision.box != null && collision.box.isFullBlock()
                && !Materials.isGlassBlock(type)
                && !Materials.isGlassPane(type)
                && !Materials.isLeaves(type)
                && type != StateTypes.WATER
                && type != StateTypes.LAVA
                && type != StateTypes.ICE
                && type != StateTypes.SLIME_BLOCK
                && type != StateTypes.HONEY_BLOCK;
    }

    private void updateSpawnVisibility(PacketSendEvent event, EntitySpawn spawn) {
        if (shouldHide(spawn)) {
            hiddenEntities.add(spawn.entityId);
            event.setCancelled(true);
        } else {
            hiddenEntities.remove(spawn.entityId);
        }
    }

    private void updateMoveVisibility(PacketSendEvent event, EntitySpawn spawn) {
        boolean hide = shouldHide(spawn);
        boolean hidden = hiddenEntities.contains(spawn.entityId);
        if (hide) {
            if (!hidden) {
                hiddenEntities.add(spawn.entityId);
                sendSilently(new WrapperPlayServerDestroyEntities(spawn.entityId));
            }
            event.setCancelled(true);
        } else if (hidden) {
            reveal(spawn);
            event.setCancelled(true);
        }
    }

    private void updateEntityVisibility() {
        for (EntitySpawn spawn : entitySpawns.values()) {
            boolean hide = shouldHide(spawn);
            boolean hidden = hiddenEntities.contains(spawn.entityId);
            if (hide && !hidden) {
                hiddenEntities.add(spawn.entityId);
                sendSilently(new WrapperPlayServerDestroyEntities(spawn.entityId));
            } else if (!hide && hidden) {
                reveal(spawn);
            }
        }
    }

    private void revealAllEntities() {
        if (hiddenEntities.isEmpty()) {
            return;
        }
        int[] entityIds = hiddenEntities.toIntArray();
        for (int entityId : entityIds) {
            EntitySpawn spawn = entitySpawns.get(entityId);
            if (spawn != null) {
                reveal(spawn);
            } else {
                hiddenEntities.remove(entityId);
            }
        }
    }

    private void reveal(EntitySpawn spawn) {
        hiddenEntities.remove(spawn.entityId);
        sendSilently(spawn.createPacket());
        List<EntityData<?>> entityMetadata = metadata.get(spawn.entityId);
        if (entityMetadata != null && !entityMetadata.isEmpty()) {
            sendSilently(new WrapperPlayServerEntityMetadata(spawn.entityId, entityMetadata));
        }
        List<Equipment> entityEquipment = equipment.get(spawn.entityId);
        if (entityEquipment != null && !entityEquipment.isEmpty()) {
            sendSilently(new WrapperPlayServerEntityEquipment(spawn.entityId, entityEquipment));
        }
    }

    private boolean shouldHide(EntitySpawn spawn) {
        if (!entityVisibility || !isEnabled() || spawn.uuid != null && spawn.uuid.equals(player.uuid)) {
            return false;
        }

        Vector3d position = spawn.position;
        return position.getY() < thresholdY
                && (shouldMaskBlock((int) Math.floor(position.getX()), (int) Math.floor(position.getY()), (int) Math.floor(position.getZ()))
                || isOccluded(position.getX(), position.getY() + 0.9, position.getZ()));
    }

    private boolean isOccluded(double targetX, double targetY, double targetZ) {
        double eyeY = player.y + player.getEyeHeight();
        double deltaX = targetX - player.x;
        double deltaY = targetY - eyeY;
        double deltaZ = targetZ - player.z;
        double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        if (distanceSquared <= (double) proximityRadius * proximityRadius) {
            return false;
        }
        if (distanceSquared > (double) lineOfSightDistance * lineOfSightDistance) {
            return true;
        }
        return !hasLineOfSight(player.x, eyeY, player.z, targetX, targetY, targetZ)
                && !hasLineOfSight(player.x, eyeY, player.z, targetX, targetY + 0.8, targetZ)
                && !hasLineOfSight(player.x, eyeY, player.z, targetX, targetY - 0.8, targetZ);
    }

    private boolean hasLineOfSight(
            double startX, double startY, double startZ,
            double targetX, double targetY, double targetZ
    ) {
        double deltaX = targetX - startX;
        double deltaY = targetY - startY;
        double deltaZ = targetZ - startZ;
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (distance <= 0.5) {
            return true;
        }

        double step = 0.5 / distance;
        for (double progress = step; progress < 1.0 - step; progress += step) {
            int blockX = (int) Math.floor(startX + deltaX * progress);
            int blockY = (int) Math.floor(startY + deltaY * progress);
            int blockZ = (int) Math.floor(startZ + deltaZ * progress);
            if (!player.compensatedWorld.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                return true;
            }
            if (isVisionBlocking(player.compensatedWorld.getBlock(blockX, blockY, blockZ).getType())) {
                return false;
            }
        }
        return true;
    }

    private void sendSilently(PacketWrapper<?> packet) {
        PacketEvents.getAPI().getProtocolManager().sendPacketSilently(player.user.getChannel(), packet);
    }

    private static final class EntitySpawn {
        private final int entityId;
        private final UUID uuid;
        private final EntityType entityType;
        private final Kind kind;
        private final float headYaw;
        private final int data;
        private final Optional<Vector3d> velocity;
        private final List<EntityData<?>> initialMetadata;
        private Vector3d position;
        private float pitch;
        private float yaw;

        private EntitySpawn(
                int entityId, UUID uuid, EntityType entityType, Kind kind, Vector3d position,
                float pitch, float yaw, float headYaw, int data,
                Optional<Vector3d> velocity, List<EntityData<?>> initialMetadata
        ) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.entityType = entityType;
            this.kind = kind;
            this.position = position;
            this.pitch = pitch;
            this.yaw = yaw;
            this.headYaw = headYaw;
            this.data = data;
            this.velocity = velocity;
            this.initialMetadata = initialMetadata;
        }

        private static EntitySpawn generic(
                int entityId, Optional<UUID> uuid, EntityType entityType, Vector3d position,
                float pitch, float yaw, float headYaw, int data, Optional<Vector3d> velocity
        ) {
            return new EntitySpawn(entityId, uuid.orElse(null), entityType, Kind.GENERIC, position, pitch, yaw, headYaw, data, velocity, List.of());
        }

        private static EntitySpawn living(
                int entityId, UUID uuid, EntityType entityType, Vector3d position,
                float yaw, float pitch, float headYaw, Vector3d velocity, List<EntityData<?>> metadata
        ) {
            return new EntitySpawn(entityId, uuid, entityType, Kind.LIVING, position, pitch, yaw, headYaw, 0, Optional.of(velocity), List.copyOf(metadata));
        }

        private static EntitySpawn player(
                int entityId, UUID uuid, Vector3d position,
                float yaw, float pitch, List<EntityData<?>> metadata
        ) {
            return new EntitySpawn(entityId, uuid, EntityTypes.PLAYER, Kind.PLAYER, position, pitch, yaw, 0, 0, Optional.empty(), List.copyOf(metadata));
        }

        private PacketWrapper<?> createPacket() {
            if (kind == Kind.PLAYER) {
                return new WrapperPlayServerSpawnPlayer(entityId, uuid, position, yaw, pitch, initialMetadata);
            }
            if (kind == Kind.LIVING) {
                return new WrapperPlayServerSpawnLivingEntity(
                        entityId, uuid, entityType, position, yaw, pitch, headYaw,
                        velocity.orElse(Vector3d.zero()), initialMetadata
                );
            }
            return new WrapperPlayServerSpawnEntity(
                    entityId, Optional.ofNullable(uuid), entityType,
                    position, pitch, yaw, headYaw, data, velocity
            );
        }
    }

    private enum Kind {
        GENERIC,
        LIVING,
        PLAYER
    }
}
