package ac.voidac.events.packets.worldreader;

import ac.voidac.VoidAPI;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.chunks.Column;
import ac.voidac.utils.data.TeleportData;
import ac.voidac.utils.latency.AntiEspPolicy;
import ac.voidac.utils.latency.CompensatedWorld;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.ByteArray3d;
import com.github.retrooper.packetevents.protocol.world.chunk.NibbleArray3d;
import com.github.retrooper.packetevents.protocol.world.chunk.ShortArray3d;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_16.Chunk_v1_9;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_7.Chunk_v1_7;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_8.Chunk_v1_8;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.MapPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BaseStorage;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.LegacyFlexibleStorage;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgePlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkDataBulk;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class BasePacketWorldReader extends PacketListenerAbstract {
    private static final Field CHUNK_V1_9_BLOCK_COUNT_FIELD = lookupField(Chunk_v1_9.class, "blockCount");
    private static final Field CHUNK_V1_9_DATA_PALETTE_FIELD = lookupField(Chunk_v1_9.class, "dataPalette");

    public BasePacketWorldReader() {
        super(PacketListenerPriority.HIGH);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.EXPLOSION
                && PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_2)) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player != null && player.antiEspManager.isEnabled()) {
                WrapperPlayServerExplosion explosion = new WrapperPlayServerExplosion(event);
                var interaction = explosion.getBlockInteraction();
                if (interaction != WrapperPlayServerExplosion.BlockInteraction.KEEP_BLOCKS) {
                    for (Vector3i position : explosion.getRecords()) {
                        if (interaction != WrapperPlayServerExplosion.BlockInteraction.TRIGGER_BLOCKS) {
                            player.antiEspManager.cacheBlock(position.getX(), position.getY(), position.getZ(), 0);
                        }
                        player.antiEspManager.exposeTerrain(position.getX(), position.getY(), position.getZ());
                    }
                    if (interaction == WrapperPlayServerExplosion.BlockInteraction.TRIGGER_BLOCKS) {
                        player.antiEspManager.flushTerrainUpdates();
                    } else {
                        player.antiEspManager.flushTerrainUpdatesAfter(event);
                    }
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Server.UNLOAD_CHUNK) {
            WrapperPlayServerUnloadChunk unloadChunk = new WrapperPlayServerUnloadChunk(event);
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            unloadChunk(player, unloadChunk.getChunkX(), unloadChunk.getChunkZ());
        }

        // 1.7 and 1.8 only
        if (event.getPacketType() == PacketType.Play.Server.MAP_CHUNK_BULK) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            handleMapChunkBulk(player, event);
        }

        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            handleMapChunk(player, event);
        }

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            handleBlockChange(player, event);
        }

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerBlockEntityData blockEntity = new WrapperPlayServerBlockEntityData(event);
            player.antiEspManager.cacheBlockEntity(blockEntity);
            if (player.antiEspManager.shouldHideBlockEntity(blockEntity.getPosition())) {
                event.setCancelled(true);
            }
        }

        if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            handleMultiBlockChange(player, event);
        }

        if (event.getPacketType() == PacketType.Play.Server.ACKNOWLEDGE_BLOCK_CHANGES) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerAcknowledgeBlockChanges changes = new WrapperPlayServerAcknowledgeBlockChanges(event);
            player.compensatedWorld.handlePredictionConfirmation(changes.getSequence());
        }

        if (event.getPacketType() == PacketType.Play.Server.ACKNOWLEDGE_PLAYER_DIGGING) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerAcknowledgePlayerDigging ack = new WrapperPlayServerAcknowledgePlayerDigging(event);
            player.compensatedWorld.handleBlockBreakAck(ack.getBlockPosition(), ack.getBlockId(), ack.getAction(), ack.isSuccessful());
        }

        if (event.getPacketType() == PacketType.Play.Server.CHANGE_GAME_STATE) {
            VoidPlayer player = VoidAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerChangeGameState newState = new WrapperPlayServerChangeGameState(event);

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                if (newState.getReason() == WrapperPlayServerChangeGameState.Reason.BEGIN_RAINING) {
                    player.compensatedWorld.isRaining = true;
                } else if (newState.getReason() == WrapperPlayServerChangeGameState.Reason.END_RAINING) {
                    player.compensatedWorld.isRaining = false;
                } else if (newState.getReason() == WrapperPlayServerChangeGameState.Reason.RAIN_LEVEL_CHANGE) {
                    player.compensatedWorld.isRaining = newState.getValue() > 0.2f;
                }
            });
        }
    }

    public void handleMapChunkBulk(VoidPlayer player, PacketSendEvent event) {
        WrapperPlayServerChunkDataBulk chunkData = new WrapperPlayServerChunkDataBulk(event);
        BaseChunk[][] chunks = chunkData.getChunks();
        int[] chunkX = chunkData.getX();
        int[] chunkZ = chunkData.getZ();

        boolean changed = false;
        player.antiEspManager.flushTerrainUpdatesAfter(event);
        int columns = Math.min(chunks.length, Math.min(chunkX.length, chunkZ.length));
        for (int i = 0; i < columns; i++) {
            BaseChunk[] columnChunks = chunks[i];
            if (columnChunks == null) {
                continue;
            }

            addChunkToCache(event, player, columnChunks, true, chunkX[i], chunkZ[i], null);
            player.antiEspManager.cacheLegacyChunk(chunkX[i], chunkZ[i], columnChunks);
            BaseChunk[] maskedChunks = applyAntiEspToChunkCopy(player, chunkX[i], chunkZ[i], columnChunks, null).chunks();
            BaseChunk[] mutatedChunks = applyDecoysToChunkCopy(player, chunkX[i], chunkZ[i], maskedChunks);
            if (mutatedChunks != columnChunks) {
                chunks[i] = mutatedChunks;
                changed = true;
            }
        }

        if (changed) {
            event.setLastUsedWrapper(chunkData);
            event.markForReEncode(true);
        }
    }

    public void handleMapChunk(VoidPlayer player, PacketSendEvent event) {
        WrapperPlayServerChunkData chunkData = new WrapperPlayServerChunkData(event);
        com.github.retrooper.packetevents.protocol.world.chunk.Column column = chunkData.getColumn();
        BaseChunk[] chunks = column.getChunks();

        addChunkToCache(event, player, chunks, column.isFullChunk(), column.getX(), column.getZ(), column.getTileEntities());
        player.antiEspManager.cacheChunk(chunkData);
        player.antiEspManager.flushTerrainUpdatesAfter(event);
        MaskedColumn masked = applyAntiEspToChunkCopy(player, column.getX(), column.getZ(), chunks, column.getTileEntities());
        BaseChunk[] mutatedChunks = applyDecoysToChunkCopy(player, column.getX(), column.getZ(), masked.chunks());
        if (mutatedChunks != chunks || masked.tileEntities() != column.getTileEntities()) {
            chunkData.setColumn(copyPacketColumn(column, mutatedChunks, masked.tileEntities()));
            event.setLastUsedWrapper(chunkData);
            event.markForReEncode(true);
        }
    }

    public void addChunkToCache(PacketSendEvent event, VoidPlayer player, BaseChunk[] chunks, boolean isGroundUp, int chunkX, int chunkZ, TileEntity[] tileEntities) {
        double chunkCenterX = (chunkX << 4) + 8;
        double chunkCenterZ = (chunkZ << 4) + 8;
        boolean shouldPostTrans = Math.abs(player.x - chunkCenterX) < 16 && Math.abs(player.z - chunkCenterZ) < 16;

        for (TeleportData teleports : player.getSetbackTeleportUtil().pendingTeleports) {
            if (teleports.getFlags().getMask() != 0) {
                continue; // Worse that will happen is people will get an extra setback...
            }
            shouldPostTrans = shouldPostTrans || (Math.abs(teleports.getLocation().getX() - chunkCenterX) < 16 && Math.abs(teleports.getLocation().getZ() - chunkCenterZ) < 16);
        }

        if (shouldPostTrans) {
            event.getTasksAfterSend().add(player::sendTransaction); // Player is in this unloaded chunk
        }
        if (isGroundUp) {
            Column column = new Column(chunkX, chunkZ, chunks, player.lastTransactionSent.get());
            player.compensatedWorld.addToCache(column, chunkX, chunkZ, tileEntities);
        } else {
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                Column existingColumn = player.compensatedWorld.getChunk(chunkX, chunkZ);
                if (existingColumn == null) {
                    // Corrupting the player's empty chunk is actually quite meaningless
                    // You are able to set blocks inside it, and they do apply, it just always returns air despite what its data says
                    // So go ahead, corrupt the player's empty chunk and make it no longer all air, it doesn't matter
                    //
                    // LogUtil.warn("Invalid non-ground up continuous sent for empty chunk " + chunkX + " " + chunkZ + " for " + player.user.getProfile().getName() + "! This corrupts the player's empty chunk!");
                    return;
                }
                existingColumn.mergeChunks(chunks);
            });
        }
    }

    public void unloadChunk(VoidPlayer player, int x, int z) {
        if (player == null) return;
        player.storageEspDecoyManager.onChunkUnload(x, z);
        player.antiEspManager.unloadChunk(x, z);
        player.compensatedWorld.removeChunkLater(x, z);
    }

    public void handleBlockChange(VoidPlayer player, PacketSendEvent event) {
        WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange(event);
        int range = 16;

        Vector3i blockPosition = blockChange.getBlockPosition();
        int originalBlockId = blockChange.getBlockId();
        player.antiEspManager.cacheBlock(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), originalBlockId);
        player.antiEspManager.flushTerrainUpdatesAfter(event);
        // Don't spam transactions (block changes are sent in batches)
        if (Math.abs(blockPosition.getX() - player.x) < range && Math.abs(blockPosition.getY() - player.y) < range && Math.abs(blockPosition.getZ() - player.z) < range &&
                player.lastTransSent + 2 < System.currentTimeMillis())
            player.sendTransaction();

        int decoyBlockId = player.storageEspDecoyManager.getDecoyBlockId(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
        if (player.antiEspManager.shouldMaskBlock(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ())) {
            blockChange.setBlockID(player.antiEspManager.getReplacementBlockId());
            event.markForReEncode(true);
        } else if (decoyBlockId != -1) {
            blockChange.setBlockID(decoyBlockId);
            event.markForReEncode(true);
        }

        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            player.compensatedWorld.updateBlock(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), originalBlockId);
        });
    }

    public void handleMultiBlockChange(VoidPlayer player, PacketSendEvent event) {
        WrapperPlayServerMultiBlockChange multiBlockChange = new WrapperPlayServerMultiBlockChange(event);
        player.antiEspManager.flushTerrainUpdatesAfter(event);

        int range = 16;

        final var blocks = multiBlockChange.getBlocks();
        for (WrapperPlayServerMultiBlockChange.EncodedBlock blockChange : blocks) {
            // Don't send a transaction unless it's within 16 blocks of the player
            if (Math.abs(blockChange.getX() - player.x) < range && Math.abs(blockChange.getY() - player.y) < range && Math.abs(blockChange.getZ() - player.z) < range && player.lastTransSent + 2 < System.currentTimeMillis()) {
                player.sendTransaction();
                break;
            }
        }

        boolean changed = false;
        int[] originalBlockIds = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            originalBlockIds[i] = blocks[i].getBlockId();
            player.antiEspManager.cacheBlock(blocks[i].getX(), blocks[i].getY(), blocks[i].getZ(), originalBlockIds[i]);
        }
        for (WrapperPlayServerMultiBlockChange.EncodedBlock blockChange : blocks) {
            if (player.antiEspManager.shouldMaskBlock(blockChange.getX(), blockChange.getY(), blockChange.getZ())) {
                blockChange.setBlockId(player.antiEspManager.getReplacementBlockId());
                changed = true;
                continue;
            }
            int decoyBlockId = player.storageEspDecoyManager.getDecoyBlockId(blockChange.getX(), blockChange.getY(), blockChange.getZ());
            if (decoyBlockId != -1) {
                blockChange.setBlockId(decoyBlockId);
                changed = true;
            }
        }

        if (changed) {
            event.markForReEncode(true);
        }

        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            for (int i = 0; i < blocks.length; i++) {
                WrapperPlayServerMultiBlockChange.EncodedBlock blockChange = blocks[i];
                player.compensatedWorld.updateBlock(blockChange.getX(), blockChange.getY(), blockChange.getZ(), originalBlockIds[i]);
            }
        });
    }

    private BaseChunk[] applyDecoysToChunkCopy(VoidPlayer player, int chunkX, int chunkZ, BaseChunk[] chunks) {
        if (chunks == null || chunks.length == 0 || !player.storageEspDecoyManager.isActive()) {
            return chunks;
        }

        List<Vector3i> positions = player.storageEspDecoyManager.getDecoyPositionsForChunk(chunkX, chunkZ);
        if (positions.isEmpty()) {
            return chunks;
        }

        BaseChunk[] copiedChunks = null;
        int minHeight = player.compensatedWorld.getMinHeight();

        for (Vector3i position : positions) {
            int localY = position.getY() - minHeight;
            int sectionIndex = localY >> 4;
            if (localY < 0 || sectionIndex < 0 || sectionIndex >= chunks.length) {
                continue;
            }

            int decoyBlockId = player.storageEspDecoyManager.getDecoyBlockId(position.getX(), position.getY(), position.getZ());
            if (decoyBlockId == -1) {
                continue;
            }

            BaseChunk targetChunk = copiedChunks != null ? copiedChunks[sectionIndex] : chunks[sectionIndex];
            if (targetChunk == null) {
                continue;
            }

            if (copiedChunks == null) {
                copiedChunks = copyChunkArray(chunks);
                targetChunk = copiedChunks[sectionIndex];
                if (targetChunk == null) {
                    continue;
                }
            }

            targetChunk.set(position.getX() & 15, localY & 15, position.getZ() & 15, decoyBlockId);
        }

        return copiedChunks == null ? chunks : copiedChunks;
    }

    private MaskedColumn applyAntiEspToChunkCopy(
            VoidPlayer player, int chunkX, int chunkZ,
            BaseChunk[] chunks, TileEntity[] tileEntities
    ) {
        if (chunks == null || chunks.length == 0 || !player.antiEspManager.shouldMaskTerrain()) {
            return new MaskedColumn(chunks, tileEntities);
        }

        int minHeight = player.user.getMinWorldHeight();
        int thresholdY = player.antiEspManager.getThresholdY();
        int replacementBlockId = player.antiEspManager.getReplacementBlockId();
        BaseChunk[] maskedChunks = null;

        for (int sectionIndex = 0; sectionIndex < chunks.length; sectionIndex++) {
            BaseChunk chunk = chunks[sectionIndex];
            int sectionY = (minHeight >> 4) + sectionIndex;
            int maskedRows = AntiEspPolicy.maskedRows(minHeight, sectionIndex, thresholdY);
            if (chunk == null || maskedRows == 0 || player.antiEspManager.shouldRevealSection(chunkX, sectionY, chunkZ)) {
                continue;
            }

            if (maskedChunks == null) {
                maskedChunks = chunks.clone();
            }
            if (maskedRows == 16) {
                maskedChunks[sectionIndex] = createFilledChunk(chunk, replacementBlockId);
                continue;
            }

            BaseChunk copied = copyChunk(chunk);
            for (int y = 0; y < maskedRows; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        copied.set(x, y, z, replacementBlockId);
                    }
                }
            }
            maskedChunks[sectionIndex] = copied;
        }

        TileEntity[] maskedTileEntities = filterTileEntities(player, chunkX, chunkZ, tileEntities);
        return new MaskedColumn(maskedChunks == null ? chunks : maskedChunks, maskedTileEntities);
    }

    private BaseChunk createFilledChunk(BaseChunk original, int blockId) {
        if (original instanceof Chunk_v1_18 modernChunk) {
            DataPalette blocks = new DataPalette(new SingletonPalette(blockId), null, PaletteType.CHUNK);
            DataPalette biomes = modernChunk.getBiomeData();
            return new Chunk_v1_18(
                    CompensatedWorld.blockVersion,
                    blockId == 0 ? 0 : 4096,
                    0,
                    blocks,
                    biomes == null ? null : copyDataPalette(biomes)
            );
        }

        if (original instanceof Chunk_v1_9 modernChunk) {
            DataPalette blocks;
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_16)) {
                blocks = PaletteType.CHUNK.create();
            } else {
                blocks = new DataPalette(new ListPalette(4), new LegacyFlexibleStorage(4, 4096), PaletteType.CHUNK);
            }
            blocks.palette.stateToId(blockId);
            Chunk_v1_9 filled = new Chunk_v1_9(blockId == 0 ? 0 : 4096, blocks);
            if (modernChunk.getBlockLight() != null) {
                filled.setBlockLight(new NibbleArray3d(modernChunk.getBlockLight().getData().clone()));
            }
            if (modernChunk.getSkyLight() != null) {
                filled.setSkyLight(new NibbleArray3d(modernChunk.getSkyLight().getData().clone()));
            }
            return filled;
        }

        if (original instanceof Chunk_v1_8 legacyChunk) {
            short[] blocks = new short[4096];
            Arrays.fill(blocks, (short) blockId);
            return new Chunk_v1_8(
                    new ShortArray3d(blocks),
                    legacyChunk.getBlockLight() == null ? null : new NibbleArray3d(legacyChunk.getBlockLight().getData().clone()),
                    legacyChunk.getSkyLight() == null ? null : new NibbleArray3d(legacyChunk.getSkyLight().getData().clone())
            );
        }

        BaseChunk filled = copyChunk(original);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    filled.set(x, y, z, blockId);
                }
            }
        }
        return filled;
    }

    private TileEntity[] filterTileEntities(VoidPlayer player, int chunkX, int chunkZ, TileEntity[] tileEntities) {
        if (tileEntities == null || tileEntities.length == 0) {
            return tileEntities;
        }

        int visibleCount = 0;
        for (TileEntity tileEntity : tileEntities) {
            if (!player.antiEspManager.shouldHideBlockEntity((chunkX << 4) | (tileEntity.getX() & 15), tileEntity.getY(), (chunkZ << 4) | (tileEntity.getZ() & 15))) {
                visibleCount++;
            }
        }
        if (visibleCount == tileEntities.length) {
            return tileEntities;
        }

        TileEntity[] visible = new TileEntity[visibleCount];
        int index = 0;
        for (TileEntity tileEntity : tileEntities) {
            if (!player.antiEspManager.shouldHideBlockEntity((chunkX << 4) | (tileEntity.getX() & 15), tileEntity.getY(), (chunkZ << 4) | (tileEntity.getZ() & 15))) {
                visible[index++] = tileEntity;
            }
        }
        return visible;
    }

    private BaseChunk[] copyChunkArray(BaseChunk[] chunks) {
        BaseChunk[] copied = new BaseChunk[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            copied[i] = copyChunk(chunks[i]);
        }
        return copied;
    }

    public static BaseChunk copyChunk(BaseChunk chunk) {
        if (chunk == null) {
            return null;
        }

        if (chunk instanceof Chunk_v1_7 legacyChunk) {
            return copyChunkV17(legacyChunk);
        }
        if (chunk instanceof Chunk_v1_8 legacyChunk) {
            return copyChunkV18(legacyChunk);
        }
        if (chunk instanceof Chunk_v1_9 modernChunk) {
            return copyChunkV19(modernChunk);
        }
        if (chunk instanceof Chunk_v1_18 modernChunk) {
            return copyChunkV118(modernChunk);
        }

        return chunk;
    }

    private static Chunk_v1_7 copyChunkV17(Chunk_v1_7 chunk) {
        ByteArray3d blocks = chunk.getBlocks();
        NibbleArray3d metadata = chunk.getMetadata();
        NibbleArray3d blockLight = chunk.getBlockLight();
        NibbleArray3d skyLight = chunk.getSkyLight();
        NibbleArray3d extendedBlocks = chunk.getExtendedBlocks();

        return new Chunk_v1_7(
                blocks == null ? null : new ByteArray3d(blocks.getData().clone()),
                metadata == null ? null : new NibbleArray3d(metadata.getData().clone()),
                blockLight == null ? null : new NibbleArray3d(blockLight.getData().clone()),
                skyLight == null ? null : new NibbleArray3d(skyLight.getData().clone()),
                extendedBlocks == null ? null : new NibbleArray3d(extendedBlocks.getData().clone())
        );
    }

    private static Chunk_v1_8 copyChunkV18(Chunk_v1_8 chunk) {
        ShortArray3d blocks = chunk.getBlocks();
        NibbleArray3d blockLight = chunk.getBlockLight();
        NibbleArray3d skyLight = chunk.getSkyLight();

        return new Chunk_v1_8(
                blocks == null ? null : new ShortArray3d(blocks.getData().clone()),
                blockLight == null ? null : new NibbleArray3d(blockLight.getData().clone()),
                skyLight == null ? null : new NibbleArray3d(skyLight.getData().clone())
        );
    }

    private static Chunk_v1_9 copyChunkV19(Chunk_v1_9 chunk) {
        try {
            int blockCount = (int) CHUNK_V1_9_BLOCK_COUNT_FIELD.get(chunk);
            DataPalette dataPalette = (DataPalette) CHUNK_V1_9_DATA_PALETTE_FIELD.get(chunk);
            Chunk_v1_9 copied = new Chunk_v1_9(blockCount, copyDataPalette(dataPalette));

            NibbleArray3d blockLight = chunk.getBlockLight();
            NibbleArray3d skyLight = chunk.getSkyLight();
            if (blockLight != null) {
                copied.setBlockLight(new NibbleArray3d(blockLight.getData().clone()));
            }
            if (skyLight != null) {
                copied.setSkyLight(new NibbleArray3d(skyLight.getData().clone()));
            }

            return copied;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to copy PacketEvents 1.9 chunk", e);
        }
    }

    private static Chunk_v1_18 copyChunkV118(Chunk_v1_18 chunk) {
        DataPalette chunkData = chunk.getChunkData();
        DataPalette biomeData = chunk.getBiomeData();
        Chunk_v1_18 copied = new Chunk_v1_18(CompensatedWorld.blockVersion, chunk.getBlockCount(), chunk.getFluidCount(), copyDataPalette(chunkData), biomeData == null ? null : copyDataPalette(biomeData));
        return copied;
    }

    private static DataPalette copyDataPalette(DataPalette original) {
        if (original == null) {
            return null;
        }

        Palette copiedPalette = copyPalette(original.palette);
        BaseStorage copiedStorage = copyStorage(original.storage);
        return new DataPalette(copiedPalette, copiedStorage, original.paletteType);
    }

    private static Palette copyPalette(Palette original) {
        if (original instanceof GlobalPalette) {
            return new GlobalPalette();
        }

        if (original instanceof SingletonPalette singletonPalette) {
            return new SingletonPalette(singletonPalette.idToState(0));
        }

        if (original instanceof ListPalette listPalette) {
            ListPalette copied = new ListPalette(listPalette.getBits());
            for (int index = 0; index < listPalette.size(); index++) {
                copied.stateToId(listPalette.idToState(index));
            }
            return copied;
        }

        if (original instanceof MapPalette mapPalette) {
            MapPalette copied = new MapPalette(mapPalette.getBits());
            for (int index = 0; index < mapPalette.size(); index++) {
                copied.stateToId(mapPalette.idToState(index));
            }
            return copied;
        }

        return original;
    }

    private static BaseStorage copyStorage(BaseStorage original) {
        if (original instanceof BitStorage bitStorage) {
            return new BitStorage(bitStorage.getBitsPerEntry(), bitStorage.getSize(), bitStorage.getData().clone());
        }

        if (original instanceof LegacyFlexibleStorage legacyStorage) {
            return new LegacyFlexibleStorage(legacyStorage.getBitsPerEntry(), legacyStorage.getData().clone());
        }

        return original;
    }

    private com.github.retrooper.packetevents.protocol.world.chunk.Column copyPacketColumn(com.github.retrooper.packetevents.protocol.world.chunk.Column column, BaseChunk[] chunks, TileEntity[] tileEntities) {
        if (column.hasBiomeData()) {
            com.github.retrooper.packetevents.protocol.nbt.NBTCompound heightmaps = column.getHeightMaps();
            int[] biomeDataInts = column.getBiomeDataInts();
            if (biomeDataInts != null) {
                return new com.github.retrooper.packetevents.protocol.world.chunk.Column(column.getX(), column.getZ(), column.isFullChunk(), chunks, tileEntities, heightmaps, biomeDataInts.clone());
            }

            byte[] biomeDataBytes = column.getBiomeDataBytes();
            if (biomeDataBytes != null) {
                return new com.github.retrooper.packetevents.protocol.world.chunk.Column(column.getX(), column.getZ(), column.isFullChunk(), chunks, tileEntities, heightmaps, biomeDataBytes.clone());
            }
        }

        if (column.hasHeightMaps()) {
            return new com.github.retrooper.packetevents.protocol.world.chunk.Column(column.getX(), column.getZ(), column.isFullChunk(), chunks, tileEntities, column.getHeightmaps());
        }

        return new com.github.retrooper.packetevents.protocol.world.chunk.Column(column.getX(), column.getZ(), column.isFullChunk(), chunks, tileEntities);
    }

    private record MaskedColumn(BaseChunk[] chunks, TileEntity[] tileEntities) {}

    private static Field lookupField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing PacketEvents field: " + type.getName() + '.' + name, e);
        }
    }
}
