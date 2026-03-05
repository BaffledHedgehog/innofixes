package baffledhedgehog.innofixes.fix.create;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExtendedSchematicFormatHelper {
    private static final String EXT_NBT = ".nbt";
    private static final String EXT_SCHEM = ".schem";
    private static final String EXT_LITEMATIC = ".litematic";

    private ExtendedSchematicFormatHelper() {
    }

    public static boolean isSupportedSchematicFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(EXT_NBT) || lower.endsWith(EXT_SCHEM) || lower.endsWith(EXT_LITEMATIC);
    }

    public static boolean isExtendedSchematicFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(EXT_SCHEM) || lower.endsWith(EXT_LITEMATIC);
    }

    public static String stripSupportedExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(EXT_LITEMATIC)) {
            return fileName.substring(0, fileName.length() - EXT_LITEMATIC.length());
        }
        if (lower.endsWith(EXT_SCHEM)) {
            return fileName.substring(0, fileName.length() - EXT_SCHEM.length());
        }
        if (lower.endsWith(EXT_NBT)) {
            return fileName.substring(0, fileName.length() - EXT_NBT.length());
        }
        return fileName;
    }

    public static StructureTemplate loadExtendedTemplate(Level level, Path path, String fileName) throws IOException {
        CompoundTag root = readAnyNbt(path);
        CompoundTag structureNbt;
        String lower = fileName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(EXT_SCHEM)) {
            structureNbt = convertSpongeSchematic(level, root);
        } else if (lower.endsWith(EXT_LITEMATIC)) {
            structureNbt = convertLitematic(level, root);
        } else {
            throw new IOException("Unsupported schematic extension: " + fileName);
        }

        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), structureNbt);
        return template;
    }

    private static CompoundTag readAnyNbt(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return NbtIo.readCompressed(in);
        } catch (IOException compressedReadFailure) {
            try (InputStream in = Files.newInputStream(path);
                 DataInputStream dataIn = new DataInputStream(in)) {
                return NbtIo.read(dataIn, NbtAccounter.UNLIMITED);
            }
        }
    }

    private static CompoundTag convertSpongeSchematic(Level level, CompoundTag sourceRoot) {
        CompoundTag root = sourceRoot;
        if (sourceRoot.contains("Schematic", 10)) {
            root = sourceRoot.getCompound("Schematic");
        }

        int width = getPositiveDimension(root, "Width");
        int height = getPositiveDimension(root, "Height");
        int length = getPositiveDimension(root, "Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            return emptyStructureTag();
        }

        CompoundTag blockRoot = root.contains("Blocks", 10) ? root.getCompound("Blocks") : root;
        CompoundTag paletteTag = blockRoot.getCompound("Palette");
        byte[] blockData = blockRoot.contains("Data", 7) ? blockRoot.getByteArray("Data") : root.getByteArray("BlockData");

        ListTag blockEntitiesTag;
        if (blockRoot.contains("BlockEntities", 9)) {
            blockEntitiesTag = blockRoot.getList("BlockEntities", 10);
        } else if (root.contains("BlockEntities", 9)) {
            blockEntitiesTag = root.getList("BlockEntities", 10);
        } else {
            blockEntitiesTag = root.getList("TileEntities", 10);
        }

        ListTag entitiesTag = root.getList("Entities", 10);
        Map<Integer, BlockState> paletteById = parseSpongePalette(level, paletteTag);
        Map<BlockPos, CompoundTag> blockEntitiesByPos = parseSpongeBlockEntities(blockEntitiesTag);

        long volumeLong = (long) width * (long) height * (long) length;
        if (volumeLong <= 0L || volumeLong > Integer.MAX_VALUE) {
            return emptyStructureTag();
        }
        int volume = (int) volumeLong;
        int[] stateIds = decodeVarInts(blockData, volume);

        List<PlacedBlock> blocks = new ArrayList<>();
        int xzLayer = width * length;
        int limit = Math.min(volume, stateIds.length);
        for (int index = 0; index < limit; index++) {
            int paletteId = stateIds[index];
            BlockState state = paletteById.getOrDefault(paletteId, Blocks.AIR.defaultBlockState());

            int y = index / xzLayer;
            int rem = index - y * xzLayer;
            int z = rem / width;
            int x = rem - z * width;

            BlockPos pos = new BlockPos(x, y, z);
            CompoundTag blockEntityData = blockEntitiesByPos.get(pos);
            if (state.isAir() && blockEntityData == null) {
                continue;
            }

            blocks.add(new PlacedBlock(pos, state, blockEntityData == null ? null : blockEntityData.copy()));
        }

        ListTag entities = parseSpongeEntities(entitiesTag);
        return buildStructureTag(width, height, length, blocks, entities);
    }

    private static CompoundTag convertLitematic(Level level, CompoundTag root) {
        if (!root.contains("Regions", 10) && root.contains("Litematic", 10)) {
            root = root.getCompound("Litematic");
        }

        if (!root.contains("Regions", 10)) {
            return emptyStructureTag();
        }

        CompoundTag regions = root.getCompound("Regions");
        if (regions.isEmpty()) {
            return emptyStructureTag();
        }

        List<LitematicRegion> parsedRegions = new ArrayList<>();
        int globalMinX = Integer.MAX_VALUE;
        int globalMinY = Integer.MAX_VALUE;
        int globalMinZ = Integer.MAX_VALUE;
        int globalMaxX = Integer.MIN_VALUE;
        int globalMaxY = Integer.MIN_VALUE;
        int globalMaxZ = Integer.MIN_VALUE;

        for (String regionName : regions.getAllKeys()) {
            CompoundTag regionTag = regions.getCompound(regionName);
            BlockPos regionPos = readNamedPos(regionTag, "Position");
            BlockPos regionSize = readNamedPos(regionTag, "Size");
            if (regionPos == null || regionSize == null) {
                continue;
            }

            LitematicRegion region = LitematicRegion.of(regionTag, regionPos, regionSize);
            if (region == null) {
                continue;
            }

            parsedRegions.add(region);
            globalMinX = Math.min(globalMinX, region.minX());
            globalMinY = Math.min(globalMinY, region.minY());
            globalMinZ = Math.min(globalMinZ, region.minZ());
            globalMaxX = Math.max(globalMaxX, region.maxX());
            globalMaxY = Math.max(globalMaxY, region.maxY());
            globalMaxZ = Math.max(globalMaxZ, region.maxZ());
        }

        if (parsedRegions.isEmpty()) {
            return emptyStructureTag();
        }

        int totalSizeX = globalMaxX - globalMinX + 1;
        int totalSizeY = globalMaxY - globalMinY + 1;
        int totalSizeZ = globalMaxZ - globalMinZ + 1;

        List<PlacedBlock> blocks = new ArrayList<>();
        ListTag entities = new ListTag();

        for (LitematicRegion region : parsedRegions) {
            Map<BlockPos, CompoundTag> beByRelativePos = parseLitematicBlockEntities(region, globalMinX, globalMinY, globalMinZ);

            List<BlockState> palette = parseLitematicPalette(level, region.tag().getList("BlockStatePalette", 10));
            if (palette.isEmpty()) {
                palette.add(Blocks.AIR.defaultBlockState());
            }

            int bitsPerEntry = getRequiredBits(palette.size());
            long[] packed = region.tag().getLongArray("BlockStates");
            long volumeLong = (long) region.absSizeX() * (long) region.absSizeY() * (long) region.absSizeZ();
            if (volumeLong <= 0L || volumeLong > Integer.MAX_VALUE) {
                continue;
            }

            int volume = (int) volumeLong;
            int xzLayer = region.absSizeX() * region.absSizeZ();
            for (int index = 0; index < volume; index++) {
                int paletteIndex = readPackedInt(packed, bitsPerEntry, index);
                if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                    paletteIndex = 0;
                }
                BlockState state = palette.get(paletteIndex);

                int y = index / xzLayer;
                int rem = index - y * xzLayer;
                int z = rem / region.absSizeX();
                int x = rem - z * region.absSizeX();

                int worldX = region.sizeX() >= 0 ? region.positionX() + x : region.positionX() - x;
                int worldY = region.sizeY() >= 0 ? region.positionY() + y : region.positionY() - y;
                int worldZ = region.sizeZ() >= 0 ? region.positionZ() + z : region.positionZ() - z;

                BlockPos relativePos = new BlockPos(worldX - globalMinX, worldY - globalMinY, worldZ - globalMinZ);
                CompoundTag blockEntityData = beByRelativePos.get(relativePos);
                if (state.isAir() && blockEntityData == null) {
                    continue;
                }

                blocks.add(new PlacedBlock(relativePos, state, blockEntityData == null ? null : blockEntityData.copy()));
            }

            appendLitematicEntities(entities, region, globalMinX, globalMinY, globalMinZ);
        }

        return buildStructureTag(totalSizeX, totalSizeY, totalSizeZ, blocks, entities);
    }

    private static List<BlockState> parseLitematicPalette(Level level, ListTag paletteTag) {
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag blockStateTag = paletteTag.getCompound(i);
            palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), blockStateTag));
        }
        return palette;
    }

    private static Map<Integer, BlockState> parseSpongePalette(Level level, CompoundTag paletteTag) {
        Map<Integer, BlockState> paletteById = new HashMap<>();
        for (String paletteEntry : paletteTag.getAllKeys()) {
            int id = paletteTag.getInt(paletteEntry);
            paletteById.put(id, parseSpongeBlockState(level, paletteEntry));
        }
        return paletteById;
    }

    private static BlockState parseSpongeBlockState(Level level, String serializedState) {
        try {
            return BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), serializedState, false).blockState();
        } catch (CommandSyntaxException ignored) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    private static Map<BlockPos, CompoundTag> parseSpongeBlockEntities(ListTag blockEntities) {
        Map<BlockPos, CompoundTag> byPos = new HashMap<>();
        for (int i = 0; i < blockEntities.size(); i++) {
            CompoundTag tag = blockEntities.getCompound(i);
            BlockPos pos = readPosArrayOrCoordinates(tag, "Pos");
            if (pos == null) {
                continue;
            }

            CompoundTag data;
            if (tag.contains("Data", 10)) {
                data = tag.getCompound("Data").copy();
                if (tag.contains("Id", 8) && !data.contains("id", 8)) {
                    data.putString("id", tag.getString("Id"));
                }
            } else if (tag.contains("TileNBT", 10)) {
                data = tag.getCompound("TileNBT").copy();
            } else {
                data = tag.copy();
                if (data.contains("Id", 8) && !data.contains("id", 8)) {
                    data.putString("id", data.getString("Id"));
                }
                data.remove("Id");
                data.remove("ContentVersion");
            }

            removeCoordinateFields(data);
            byPos.put(pos, data);
        }
        return byPos;
    }

    private static ListTag parseSpongeEntities(ListTag entitiesTag) {
        ListTag entities = new ListTag();
        for (int i = 0; i < entitiesTag.size(); i++) {
            CompoundTag tag = entitiesTag.getCompound(i);

            CompoundTag entityData;
            ListTag posList;
            if (tag.contains("Data", 10)) {
                entityData = tag.getCompound("Data").copy();
                posList = tag.getList("Pos", 6);
            } else if (tag.contains("EntityData", 10)) {
                entityData = tag.getCompound("EntityData").copy();
                posList = tag.getList("Pos", 6);
            } else {
                entityData = tag.copy();
                posList = entityData.getList("Pos", 6);
                if (entityData.contains("Id", 8) && !entityData.contains("id", 8)) {
                    entityData.putString("id", entityData.getString("Id"));
                }
                entityData.remove("Id");
                entityData.remove("ContentVersion");
            }

            if (posList.size() < 3) {
                continue;
            }

            double x = posList.getDouble(0);
            double y = posList.getDouble(1);
            double z = posList.getDouble(2);

            CompoundTag out = new CompoundTag();
            out.put("pos", doubleList(x, y, z));
            out.put("blockPos", intList(floorToInt(x), floorToInt(y), floorToInt(z)));
            out.put("nbt", entityData);
            entities.add(out);
        }
        return entities;
    }

    private static Map<BlockPos, CompoundTag> parseLitematicBlockEntities(
        LitematicRegion region,
        int globalMinX,
        int globalMinY,
        int globalMinZ
    ) {
        Map<BlockPos, CompoundTag> byPos = new HashMap<>();
        ListTag tagList = region.tag().getList("TileEntities", 10);
        for (int i = 0; i < tagList.size(); i++) {
            CompoundTag original = tagList.getCompound(i);

            CompoundTag blockEntityData;
            BlockPos localPos;
            if (original.contains("TileNBT", 10)) {
                blockEntityData = original.getCompound("TileNBT").copy();
                localPos = readPosArrayOrCoordinates(original, "Pos");
            } else {
                blockEntityData = original.copy();
                localPos = readPosArrayOrCoordinates(blockEntityData, "Pos");
            }

            if (localPos == null) {
                continue;
            }

            int worldX = region.sizeX() >= 0 ? region.positionX() + localPos.getX() : region.positionX() - localPos.getX();
            int worldY = region.sizeY() >= 0 ? region.positionY() + localPos.getY() : region.positionY() - localPos.getY();
            int worldZ = region.sizeZ() >= 0 ? region.positionZ() + localPos.getZ() : region.positionZ() - localPos.getZ();

            BlockPos relative = new BlockPos(worldX - globalMinX, worldY - globalMinY, worldZ - globalMinZ);
            removeCoordinateFields(blockEntityData);
            byPos.put(relative, blockEntityData);
        }
        return byPos;
    }

    private static void appendLitematicEntities(
        ListTag out,
        LitematicRegion region,
        int globalMinX,
        int globalMinY,
        int globalMinZ
    ) {
        ListTag entities = region.tag().getList("Entities", 10);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag original = entities.getCompound(i);

            CompoundTag entityData;
            ListTag localPosList;
            if (original.contains("EntityData", 10)) {
                entityData = original.getCompound("EntityData").copy();
                localPosList = original.getList("Pos", 6);
            } else {
                entityData = original.copy();
                localPosList = entityData.getList("Pos", 6);
            }

            if (localPosList.size() < 3) {
                continue;
            }

            double localX = localPosList.getDouble(0);
            double localY = localPosList.getDouble(1);
            double localZ = localPosList.getDouble(2);

            double worldX = region.sizeX() >= 0 ? region.positionX() + localX : region.positionX() - localX;
            double worldY = region.sizeY() >= 0 ? region.positionY() + localY : region.positionY() - localY;
            double worldZ = region.sizeZ() >= 0 ? region.positionZ() + localZ : region.positionZ() - localZ;

            double relX = worldX - globalMinX;
            double relY = worldY - globalMinY;
            double relZ = worldZ - globalMinZ;

            CompoundTag entityOut = new CompoundTag();
            entityOut.put("pos", doubleList(relX, relY, relZ));
            entityOut.put("blockPos", intList(floorToInt(relX), floorToInt(relY), floorToInt(relZ)));
            entityOut.put("nbt", entityData);
            out.add(entityOut);
        }
    }

    private static CompoundTag buildStructureTag(
        int sizeX,
        int sizeY,
        int sizeZ,
        List<PlacedBlock> blocks,
        ListTag entities
    ) {
        ListTag palette = new ListTag();
        ListTag blockList = new ListTag();
        Map<BlockState, Integer> paletteIndices = new HashMap<>();

        for (PlacedBlock placed : blocks) {
            Integer paletteIndex = paletteIndices.get(placed.state());
            if (paletteIndex == null) {
                paletteIndex = palette.size();
                paletteIndices.put(placed.state(), paletteIndex);
                palette.add(NbtUtils.writeBlockState(placed.state()));
            }

            CompoundTag blockTag = new CompoundTag();
            blockTag.put("pos", intList(placed.pos().getX(), placed.pos().getY(), placed.pos().getZ()));
            blockTag.putInt("state", paletteIndex);
            if (placed.blockEntityNbt() != null) {
                blockTag.put("nbt", placed.blockEntityNbt().copy());
            }
            blockList.add(blockTag);
        }

        if (palette.isEmpty()) {
            palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        }

        CompoundTag structure = new CompoundTag();
        structure.put("size", intList(sizeX, sizeY, sizeZ));
        structure.put("palette", palette);
        structure.put("blocks", blockList);
        structure.put("entities", entities);
        return structure;
    }

    private static CompoundTag emptyStructureTag() {
        return buildStructureTag(0, 0, 0, new ArrayList<>(), new ListTag());
    }

    private static int[] decodeVarInts(byte[] bytes, int maxEntries) {
        int[] values = new int[maxEntries];
        int valueCount = 0;
        int value = 0;
        int bitPosition = 0;

        for (byte current : bytes) {
            value |= (current & 0x7F) << bitPosition;
            if ((current & 0x80) == 0) {
                if (valueCount < maxEntries) {
                    values[valueCount] = value;
                }
                valueCount++;
                if (valueCount >= maxEntries) {
                    break;
                }
                value = 0;
                bitPosition = 0;
            } else {
                bitPosition += 7;
                if (bitPosition > 35) {
                    value = 0;
                    bitPosition = 0;
                }
            }
        }

        if (valueCount == maxEntries) {
            return values;
        }

        int[] truncated = new int[Math.max(0, valueCount)];
        if (valueCount > 0) {
            System.arraycopy(values, 0, truncated, 0, valueCount);
        }
        return truncated;
    }

    private static int readPackedInt(long[] data, int bits, int index) {
        if (bits <= 0 || data.length == 0) {
            return 0;
        }

        long startBit = (long) index * bits;
        int startLong = (int) (startBit >>> 6);
        if (startLong >= data.length) {
            return 0;
        }

        int endLong = (int) ((((long) index + 1L) * bits - 1L) >>> 6);
        int bitOffset = (int) (startBit & 63L);
        long mask = (1L << bits) - 1L;

        if (startLong == endLong || endLong >= data.length) {
            return (int) ((data[startLong] >>> bitOffset) & mask);
        }

        int split = 64 - bitOffset;
        long value = (data[startLong] >>> bitOffset) | (data[endLong] << split);
        return (int) (value & mask);
    }

    private static int getRequiredBits(int paletteSize) {
        if (paletteSize <= 1) {
            return 2;
        }
        return Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    private static int getPositiveDimension(CompoundTag tag, String key) {
        int value = tag.getInt(key);
        if (value == 0 && tag.contains(key, 2)) {
            value = tag.getShort(key);
        }
        return value;
    }

    @Nullable
    private static BlockPos readNamedPos(CompoundTag parent, String key) {
        if (parent.contains(key, 10)) {
            CompoundTag compound = parent.getCompound(key);
            BlockPos nested = readPosArrayOrCoordinates(compound, key);
            if (nested != null) {
                return nested;
            }
            return readBlockPosFromCompound(compound);
        }
        if (parent.contains(key, 11)) {
            int[] values = parent.getIntArray(key);
            if (values.length >= 3) {
                return new BlockPos(values[0], values[1], values[2]);
            }
        }
        if (parent.contains(key, 9)) {
            BlockPos listPos = readNumericPositionList(parent.get(key));
            if (listPos != null) {
                return listPos;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos readBlockPosFromCompound(CompoundTag tag) {
        if (tag.contains("X", 99) && tag.contains("Y", 99) && tag.contains("Z", 99)) {
            return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }
        if (tag.contains("x", 99) && tag.contains("y", 99) && tag.contains("z", 99)) {
            return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        }
        return null;
    }

    @Nullable
    private static BlockPos readPosArrayOrCoordinates(CompoundTag tag, String posKey) {
        if (!posKey.isEmpty()) {
            if (tag.contains(posKey, 10)) {
                BlockPos nested = readBlockPosFromCompound(tag.getCompound(posKey));
                if (nested != null) {
                    return nested;
                }
            }
            if (tag.contains(posKey, 11)) {
                int[] values = tag.getIntArray(posKey);
                if (values.length >= 3) {
                    return new BlockPos(values[0], values[1], values[2]);
                }
            }
            if (tag.contains(posKey, 9)) {
                BlockPos listPos = readNumericPositionList(tag.get(posKey));
                if (listPos != null) {
                    return listPos;
                }
            }
        }
        if (tag.contains("x", 99) && tag.contains("y", 99) && tag.contains("z", 99)) {
            return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        }
        if (tag.contains("X", 99) && tag.contains("Y", 99) && tag.contains("Z", 99)) {
            return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }
        return null;
    }

    @Nullable
    private static BlockPos readNumericPositionList(@Nullable Tag tag) {
        if (!(tag instanceof ListTag list) || list.size() < 3) {
            return null;
        }

        Tag xTag = list.get(0);
        Tag yTag = list.get(1);
        Tag zTag = list.get(2);
        if (!(xTag instanceof NumericTag xNumeric)
            || !(yTag instanceof NumericTag yNumeric)
            || !(zTag instanceof NumericTag zNumeric)) {
            return null;
        }

        return new BlockPos(xNumeric.getAsInt(), yNumeric.getAsInt(), zNumeric.getAsInt());
    }

    private static void removeCoordinateFields(CompoundTag tag) {
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        tag.remove("X");
        tag.remove("Y");
        tag.remove("Z");
        tag.remove("Pos");
    }

    private static ListTag intList(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }

    private static ListTag doubleList(double x, double y, double z) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(x));
        list.add(DoubleTag.valueOf(y));
        list.add(DoubleTag.valueOf(z));
        return list;
    }

    private static int floorToInt(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private record PlacedBlock(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityNbt) {
    }

    private record LitematicRegion(
        CompoundTag tag,
        int positionX,
        int positionY,
        int positionZ,
        int sizeX,
        int sizeY,
        int sizeZ,
        int absSizeX,
        int absSizeY,
        int absSizeZ,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        @Nullable
        private static LitematicRegion of(CompoundTag tag, BlockPos position, BlockPos size) {
            int sx = size.getX();
            int sy = size.getY();
            int sz = size.getZ();
            int ax = Math.abs(sx);
            int ay = Math.abs(sy);
            int az = Math.abs(sz);
            if (ax <= 0 || ay <= 0 || az <= 0) {
                return null;
            }

            int px = position.getX();
            int py = position.getY();
            int pz = position.getZ();

            int minX = sx >= 0 ? px : px + sx + 1;
            int minY = sy >= 0 ? py : py + sy + 1;
            int minZ = sz >= 0 ? pz : pz + sz + 1;
            int maxX = sx >= 0 ? px + sx - 1 : px;
            int maxY = sy >= 0 ? py + sy - 1 : py;
            int maxZ = sz >= 0 ? pz + sz - 1 : pz;

            return new LitematicRegion(
                tag,
                px, py, pz,
                sx, sy, sz,
                ax, ay, az,
                minX, minY, minZ,
                maxX, maxY, maxZ
            );
        }
    }
}
