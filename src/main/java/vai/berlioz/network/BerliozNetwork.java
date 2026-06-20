package vai.berlioz.network;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import vai.berlioz.ambient.AmbientZone;
import vai.berlioz.ambient.AmbientZoneStore;


public final class BerliozNetwork {
    public static final String PLAYLIST_PERMISSION = "berlioz.playlist";

    public static final String AMBIENT_PERMISSION = "berlioz.ambient";

    public static final String COMMAND_PERMISSION = "berlioz.command";

    private static final int MAX_CACHE_RADIUS = 256;

    private static final int NEARBY_RADIUS = 300;

    private static final int MAX_LIST_RADIUS = 512;

    private static final int MAX_ZONE_URLS = 256;

    private static final Map<Byte, Handler> HANDLERS = new HashMap<>();

    private static BroadcastManager broadcasts;
    private static AmbientZoneStore ambientZones;

    private static JavaPlugin plugin;

    public interface Handler {
        void handle(Player player, byte[] body);
    }

    private BerliozNetwork() {}

    public static void init(@NonNull JavaPlugin owner) {
        plugin = owner;
        var messenger = owner.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(owner, BerliozChannel.KEY);
        messenger.registerIncomingPluginChannel(owner, BerliozChannel.KEY, new Listener());

        ambientZones = new AmbientZoneStore(owner);
        long graceMillis = Math.max(0L, owner.getConfig().getLong("ambient-grace-seconds", 3)) * 1000L;
        broadcasts = new BroadcastManager(owner, ambientZones, graceMillis);
        broadcasts.start();

        registerReceiver(MessageType.HELLO, (player, body) -> {
            String version = new String(body, StandardCharsets.UTF_8);
            owner.getLogger().info("Berlioz client " + version + " connected for " + player.getName());
            send(player, MessageType.WELCOME,
                    owner.getPluginMeta().getVersion().getBytes(StandardCharsets.UTF_8));
            sendPermission(player);
        });
        registerReceiver(MessageType.CACHE_REQUEST,
                (player, body) -> relayToNearby(player, body, MessageType.CACHE_ORDER, "cache"));

        registerReceiver(MessageType.BROADCAST_START, (player, body) -> {
            if (!player.hasPermission(PLAYLIST_PERMISSION) || body.length < Integer.BYTES) return;
            ByteBuffer buf = ByteBuffer.wrap(body);
            int radius = buf.getInt();
            byte[] urlBytes = new byte[buf.remaining()];
            buf.get(urlBytes);
            String url = new String(urlBytes, StandardCharsets.UTF_8);
            if (!url.isEmpty())
                broadcasts.startBroadcast(player, radius, url);
        });
        registerReceiver(MessageType.BROADCAST_PAUSE, (player, body) -> {
            if (player.hasPermission(PLAYLIST_PERMISSION)) {
                broadcasts.pauseBroadcast(player);
            }
        });
        registerReceiver(MessageType.BROADCAST_RESUME, (player, body) -> {
            if (player.hasPermission(PLAYLIST_PERMISSION)) {
                broadcasts.resumeBroadcast(player);
            }
        });
        registerReceiver(MessageType.BROADCAST_STOP,
                (player, body) -> broadcasts.stopBroadcast(player));
        registerReceiver(MessageType.BROADCAST_SEEK, (player, body) -> {
            if (player.hasPermission(PLAYLIST_PERMISSION) && body.length >= Float.BYTES) {
                broadcasts.seekBroadcast(player, ByteBuffer.wrap(body).getFloat());
            }
        });

        registerReceiver(MessageType.ZONE_LIST_REQUEST, BerliozNetwork::handleZoneListRequest);
        registerReceiver(MessageType.ZONE_SAVE, BerliozNetwork::handleZoneSave);
        registerReceiver(MessageType.ZONE_DELETE, BerliozNetwork::handleZoneDelete);
        registerReceiver(MessageType.ASK_NEXT, (player, body) -> broadcasts.askNext(player));
    }

    private static void sendPermission(Player player) {
        send(player, MessageType.PERMISSION, new byte[] {
            (byte) (player.hasPermission(PLAYLIST_PERMISSION) ? 1 : 0),
            (byte) (player.hasPermission(AMBIENT_PERMISSION) ? 1 : 0)
        });
    }

    private static void relayToNearby(Player sender, byte[] body, byte orderType, String label) {
        if (!sender.hasPermission(PLAYLIST_PERMISSION)) {
            plugin.getLogger().info(
                    "Denied " + label + " request from " + sender.getName() + " (no permission)");
            return;
        }
        if (body.length < Integer.BYTES) return;
        ByteBuffer buf = ByteBuffer.wrap(body);
        int radius = Math.max(1, Math.min(buf.getInt(), MAX_CACHE_RADIUS));
        byte[] urlBytes = new byte[buf.remaining()];
        buf.get(urlBytes);
        String url = new String(urlBytes, StandardCharsets.UTF_8);
        if (url.isEmpty()) return;

        Location origin = sender.getLocation();
        double maxDistanceSq = (double) radius * radius;
        int sent = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(sender) || !p.getWorld().equals(sender.getWorld())) continue;
            if (p.getLocation().distanceSquared(origin) <= maxDistanceSq) {
                send(p, orderType, urlBytes);
                sent++;
            }
        }
        plugin.getLogger().info(
                sender.getName() + " asked " + sent + " nearby player(s) to " + label + " " + url);
    }


    public static void startServerBroadcast(Location origin, int radius, String url, boolean commandBlock) {
        broadcasts.startServerBroadcast(origin, radius, url, commandBlock);
    }


    public static void stopServerBroadcast(Location near) {
        broadcasts.stopServerBroadcast(near);
    }

    public static void broadcastCache(Location origin, int radius, String url) {
        if (origin.getWorld() == null || url.isEmpty()) return;
        int r = Math.max(1, Math.min(radius, MAX_CACHE_RADIUS));
        double maxDistanceSq = (double) r * r;
        byte[] urlBytes = url.getBytes(StandardCharsets.UTF_8);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(origin.getWorld())
                    && p.getLocation().distanceSquared(origin) <= maxDistanceSq) {
                send(p, MessageType.CACHE_ORDER, urlBytes);
            }
        }
    }

    private static void handleZoneListRequest(Player player, byte[] body) {
        if (!player.hasPermission(AMBIENT_PERMISSION)) return; // No listing orders from normal players
        int radius = body.length >= Integer.BYTES ? ByteBuffer.wrap(body).getInt() : NEARBY_RADIUS;
        sendZoneList(player, radius);
    }

    private static void sendZoneList(Player player, int radius) {
        int r = Math.max(1, Math.min(radius, MAX_LIST_RADIUS));
        List<AmbientZone> nearby = ambientZones.nearby(player.getLocation(), r);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(nearby.size());
            for (AmbientZone z : nearby) {
                writeZone(out, z);
            }
        } catch (IOException e) {
            return;
        }
        send(player, MessageType.ZONE_LIST, baos.toByteArray());
    }

    private static void writeZone(DataOutputStream out, AmbientZone z) throws IOException {
        out.writeLong(z.id().getMostSignificantBits());
        out.writeLong(z.id().getLeastSignificantBits());
        out.writeUTF(z.name());
        out.writeInt(z.x1());
        out.writeInt(z.y1());
        out.writeInt(z.z1());
        out.writeInt(z.x2());
        out.writeInt(z.y2());
        out.writeInt(z.z2());
        List<String> urls = z.urls();
        out.writeInt(urls.size());
        for (String u : urls) {
            out.writeUTF(u);
        }
    }

    private static void handleZoneSave(Player player, byte[] body) {
        if (!player.hasPermission(AMBIENT_PERMISSION)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            boolean hasId = in.readBoolean();
            UUID id = hasId ? new UUID(in.readLong(), in.readLong()) : UUID.randomUUID();
            String name = in.readUTF();
            int x1 = in.readInt();
            int y1 = in.readInt();
            int z1 = in.readInt();
            int x2 = in.readInt();
            int y2 = in.readInt();
            int z2 = in.readInt();
            int count = Math.min(in.readInt(), MAX_ZONE_URLS);
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < count; i++)
                urls.add(in.readUTF());
            AmbientZone existing = hasId ? ambientZones.get(id) : null;
            UUID worldId = existing != null ? existing.world() : player.getWorld().getUID();
            ambientZones.put(new AmbientZone(id, name, worldId, x1, y1, z1, x2, y2, z2, urls));
        } catch (IOException e) {
            plugin.getLogger().warning("Malformed ZONE_SAVE from " + player.getName());
            return;
        }
        sendZoneList(player, NEARBY_RADIUS);
    }

    private static void handleZoneDelete(Player player, byte[] body) {
        if (!player.hasPermission(AMBIENT_PERMISSION) || body.length < 2 * Long.BYTES) {
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(body);
        ambientZones.remove(new UUID(buf.getLong(), buf.getLong()));
        sendZoneList(player, NEARBY_RADIUS);
    }

    public static void registerReceiver(byte type, Handler handler) {
        HANDLERS.put(type, handler);
    }

    public static void send(Player target, byte type, byte[] body) {
        if (plugin == null) {
            throw new IllegalStateException("BerliozNetwork.init(plugin) was never called");
        }
        if (!target.getListeningPluginChannels().contains(BerliozChannel.KEY)) return;
        byte[] data = new byte[1 + body.length];
        data[0] = type;
        System.arraycopy(body, 0, data, 1, body.length);
        target.sendPluginMessage(plugin, BerliozChannel.KEY, data);
    }

    private static final class Listener implements PluginMessageListener {
        @Override
        public void onPluginMessageReceived(String channel, Player player,
                byte[] message) {
            if (!BerliozChannel.KEY.equals(channel)) return;
            if (message.length == 0) {
                plugin.getLogger().log(Level.WARNING,
                        "Empty Berlioz packet from " + player.getName());
                return;
            }
            byte type = message[0];
            byte[] body = new byte[message.length - 1];
            System.arraycopy(message, 1, body, 0, body.length);

            Handler handler = HANDLERS.get(type);
            if (handler == null) {
                plugin.getLogger().log(Level.WARNING,
                        "No handler for Berlioz message type " + type + " from " + player.getName());
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> handler.handle(player, body));
        }
    }
}
