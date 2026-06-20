package vai.berlioz.ambient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Stores AmbientZones in zones.yml. */
public final class AmbientZoneStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, AmbientZone> zones = new HashMap<>();

    /** Creates the store for plugin and loads any persisted zones. */
    public AmbientZoneStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("zones");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID id = UUID.fromString(key);
                UUID world = UUID.fromString(Objects.requireNonNullElse(s.getString("world"), ""));
                String name = Objects.requireNonNullElse(s.getString("name"), "");
                AmbientZone zone = new AmbientZone(id, name, world,
                        s.getInt("x1"), s.getInt("y1"), s.getInt("z1"),
                        s.getInt("x2"), s.getInt("y2"), s.getInt("z2"),
                        s.getStringList("urls"));
                zones.put(id, zone);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping malformed ambient zone: " + key);
            }
        }
    }

    /**
     * Writes all zones to disk, creating the plugin folder if needed.
     * I think it's ok to do it in the main thread, as saving a new zone is a rare occurrence.
     * Btw it's ugly, but I think it's faster
     */
    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (AmbientZone z : zones.values()) {
            String base = "zones." + z.id();
            yaml.set(base + ".name", z.name());
            yaml.set(base + ".world", z.world().toString());
            yaml.set(base + ".x1", z.x1());
            yaml.set(base + ".y1", z.y1());
            yaml.set(base + ".z1", z.z1());
            yaml.set(base + ".x2", z.x2());
            yaml.set(base + ".y2", z.y2());
            yaml.set(base + ".z2", z.z2());
            yaml.set(base + ".urls", new ArrayList<>(z.urls()));
        }
        try {
            File dir = plugin.getDataFolder();
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("Could not create plugin folder for zones.yml");
            }
            backup();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save zones.yml: " + e.getMessage());
        }
    }

    /** Copies the current zones.yml to zones.yml.bak,nothing is lost if save fails. */
    private void backup() {
        if (!file.exists()) return;
        File bak = new File(file.getParentFile(), file.getName() + ".bak");
        try {
            Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not back up zones.yml: " + e.getMessage());
        }
    }

    public void put(AmbientZone zone) {
        zones.put(zone.id(), zone);
        save();
    }

    public void remove(UUID id) {
        if (zones.remove(id) != null) save();
    }

    public AmbientZone get(UUID id) {
        return zones.get(id);
    }

    /**
     * Zones in loc's world whose center is within radius blocks, nearest first.
     * Is it too heavy ?
     */
    public List<AmbientZone> nearby(Location loc, double radius) {
        double r2 = radius * radius;
        List<AmbientZone> out = new ArrayList<>();
        for (AmbientZone z : zones.values()) {
            if (z.centerDistanceSq(loc) <= r2) out.add(z);
        }
        out.sort(Comparator.comparingDouble(a -> a.centerDistanceSq(loc)));
        return out;
    }

    /** The zone with loc with nearest center, or null. */
    public AmbientZone zoneAt(Location loc) {
        AmbientZone best = null;
        double bestSq = Double.MAX_VALUE;
        for (AmbientZone z : zones.values()) {
            if (z.contains(loc)) {
                double sq = z.centerDistanceSq(loc);
                if (sq < bestSq) {
                    bestSq = sq;
                    best = z;
                }
            }
        }
        return best;
    }
}
