package org.hismeo.fractureclient.client.room;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import org.hismeo.fractureclient.FractureClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Client-local persistence for explicitly authored room footprints. */
public final class RoomRegionStore {
    private static final int DATA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<RoomRegion> ROOMS = new ArrayList<>();

    private static boolean loaded;

    private RoomRegionStore() {
    }

    public static RoomRegion findContaining(Minecraft minecraft, BlockPos pos) {
        ensureLoaded();
        String context = contextKey(minecraft);
        String dimension = dimensionKey(minecraft.level);
        return ROOMS.stream()
                .filter(room -> room.contextKey().equals(context))
                .filter(room -> room.dimensionKey().equals(dimension))
                .filter(room -> room.contains(pos.getX(), pos.getY(), pos.getZ()))
                .min(Comparator.comparingLong(RoomRegion::volumeScore))
                .orElse(null);
    }

    public static List<RoomRegion> roomsForCurrentWorld(Minecraft minecraft) {
        ensureLoaded();
        String context = contextKey(minecraft);
        String dimension = dimensionKey(minecraft.level);
        List<RoomRegion> result = new ArrayList<>();
        for (RoomRegion room : ROOMS) {
            if (room.contextKey().equals(context) && room.dimensionKey().equals(dimension)) {
                result.add(room);
            }
        }
        result.sort(Comparator.comparing(RoomRegion::name));
        return List.copyOf(result);
    }

    public static void upsert(RoomRegion room) {
        ensureLoaded();
        for (int index = 0; index < ROOMS.size(); index++) {
            if (ROOMS.get(index).id().equals(room.id())) {
                ROOMS.set(index, room.copy());
                return;
            }
        }
        ROOMS.add(room.copy());
    }

    public static RoomRegion removeContaining(Minecraft minecraft, BlockPos pos) {
        RoomRegion room = findContaining(minecraft, pos);
        if (room != null) {
            ROOMS.removeIf(candidate -> candidate.id().equals(room.id()));
        }
        return room;
    }

    public static String nextRoomName(Minecraft minecraft) {
        ensureLoaded();
        int next = 1;
        List<String> names = roomsForCurrentWorld(minecraft).stream()
                .map(RoomRegion::name)
                .toList();
        while (names.contains("room_" + next)) {
            next++;
        }
        return "room_" + next;
    }

    public static boolean save() {
        ensureLoaded();
        JsonObject root = new JsonObject();
        root.addProperty("version", DATA_VERSION);
        JsonArray rooms = new JsonArray();
        for (RoomRegion room : ROOMS) {
            rooms.add(room.toJson());
        }
        root.add("rooms", rooms);

        Path path = dataPath();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8
            )) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            FractureClient.LOGGER.error("Failed to save explicit room regions to {}", path, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The next successful save replaces this deterministic temporary file.
            }
            return false;
        }
    }

    public static void reload() {
        loaded = false;
        ROOMS.clear();
        ensureLoaded();
    }

    public static String contextKey(Minecraft minecraft) {
        var integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer != null) {
            try {
                return "singleplayer:"
                        + integratedServer.getWorldPath(LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize();
            } catch (RuntimeException exception) {
                return "singleplayer:"
                        + integratedServer.getWorldData().getLevelName().toLowerCase(Locale.ROOT);
            }
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server:" + server.ip.trim().toLowerCase(Locale.ROOT);
        }
        return "session:unknown";
    }

    public static String dimensionKey(ClientLevel level) {
        return level == null ? "minecraft:overworld" : level.dimension().location().toString();
    }

    public static RoomRegion createRoom(
            Minecraft minecraft,
            String name,
            int floorY,
            int ceilingY
    ) {
        return new RoomRegion(
                UUID.randomUUID(),
                name,
                contextKey(minecraft),
                dimensionKey(minecraft.level),
                floorY,
                ceilingY
        );
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = dataPath();
        if (!Files.isRegularFile(path)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Room region root is not an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonArray rooms = root.getAsJsonArray("rooms");
            if (rooms == null) {
                return;
            }
            for (JsonElement element : rooms) {
                try {
                    ROOMS.add(RoomRegion.fromJson(element.getAsJsonObject()));
                } catch (RuntimeException exception) {
                    FractureClient.LOGGER.warn("Skipping malformed explicit room entry", exception);
                }
            }
            FractureClient.LOGGER.info("Loaded {} explicit room regions from {}", ROOMS.size(), path);
        } catch (IOException | RuntimeException exception) {
            ROOMS.clear();
            FractureClient.LOGGER.error("Failed to load explicit room regions from {}", path, exception);
        }
    }

    private static Path dataPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(FractureClient.MODID)
                .resolve("room_regions.json");
    }
}
