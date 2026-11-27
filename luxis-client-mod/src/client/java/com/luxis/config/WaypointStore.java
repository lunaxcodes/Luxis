package com.luxis.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Local coordinate notes, never announced or sent to a server. */
public final class WaypointStore {
    public static final int LIMIT = 250;
    public record Waypoint(String id, String scope, String name, String dimension, int x, int y, int z) {
        public String coordinates() { return x + ", " + y + ", " + z; }
    }

    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static Path file;

    private WaypointStore() {}

    public static void init(Path luxisDirectory) {
        file = luxisDirectory.resolve("waypoints.json");
        Waypoint[] saved = GuiConfig.read(file, Waypoint[].class);
        WAYPOINTS.clear();
        if (saved == null) return;
        for (Waypoint place : saved) {
            if (WAYPOINTS.size() == LIMIT) break;
            if (place != null && place.id != null && place.scope != null && place.name != null && place.dimension != null) {
                WAYPOINTS.add(place);
            }
        }
    }

    public static String scopeKey(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static List<Waypoint> forWorld(String scope) {
        return WAYPOINTS.stream().filter(place -> place.scope.equals(scope)).toList();
    }

    public static boolean add(String scope, String name, String dimension, int x, int y, int z) {
        if (WAYPOINTS.size() >= LIMIT) return false;
        String text = name.strip();
        if (text.isEmpty()) text = "Marker " + x + ", " + z;
        WAYPOINTS.addFirst(new Waypoint(UUID.randomUUID().toString(), scope, text, dimension, x, y, z));
        save();
        return true;
    }

    public static boolean remove(String scope, String id) {
        boolean deleted = WAYPOINTS.removeIf(place -> place.id.equals(id) && place.scope.equals(scope));
        if (deleted) save();
        return deleted;
    }

    private static void save() {
        if (file != null) GuiConfig.write(file, WAYPOINTS);
    }
}
