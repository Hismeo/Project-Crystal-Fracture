package org.hismeo.fractureclient.client.room;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * An explicitly authored room: a block-column footprint extruded from the walkable floor to the
 * ceiling block. The footprint can be concave because it is stored as individual X/Z columns.
 */
public final class RoomRegion {
    public static final int MAX_COLUMNS = 32_768;
    public static final int MAX_HEIGHT = 64;

    private final UUID id;
    private final String contextKey;
    private final String dimensionKey;
    private String name;
    private int floorY;
    private int ceilingY;
    private final LongOpenHashSet columns;

    private boolean boundsDirty = true;
    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;

    public RoomRegion(
            UUID id,
            String name,
            String contextKey,
            String dimensionKey,
            int floorY,
            int ceilingY
    ) {
        this(id, name, contextKey, dimensionKey, floorY, ceilingY, new LongOpenHashSet());
    }

    private RoomRegion(
            UUID id,
            String name,
            String contextKey,
            String dimensionKey,
            int floorY,
            int ceilingY,
            LongOpenHashSet columns
    ) {
        this.id = id;
        this.name = name;
        this.contextKey = contextKey;
        this.dimensionKey = dimensionKey;
        this.floorY = floorY;
        this.ceilingY = ceilingY;
        this.columns = columns;
    }

    public RoomRegion copy() {
        return new RoomRegion(
                id,
                name,
                contextKey,
                dimensionKey,
                floorY,
                ceilingY,
                new LongOpenHashSet(columns)
        );
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String contextKey() {
        return contextKey;
    }

    public String dimensionKey() {
        return dimensionKey;
    }

    /** Y of the first walkable air block, inclusive. */
    public int floorY() {
        return floorY;
    }

    /** Upper Y used while resolving a flat or sloped roof, exclusive for player containment. */
    public int ceilingY() {
        return ceilingY;
    }

    public boolean setHeights(int floorY, int ceilingY) {
        if (ceilingY <= floorY || ceilingY - floorY > MAX_HEIGHT) {
            return false;
        }
        this.floorY = floorY;
        this.ceilingY = ceilingY;
        return true;
    }

    public int columnCount() {
        return columns.size();
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    public boolean contains(int x, int y, int z) {
        return y >= floorY && y < ceilingY && containsColumn(x, z);
    }

    public boolean containsColumn(int x, int z) {
        return columns.contains(packColumn(x, z));
    }

    /** Returns a read-only-by-convention iterator used by render/cull caches. */
    public LongIterator columnIterator() {
        return columns.iterator();
    }

    /** Adds an inclusive rectangle. Returns false without mutation when it exceeds the safety cap. */
    public boolean addRectangle(int firstX, int firstZ, int secondX, int secondZ) {
        int rectangleMinX = Math.min(firstX, secondX);
        int rectangleMaxX = Math.max(firstX, secondX);
        int rectangleMinZ = Math.min(firstZ, secondZ);
        int rectangleMaxZ = Math.max(firstZ, secondZ);
        long area = ((long)rectangleMaxX - rectangleMinX + 1L)
                * ((long)rectangleMaxZ - rectangleMinZ + 1L);
        if (area <= 0L || area > MAX_COLUMNS) {
            return false;
        }

        LongArrayList added = new LongArrayList();
        for (int z = rectangleMinZ; z <= rectangleMaxZ; z++) {
            for (int x = rectangleMinX; x <= rectangleMaxX; x++) {
                long packed = packColumn(x, z);
                if (columns.add(packed)) {
                    added.add(packed);
                    if (columns.size() > MAX_COLUMNS) {
                        LongIterator iterator = added.iterator();
                        while (iterator.hasNext()) {
                            columns.remove(iterator.nextLong());
                        }
                        return false;
                    }
                }
            }
        }
        if (!added.isEmpty()) {
            boundsDirty = true;
        }
        return true;
    }

    public void removeRectangle(int firstX, int firstZ, int secondX, int secondZ) {
        int rectangleMinX = Math.min(firstX, secondX);
        int rectangleMaxX = Math.max(firstX, secondX);
        int rectangleMinZ = Math.min(firstZ, secondZ);
        int rectangleMaxZ = Math.max(firstZ, secondZ);
        for (int z = rectangleMinZ; z <= rectangleMaxZ; z++) {
            for (int x = rectangleMinX; x <= rectangleMaxX; x++) {
                columns.remove(packColumn(x, z));
            }
        }
        boundsDirty = true;
    }

    /** Rejects accidental islands that would make concave-camera framing ambiguous. */
    public boolean isConnected() {
        if (columns.isEmpty()) {
            return false;
        }
        LongOpenHashSet remaining = new LongOpenHashSet(columns);
        LongArrayList queue = new LongArrayList(columns.size());
        long first = remaining.iterator().nextLong();
        remaining.remove(first);
        queue.add(first);
        for (int head = 0; head < queue.size(); head++) {
            long packed = queue.getLong(head);
            int x = unpackX(packed);
            int z = unpackZ(packed);
            enqueueConnected(remaining, queue, x - 1, z);
            enqueueConnected(remaining, queue, x + 1, z);
            enqueueConnected(remaining, queue, x, z - 1);
            enqueueConnected(remaining, queue, x, z + 1);
        }
        return remaining.isEmpty();
    }

    public int minX() {
        updateBounds();
        return minX;
    }

    public int maxX() {
        updateBounds();
        return maxX;
    }

    public int minZ() {
        updateBounds();
        return minZ;
    }

    public int maxZ() {
        updateBounds();
        return maxZ;
    }

    public long volumeScore() {
        return (long)columns.size() * Math.max(1, ceilingY - floorY);
    }

    /** Keeps a concave-room camera target on a real selected column. */
    public HorizontalPoint closestInteriorPoint(double targetX, double targetZ) {
        if (columns.isEmpty()) {
            return new HorizontalPoint(targetX, targetZ);
        }
        int targetCellX = floorToInt(targetX);
        int targetCellZ = floorToInt(targetZ);
        if (containsColumn(targetCellX, targetCellZ)) {
            return new HorizontalPoint(
                    clamp(targetX, targetCellX + 0.08, targetCellX + 0.92),
                    clamp(targetZ, targetCellZ + 0.08, targetCellZ + 0.92)
            );
        }

        double bestDistance = Double.POSITIVE_INFINITY;
        double bestX = targetX;
        double bestZ = targetZ;
        LongIterator iterator = columns.iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            double x = unpackX(packed) + 0.5;
            double z = unpackZ(packed) + 0.5;
            double dx = x - targetX;
            double dz = z - targetZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestX = x;
                bestZ = z;
            }
        }
        return new HorizontalPoint(bestX, bestZ);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("name", name);
        json.addProperty("context", contextKey);
        json.addProperty("dimension", dimensionKey);
        json.addProperty("floor_y", floorY);
        json.addProperty("ceiling_y", ceilingY);

        TreeMap<Integer, IntArrayList> rows = new TreeMap<>();
        LongIterator iterator = columns.iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            rows.computeIfAbsent(unpackZ(packed), ignored -> new IntArrayList())
                    .add(unpackX(packed));
        }

        JsonArray rowArray = new JsonArray();
        for (Map.Entry<Integer, IntArrayList> entry : rows.entrySet()) {
            IntArrayList xs = entry.getValue();
            xs.sort(Integer::compare);
            JsonObject row = new JsonObject();
            row.addProperty("z", entry.getKey());
            JsonArray ranges = new JsonArray();
            int rangeStart = xs.getInt(0);
            int rangeEnd = rangeStart;
            for (int index = 1; index < xs.size(); index++) {
                int x = xs.getInt(index);
                if (x == rangeEnd + 1) {
                    rangeEnd = x;
                    continue;
                }
                ranges.add(rangeStart);
                ranges.add(rangeEnd);
                rangeStart = rangeEnd = x;
            }
            ranges.add(rangeStart);
            ranges.add(rangeEnd);
            row.add("ranges", ranges);
            rowArray.add(row);
        }
        json.add("rows", rowArray);
        return json;
    }

    public static RoomRegion fromJson(JsonObject json) {
        UUID id = UUID.fromString(requiredString(json, "id"));
        String name = requiredString(json, "name");
        String context = requiredString(json, "context");
        String dimension = requiredString(json, "dimension");
        int floorY = json.get("floor_y").getAsInt();
        int ceilingY = json.get("ceiling_y").getAsInt();
        if (ceilingY <= floorY || ceilingY - floorY > MAX_HEIGHT) {
            throw new IllegalArgumentException("Invalid room height");
        }

        RoomRegion room = new RoomRegion(id, name, context, dimension, floorY, ceilingY);
        JsonArray rows = json.getAsJsonArray("rows");
        if (rows == null) {
            throw new IllegalArgumentException("Missing room rows");
        }
        for (JsonElement rowElement : rows) {
            JsonObject row = rowElement.getAsJsonObject();
            int z = row.get("z").getAsInt();
            JsonArray ranges = row.getAsJsonArray("ranges");
            if (ranges == null || (ranges.size() & 1) != 0) {
                throw new IllegalArgumentException("Invalid room row ranges");
            }
            for (int index = 0; index < ranges.size(); index += 2) {
                int startX = ranges.get(index).getAsInt();
                int endX = ranges.get(index + 1).getAsInt();
                if (endX < startX || (long)endX - startX + 1L > MAX_COLUMNS) {
                    throw new IllegalArgumentException("Invalid room row range");
                }
                for (int x = startX; x <= endX; x++) {
                    room.columns.add(packColumn(x, z));
                    if (room.columns.size() > MAX_COLUMNS) {
                        throw new IllegalArgumentException("Room footprint exceeds safety cap");
                    }
                }
            }
        }
        room.boundsDirty = true;
        return room;
    }

    public static long packColumn(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    public static int unpackX(long packed) {
        return ChunkPos.getX(packed);
    }

    public static int unpackZ(long packed) {
        return ChunkPos.getZ(packed);
    }

    private static void enqueueConnected(
            LongOpenHashSet remaining,
            LongArrayList queue,
            int x,
            int z
    ) {
        long packed = packColumn(x, z);
        if (remaining.remove(packed)) {
            queue.add(packed);
        }
    }

    private void updateBounds() {
        if (!boundsDirty) {
            return;
        }
        if (columns.isEmpty()) {
            minX = maxX = minZ = maxZ = 0;
            boundsDirty = false;
            return;
        }
        minX = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        minZ = Integer.MAX_VALUE;
        maxZ = Integer.MIN_VALUE;
        LongIterator iterator = columns.iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            int x = unpackX(packed);
            int z = unpackZ(packed);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        boundsDirty = false;
    }

    private static String requiredString(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing room field: " + key);
        }
        return value.getAsString();
    }

    private static int floorToInt(double value) {
        int integer = (int)value;
        return value < integer ? integer - 1 : integer;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record HorizontalPoint(double x, double z) {
    }
}
