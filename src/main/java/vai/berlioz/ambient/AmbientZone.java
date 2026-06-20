package vai.berlioz.ambient;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Ambient music zone dataclass
 */
public final class AmbientZone {
    private final UUID id;
    private final String name;
    private final UUID world;
    private final int x1;
    private final int y1;
    private final int z1;
    private final int x2;
    private final int y2;
    private final int z2;
    private final List<String> urls;
    private final int minx;
    private final int maxx;
    private final int miny;
    private final int maxy;
    private final int minz;
    private final int maxz;

    public AmbientZone(UUID id, String name, UUID world, int x1, int y1, int z1, int x2, int y2, int z2, List<String> urls) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.minx = Math.min(x1, x2);
        this.maxx = Math.max(x1, x2);
        this.miny = Math.min(y1, y2);
        this.maxy = Math.max(y1, y2);
        this.minz = Math.min(z1, z2);
        this.maxz = Math.max(z1, z2);
        this.urls = List.copyOf(urls);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID world() {
        return world;
    }

    public int x1() {
        return x1;
    }

    public int y1() {
        return y1;
    }

    public int z1() {
        return z1;
    }

    public int x2() {
        return x2;
    }

    public int y2() {
        return y2;
    }

    public int z2() {
        return z2;
    }

    public List<String> urls() {
        return urls;
    }

    public boolean contains(Location loc) {
        World w = loc.getWorld();
        if (w == null || !w.getUID().equals(world)) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= minx && bx <= maxx && by >= miny && by <= maxy && bz >= minz && bz <= maxz;
    }

    public double centerX() {
        return (minx + maxx) / 2.0 + 0.5;
    }

    public double centerY() {
        return (miny + maxy) / 2.0 + 0.5;
    }

    public double centerZ() {
        return (minz + maxz) / 2.0 + 0.5;
    }

    /** Squared distance from loc to the box center, or max_double if another world. (we don't sqrt, it's too long) */
    public double centerDistanceSq(Location loc) {
        World w = loc.getWorld();
        if (w == null || !w.getUID().equals(world)) return Double.MAX_VALUE;
        double dx = loc.getX() - centerX();
        double dy = loc.getY() - centerY();
        double dz = loc.getZ() - centerZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
