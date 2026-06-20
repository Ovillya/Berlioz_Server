package vai.berlioz.network;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import vai.berlioz.ambient.AmbientZone;
import vai.berlioz.ambient.AmbientZoneStore;

public final class BroadcastManager implements Listener {
    private static final int TICK_PERIOD = 10;
    private static final int MAX_RADIUS = 256;
    private static final byte[] EMPTY = new byte[0];
    private static final byte KIND_DJ = 0x0;
    private static final byte KIND_AMBIENT = 0x1;
    private static final byte KIND_SERVER = 0x2;
    private static final int MAX_RESYNCS_PER_TRACK = 3;
    private static final int APPROACH_MARGIN = 20;
    private static final long MIN_SERVER_LOOP_MS = 5000L;
    private static final long SERVER_SOURCE_MAX_AGE_MS = 8L * 3600L * 1000L;
    private static final int PRUNE_EVERY_TICKS = 20;

    private static final class Source {
        private UUID ownerId;
        private Location origin;
        private boolean server;
        private boolean commandBlock;
        private long createdMillis;
        private String url;
        private int radius;
        private boolean paused;
        private double baseOffset;
        private long startMillis;

        private double offset(long now) {
            return paused ? baseOffset : baseOffset + (now - startMillis) / 1000.0;
        }
    }

    private static final class Heard {
        private UUID source;
        private String url;
        private boolean isPaused;
        private boolean isAmbiant;
        private boolean isServer;
        // If both are false, then it's DJ. Maybe I should change that
        private int ambientIndex;
        private int resyncs;
    }

    private final JavaPlugin plugin;
    private final AmbientZoneStore zones;
    private final long graceMillis;
    private final Map<UUID, Source> sources = new HashMap<>();
    private final Map<UUID, Heard> heard = new HashMap<>();
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private final Map<UUID, Integer> permSent = new HashMap<>(); // bit 0: playlist, bit 1: ambient
    // Last track URL each approaching player was told to pre-cache, to avoid resend
    private final Map<UUID, String> approachCached = new HashMap<>();
    // Last track URL each modless player was sent a chat link for, to avoid resend
    private final Map<UUID, String> chatNotified = new HashMap<>();
    // Last time each player looped the server broadcast, to throttle a failing stream.
    private final Map<UUID, Long> lastServerLoop = new HashMap<>();
    private final Map<String, Source> serverSources = new HashMap<>();
    private int pruneCounter = 0; // ticks since the last server-source prune

    BroadcastManager(JavaPlugin plugin, AmbientZoneStore zones, long graceMillis) {
        this.plugin = plugin;
        this.zones = zones;
        this.graceMillis = graceMillis;
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    void startBroadcast(Player owner, int radius, String url) {
        Source s = new Source();
        s.ownerId = owner.getUniqueId();
        s.url = url;
        s.radius = Math.max(1, Math.min(radius, MAX_RADIUS));
        s.paused = false;
        s.baseOffset = 0;
        s.startMillis = System.currentTimeMillis();
        sources.put(s.ownerId, s);
        UUID ownerId = s.ownerId;
        heard.values().removeIf(h -> ownerId.equals(h.source) && !h.isAmbiant);
    }

    void pauseBroadcast(Player owner) {
        Source s = sources.get(owner.getUniqueId());
        if (s != null && !s.paused) {
            s.baseOffset = s.offset(System.currentTimeMillis());
            s.paused = true;
        }
    }

    void resumeBroadcast(Player owner) {
        Source s = sources.get(owner.getUniqueId());
        if (s != null && s.paused) {
            s.startMillis = System.currentTimeMillis();
            s.paused = false;
        }
    }

    void stopBroadcast(Player owner) {
        sources.remove(owner.getUniqueId());
    }

    public void startServerBroadcast(Location origin, int radius, String url, boolean commandBlock) {
        String key = serverKey(origin);
        Source s = new Source();
        s.ownerId = serverOwnerId(key);
        s.origin = origin.clone();
        s.server = true;
        s.commandBlock = commandBlock;
        s.createdMillis = System.currentTimeMillis();
        s.url = url;
        s.radius = Math.max(1, Math.min(radius, MAX_RADIUS));
        s.paused = false;
        s.baseOffset = 0;
        s.startMillis = System.currentTimeMillis();
        serverSources.put(key, s);
        UUID ownerId = s.ownerId;
        heard.entrySet().removeIf(e -> {
            if (e.getValue().isServer && ownerId.equals(e.getValue().source)) {
                lastServerLoop.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    public void stopServerBroadcast(Location near) {
        String bestKey = null;
        double bestSq = Double.MAX_VALUE;
        for (Map.Entry<String, Source> e : serverSources.entrySet()) {
            Source s = e.getValue();
            Location o = s.origin;
            if (o.getWorld() == null || near.getWorld() == null
                    || !o.getWorld().equals(near.getWorld())) {
                continue;
            }
            double distSq = o.distanceSquared(near);
            if (distSq <= (double) s.radius * s.radius && distSq < bestSq) {
                bestSq = distSq;
                bestKey = e.getKey();
            }
        }
        if (bestKey != null) {
            serverSources.remove(bestKey);
        }
    }


    private void pruneServerSources(long now) {
        serverSources.values().removeIf(s -> shouldRemoveServer(s, now));
    }

    private static boolean shouldRemoveServer(Source s, long now) {
        if (now - s.createdMillis > SERVER_SOURCE_MAX_AGE_MS) {
            return true;  // 8h backstop
        }
        return s.commandBlock && commandBlockGone(s.origin);
    }

    private static boolean commandBlockGone(Location origin) {
        var world = origin.getWorld();
        if (world == null) return false;
        if (!world.isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) return false;
        Material type = origin.getBlock().getType();
        return type != Material.COMMAND_BLOCK
                && type != Material.CHAIN_COMMAND_BLOCK
                && type != Material.REPEATING_COMMAND_BLOCK;
    }

    private static String serverKey(Location loc) {
        UUID world = loc.getWorld() != null ? loc.getWorld().getUID() : new UUID(0L, 0L);
        return world + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static UUID serverOwnerId(String key) {
        return UUID.nameUUIDFromBytes(("berlioz-server:" + key).getBytes(StandardCharsets.UTF_8));
    }

    void seekBroadcast(Player owner, float offsetSeconds) {
        Source s = sources.get(owner.getUniqueId());
        if (s == null) return;
        s.baseOffset = Math.max(0f, offsetSeconds);
        s.startMillis = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Heard> e : heard.entrySet()) {
            Heard h = e.getValue();
            if (!h.isAmbiant && s.ownerId.equals(h.source)) {
                Player listener = Bukkit.getPlayer(e.getKey());
                if (listener != null) {
                    h.resyncs = 0;
                    sendPlayOrder(listener, s, now);
                }
            }
        }
    }


    public void askNext(Player player) {
        UUID id = player.getUniqueId();
        Heard last = heard.get(id);
        if (last == null) return;
        if (last.isServer) {
            loopServer(player, last);
            return;
        }
        if (!last.isAmbiant) {
            resyncDjListener(player, last);
            return;
        }
        AmbientZone zone = zones.get(last.source);
        if (zone == null || zone.urls().isEmpty() || !zone.contains(player.getLocation())) return;
        sendAmbient(player, zone, last.ambientIndex + 1);
    }

    private void resyncDjListener(Player player, Heard last) {
        if (last.resyncs >= MAX_RESYNCS_PER_TRACK) return;
        Source dj = sources.get(last.source);
        if (dj == null || dj.paused) return;
        Player owner = Bukkit.getPlayer(dj.ownerId);
        if (owner == null || !owner.getWorld().equals(player.getWorld())) return;
        double distSq = owner.getLocation().distanceSquared(player.getLocation());
        if (distSq > (double) dj.radius * dj.radius) return;
        last.resyncs++;
        sendPlayOrder(player, dj, System.currentTimeMillis());
    }

    private void loopServer(Player player, Heard last) {
        Source s = serverSourceById(last.source);
        if (s == null || !s.url.equals(last.url) || !covers(s, player)) return;
        long now = System.currentTimeMillis();
        Long prev = lastServerLoop.get(player.getUniqueId());
        if (prev != null && now - prev < MIN_SERVER_LOOP_MS) return;
        lastServerLoop.put(player.getUniqueId(), now);
        sendPlayOrder(player, s.url, 0f, false, KIND_SERVER);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sources.remove(id);
        heard.remove(id);
        graceUntil.remove(id);
        permSent.remove(id);
        approachCached.remove(id);
        chatNotified.remove(id);
        lastServerLoop.remove(id);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (++pruneCounter >= PRUNE_EVERY_TICKS) {
            pruneCounter = 0;
            pruneServerSources(now);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean hasMod = hasBerlioz(p);
            if (hasMod) refreshPermission(p, id); // Must do it like that bc no real way to detect changes
            Source serverSrc = findServerSource(p);
            Source active = serverSrc != null ? serverSrc : nearestSource(p);
            if (active != null) {
                graceUntil.remove(id);
                approachCached.remove(id);
                if (!hasMod)
                    notifyChatLink(p, id, active.url);
                else if (active.server)
                    serveServer(p, id, active, now);
                else
                    serveDj(p, id, active, now);
            } else {
                chatNotified.remove(id);
                if (hasMod) {
                    serveAmbientOrSilence(p, id, now);
                    precacheApproaching(p, id);
                }
            }
        }
    }

    private void serveDj(Player p, UUID id, Source dj, long now) {
        Heard last = heard.get(id);
        if (last == null || last.isAmbiant || last.isServer
                || !last.source.equals(dj.ownerId) || !last.url.equals(dj.url)) {
            sendPlayOrder(p, dj, now);
            Heard h = new Heard();
            h.source = dj.ownerId;
            h.url = dj.url;
            h.isPaused = dj.paused;
            h.isAmbiant = false;
            h.isServer = false;
            heard.put(id, h);
        } else if (last.isPaused != dj.paused) {
            BerliozNetwork.send(p, dj.paused ? MessageType.PAUSE_ORDER : MessageType.RESUME_ORDER, EMPTY);
            last.isPaused = dj.paused;
        }
    }


    private void serveServer(Player p, UUID id, Source src, long now) {
        Heard last = heard.get(id);
        if (last == null || !last.isServer
                || !last.source.equals(src.ownerId) || !last.url.equals(src.url)) {
            sendPlayOrder(p, src, now);
            Heard h = new Heard();
            h.source = src.ownerId;
            h.url = src.url;
            h.isServer = true;
            heard.put(id, h);
        }
    }

    private void serveAmbientOrSilence(Player p, UUID id, long now) {
        Heard last = heard.get(id);
        if (last != null && !last.isAmbiant) {
            // Just lost a DJ or server broadcast: stop now and hold ambient back for the grace delay.
            BerliozNetwork.send(p, MessageType.STOP_ORDER, EMPTY);
            heard.remove(id);
            graceUntil.put(id, now + graceMillis);
            return;
        }
        Long until = graceUntil.get(id);
        if (until != null) {
            if (now < until) return;
            graceUntil.remove(id);
        }
        AmbientZone zone = zones.zoneAt(p.getLocation());
        if (zone == null || zone.urls().isEmpty()) {
            if (last != null) {
                BerliozNetwork.send(p, MessageType.STOP_ORDER, EMPTY);
                heard.remove(id);
            }
            return;
        }
        if (last != null && last.isAmbiant && zone.id().equals(last.source)) return;
        sendAmbient(p, zone, 0);
    }

    /** Re-sent permissions when they change */
    private void refreshPermission(Player p, UUID id) {
        boolean playlist = p.hasPermission(BerliozNetwork.PLAYLIST_PERMISSION);
        boolean ambient = p.hasPermission(BerliozNetwork.AMBIENT_PERMISSION);
        int state = (playlist ? 1 : 0) | (ambient ? 2 : 0);
        Integer prev = permSent.get(id);
        if (prev == null || prev != state) {
            BerliozNetwork.send(p, MessageType.PERMISSION,
                    new byte[] {(byte) (playlist ? 1 : 0), (byte) (ambient ? 1 : 0)});
            permSent.put(id, state);
        }
    }

    private Source nearestSource(Player listener) {
        Location at = listener.getLocation();
        Source best = null;
        double bestSq = Double.MAX_VALUE;
        for (Source s : sources.values()) {
            Player owner = Bukkit.getPlayer(s.ownerId);
            if (owner == null || !owner.getWorld().equals(listener.getWorld())) continue;
            double distSq = owner.getLocation().distanceSquared(at);
            if (distSq <= (double) s.radius * s.radius && distSq < bestSq) {
                bestSq = distSq;
                best = s;
            }
        }
        return best;
    }

    private Source findServerSource(Player p) {
        Source best = null;
        double bestSq = Double.MAX_VALUE;
        for (Source s : serverSources.values()) {
            if (covers(s, p)) {
                double distSq = s.origin.distanceSquared(p.getLocation());
                if (distSq < bestSq) {
                    bestSq = distSq;
                    best = s;
                }
            }
        }
        return best;
    }

    /** Whether server source is in p}s world and its radius covers them. */
    private static boolean covers(Source s, Player p) {
        Location o = s.origin;
        return o != null && o.getWorld() != null && o.getWorld().equals(p.getWorld())
                && o.distanceSquared(p.getLocation()) <= (double) s.radius * s.radius;
    }

    private Source serverSourceById(UUID id) {
        for (Source s : serverSources.values()) {
            if (s.ownerId.equals(id)) return s;
        }
        return null;
    }

    private static boolean hasBerlioz(Player p) {
        return p.getListeningPluginChannels().contains(BerliozChannel.KEY);
    }

    private Source approachingSource(Player listener) {
        Location at = listener.getLocation();
        Source best = null;
        double bestSq = Double.MAX_VALUE;
        for (Source s : sources.values()) {
            Player owner = Bukkit.getPlayer(s.ownerId);
            if (owner == null || !owner.getWorld().equals(listener.getWorld())) continue;
            double distSq = owner.getLocation().distanceSquared(at);
            int band = s.radius + APPROACH_MARGIN;
            if (distSq > (double) s.radius * s.radius && distSq <= (double) band * band && distSq < bestSq) {
                bestSq = distSq;
                best = s;
            }
        }
        return best;
    }

    /** Tells a player approaching a DJ to pre-cache its current track, once per track. */
    private void precacheApproaching(Player p, UUID id) {
        Source s = approachingSource(p);
        if (s == null) {
            approachCached.remove(id);
            return;
        }
        if (s.url.equals(approachCached.get(id))) return;  // cache already asked
        BerliozNetwork.send(p, MessageType.CACHE_ORDER, s.url.getBytes(StandardCharsets.UTF_8));
        approachCached.put(id, s.url);
    }

    /** Sends a modless player a clickable chat link to the current track, once per track. */
    private void notifyChatLink(Player p, UUID id, String url) {
        if (url.equals(chatNotified.get(id))) return;
        chatNotified.put(id, url);
        if (!url.startsWith("http")) return; // Trick for mc thing
        Component link = Component.text("Click here")
                .color(NamedTextColor.WHITE)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url)));
        p.sendMessage(Component.text("[♪ Music playing: ", NamedTextColor.AQUA)
                .append(link)
                .append(Component.text(" ♪]", NamedTextColor.AQUA)));
    }

    /** Sends the ambient track at index of zone to listener. */
    private void sendAmbient(Player listener, AmbientZone zone, int index) {
        List<String> urls = zone.urls();
        int idx = ((index % urls.size()) + urls.size()) % urls.size();
        String url = urls.get(idx);
        sendPlayOrder(listener, url, 0f, false, KIND_AMBIENT);
        Heard h = new Heard();
        h.source = zone.id();
        h.url = url;
        h.isPaused = false;
        h.isAmbiant = true;
        h.ambientIndex = idx;
        heard.put(listener.getUniqueId(), h);
    }

    private static void sendPlayOrder(Player listener, Source source, long now) {
        byte kind = source.server ? KIND_SERVER : KIND_DJ;
        sendPlayOrder(listener, source.url, (float) source.offset(now), source.paused, kind);
    }

    private static void sendPlayOrder(Player listener, String url, float offset, boolean paused, byte kind) {
        byte[] urlBytes = url.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(Float.BYTES + 2 + urlBytes.length);
        buf.putFloat(offset);
        buf.put((byte) (paused ? 1 : 0));
        buf.put(kind);
        buf.put(urlBytes);
        BerliozNetwork.send(listener, MessageType.PLAY_ORDER, buf.array());
    }
}
