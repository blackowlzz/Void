package ac.voidac.utils.latency;

import ac.voidac.events.packets.worldreader.BasePacketWorldReader;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.collisions.CollisionData;
import ac.voidac.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;

final class AntiEspTerrain {
    private final VoidPlayer player;
    private final TerrainVisibility visibility = new TerrainVisibility();
    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final Int2ByteOpenHashMap openStates = new Int2ByteOpenHashMap();
    private boolean active;
    private boolean dirty;
    private int thresholdY;
    private int replacement;
    private int radius;
    private int interval;
    private int lastTick = Integer.MIN_VALUE;
    private int lastBlockX = Integer.MIN_VALUE;
    private int lastBlockY = Integer.MIN_VALUE;
    private int lastBlockZ = Integer.MIN_VALUE;
    private double lastEyeHeight;

    AntiEspTerrain(VoidPlayer player) {
        this.player = player;
        openStates.defaultReturnValue((byte) -1);
    }

    synchronized void configure(boolean active, int thresholdY, int replacement, int radius, int interval) {
        if (this.active && (!active || this.thresholdY != thresholdY || this.replacement != replacement)) {
            restore();
            clear();
        }
        dirty |= this.active != active || this.radius != radius;
        this.active = active;
        this.thresholdY = thresholdY;
        this.replacement = replacement;
        this.radius = radius;
        this.interval = interval;
    }

    synchronized void cache(int chunkX, int chunkZ, BaseChunk[] chunks, boolean full, TileEntity[] tiles) {
        if (!active || chunks == null) {
            return;
        }
        int minSection = player.user.getMinWorldHeight() >> 4;
        for (int i = 0; i < chunks.length; i++) {
            int sectionY = minSection + i;
            if (!full && chunks[i] == null) {
                continue;
            }
            long key = TerrainVisibility.key(chunkX, sectionY, chunkZ);
            Section section = new Section(chunks[i]);
            sections.put(key, section);
            updatePortals(key, section);
        }
        if (tiles != null) {
            for (TileEntity tile : tiles) {
                int x = (chunkX << 4) | (tile.getX() & 15);
                int y = tile.getY();
                int z = (chunkZ << 4) | (tile.getZ() & 15);
                if (CompensatedWorld.blockVersion.isOlderThan(ClientVersion.V_1_18)) {
                    visibility.markVisible(chunkX, y >> 4, chunkZ);
                    continue;
                }
                var type = BlockEntityTypes.getById(CompensatedWorld.blockVersion, tile.getType());
                if (type == null) {
                    visibility.markVisible(chunkX, y >> 4, chunkZ);
                    continue;
                }
                updateBlockEntity(new WrapperPlayServerBlockEntityData(new Vector3i(x, y, z), type, tile.getNBT()));
            }
        }
        recalculate(player.x, player.y, player.z);
        for (int i = 0; i < chunks.length; i++) {
            if (!full && chunks[i] == null) {
                continue;
            }
            int sectionY = minSection + i;
            Section section = sections.get(TerrainVisibility.key(chunkX, sectionY, chunkZ));
            if (section != null) {
                section.maskedRows = chunks[i] != null && !visibility.isVisible(chunkX, sectionY, chunkZ)
                        ? Math.max(0, Math.min(16, thresholdY - (sectionY << 4))) : 0;
            }
        }
    }

    synchronized void updateBlock(int x, int y, int z, int blockId) {
        if (!active) {
            return;
        }
        long key = TerrainVisibility.key(x >> 4, y >> 4, z >> 4);
        Section section = sections.get(key);
        if (section == null) {
            return;
        }
        int index = index(x, y, z);
        int oldId = section.get(x & 15, y & 15, z & 15);
        section.update(x, y, z, blockId);
        if (WrappedBlockState.getByGlobalId(CompensatedWorld.blockVersion, oldId).getType()
                != WrappedBlockState.getByGlobalId(CompensatedWorld.blockVersion, blockId).getType()) {
            section.tiles.remove(index);
        }
        if (isOpen(oldId) != isOpen(blockId)) {
            section.portalsDirty = true;
            dirty = true;
        }
    }

    synchronized void updateBlockEntity(WrapperPlayServerBlockEntityData packet) {
        Vector3i pos = packet.getPosition();
        Section section = sections.get(TerrainVisibility.key(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        if (!active || section == null) {
            return;
        }
        section.tiles.put(index(pos.getX(), pos.getY(), pos.getZ()), new WrapperPlayServerBlockEntityData(
                pos, packet.getBlockEntityType(), copyBlockEntityTag(packet.getNBT())));
    }

    static NBTCompound copyBlockEntityTag(NBTCompound tag) {
        return tag == null ? new NBTCompound() : tag.copy();
    }

    synchronized boolean shouldMask(int x, int y, int z) {
        if (!active || y >= thresholdY) {
            return false;
        }
        Section section = sections.get(TerrainVisibility.key(x >> 4, y >> 4, z >> 4));
        return section != null && (y & 15) < section.maskedRows;
    }

    synchronized boolean isVisible(int x, int y, int z) {
        return !active || visibility.isVisible(x, y, z);
    }

    synchronized void tick(double x, double y, double z, int tick) {
        if (!active) {
            return;
        }
        boolean moved = (int) Math.floor(x) != lastBlockX || (int) Math.floor(y) != lastBlockY
                || (int) Math.floor(z) != lastBlockZ || lastEyeHeight != player.getEyeHeight();
        if (!moved && (!dirty || lastTick != Integer.MIN_VALUE && tick - lastTick < interval)) {
            return;
        }
        lastTick = tick;
        recalculate(x, y, z);
        flush();
    }

    private void recalculate(double x, double y, double z) {
        for (var entry : sections.long2ObjectEntrySet()) {
            if (entry.getValue().portalsDirty) {
                updatePortals(entry.getLongKey(), entry.getValue());
            }
        }
        lastBlockX = (int) Math.floor(x);
        lastBlockY = (int) Math.floor(y);
        lastBlockZ = (int) Math.floor(z);
        lastEyeHeight = player.getEyeHeight();
        visibility.revealFrom(x, y + lastEyeHeight, z, radius, thresholdY);
        for (var teleport : player.getSetbackTeleportUtil().pendingTeleports) {
            if (teleport.getFlags().getMask() == 0) {
                var position = teleport.getLocation();
                visibility.revealFrom(position.getX(), position.getY() + player.getEyeHeight(), position.getZ(), radius, thresholdY);
            }
        }
        dirty = false;
    }

    private void updatePortals(long key, Section section) {
        int uniform = section.uniformState();
        if (uniform != -1) {
            visibility.putUniform(TerrainVisibility.x(key), TerrainVisibility.y(key), TerrainVisibility.z(key), isOpen(uniform));
        } else {
            visibility.put(TerrainVisibility.x(key), TerrainVisibility.y(key), TerrainVisibility.z(key),
                    (x, y, z) -> isOpen(section.get(x, y, z)));
        }
        section.portalsDirty = false;
    }

    synchronized void flush() {
        if (active && dirty) {
            recalculate(player.x, player.y, player.z);
        }
        visibility.drainRevealed(key -> {
            Section section = sections.get(key);
            if (section != null && section.maskedRows != 0) {
                reveal(key, section);
            }
        });
    }

    private void restore() {
        for (var entry : sections.long2ObjectEntrySet()) {
            if (entry.getValue().maskedRows != 0) {
                reveal(entry.getLongKey(), entry.getValue());
            }
        }
    }

    private void reveal(long key, Section section) {
        int sectionX = TerrainVisibility.x(key);
        int sectionY = TerrainVisibility.y(key);
        int sectionZ = TerrainVisibility.z(key);
        var changes = section.createReveal(sectionX, sectionY, sectionZ, replacement);
        for (var change : changes) {
            int decoyId = player.storageEspDecoyManager.getDecoyBlockId(change.getX(), change.getY(), change.getZ());
            if (decoyId != -1) {
                change.setBlockId(decoyId);
            }
        }
        if (changes.length != 0) {
            send(new WrapperPlayServerMultiBlockChange(new Vector3i(sectionX, sectionY, sectionZ), false, changes));
        }
        section.maskedRows = 0;
        for (var tile : section.tiles.values()) {
            send(new WrapperPlayServerBlockEntityData(tile.getPosition(), tile.getBlockEntityType(),
                    copyBlockEntityTag(tile.getNBT())));
        }
    }

    private void send(com.github.retrooper.packetevents.wrapper.PacketWrapper<?> packet) {
        PacketEvents.getAPI().getProtocolManager().sendPacketSilently(player.user.getChannel(), packet);
    }

    synchronized void unload(int chunkX, int chunkZ) {
        var iterator = sections.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            long key = iterator.next().getLongKey();
            if (TerrainVisibility.x(key) == chunkX && TerrainVisibility.z(key) == chunkZ) {
                iterator.remove();
            }
        }
        visibility.removeColumn(chunkX, chunkZ);
        dirty = true;
    }

    synchronized void clear() {
        sections.clear();
        visibility.clear();
        lastBlockX = Integer.MIN_VALUE;
        lastBlockY = Integer.MIN_VALUE;
        lastBlockZ = Integer.MIN_VALUE;
        lastTick = Integer.MIN_VALUE;
        dirty = true;
    }

    synchronized void invalidate() {
        dirty = true;
    }

    synchronized void expose(int x, int y, int z) {
        visibility.markVisible(x >> 4, y >> 4, z >> 4);
        dirty = true;
    }

    private boolean isOpen(int blockId) {
        byte cached = openStates.get(blockId);
        if (cached != -1) {
            return cached == 1;
        }
        var type = WrappedBlockState.getByGlobalId(CompensatedWorld.blockVersion, blockId).getType();
        var collision = CollisionData.getData(type);
        boolean open = !type.isBlocking() || collision.box == null || !collision.box.isFullBlock()
                || Materials.isGlassBlock(type) || Materials.isGlassPane(type) || Materials.isLeaves(type)
                || type == StateTypes.ICE || type == StateTypes.SLIME_BLOCK || type == StateTypes.HONEY_BLOCK;
        openStates.put(blockId, (byte) (open ? 1 : 0));
        return open;
    }

    private static int index(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | x & 15;
    }

    static final class Section {
        private final BaseChunk chunk;
        private final Int2IntOpenHashMap updates = new Int2IntOpenHashMap();
        private final Int2ObjectOpenHashMap<WrapperPlayServerBlockEntityData> tiles = new Int2ObjectOpenHashMap<>();
        int maskedRows;
        private boolean portalsDirty;

        Section(BaseChunk chunk) {
            this.chunk = BasePacketWorldReader.copyChunk(chunk);
            updates.defaultReturnValue(-1);
        }

        int uniformState() {
            if (!updates.isEmpty()) {
                return -1;
            }
            if (chunk == null) {
                return 0;
            }
            if (chunk instanceof Chunk_v1_18 modern && modern.getChunkData().palette.size() == 1) {
                return modern.getChunkData().palette.idToState(0);
            }
            return -1;
        }

        int get(int x, int y, int z) {
            if (updates.isEmpty()) {
                return chunk == null ? 0 : chunk.getBlockId(x, y, z);
            }
            int update = updates.get(index(x, y, z));
            return update == -1 ? chunk == null ? 0 : chunk.getBlockId(x, y, z) : update;
        }

        void update(int x, int y, int z, int blockId) {
            updates.put(index(x, y, z), blockId);
        }

        WrapperPlayServerMultiBlockChange.EncodedBlock[] createReveal(int sectionX, int sectionY, int sectionZ, int replacement) {
            var changes = new ArrayList<WrapperPlayServerMultiBlockChange.EncodedBlock>();
            for (int y = 0; y < maskedRows; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int realId = get(x, y, z);
                        if (realId != replacement) {
                            changes.add(new WrapperPlayServerMultiBlockChange.EncodedBlock(realId,
                                    (sectionX << 4) | x, (sectionY << 4) | y, (sectionZ << 4) | z));
                        }
                    }
                }
            }
            return changes.toArray(new WrapperPlayServerMultiBlockChange.EncodedBlock[0]);
        }
    }
}
