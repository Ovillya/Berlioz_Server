package vai.berlioz.command;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vai.berlioz.network.BerliozNetwork;


public final class BerliozCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAllowed(sender)) {
            reply(sender, "You don't have permission to use /berlioz.", NamedTextColor.RED);
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "play" -> handlePlay(sender, args);
            case "stop" -> handleStop(sender);
            case "cache" -> handleCache(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleStop(CommandSender sender) {
        Location loc = locationOf(sender);
        if (loc == null) {
            reply(sender, "This must be run by a player or a command block.", NamedTextColor.RED);
            return;
        }
        BerliozNetwork.stopServerBroadcast(loc);
        reply(sender, "Stopped the nearest server broadcast.", NamedTextColor.AQUA);
    }

    private void handlePlay(CommandSender sender, String[] args) {
        if (args.length < 3) {
            reply(sender, "Usage: /berlioz play <radius> <url>", NamedTextColor.RED);
            return;
        }
        int radius = parseRadius(sender, args[1]);
        if (radius < 0) return;
        Location loc = locationOf(sender);
        if (loc == null) {
            reply(sender, "This must be run by a player or a command block.", NamedTextColor.RED);
            return;
        }
        String url = args[2];
        BerliozNetwork.startServerBroadcast(loc, radius, url, sender instanceof BlockCommandSender);
        reply(sender, "Broadcasting (radius " + radius + "): " + url, NamedTextColor.AQUA);
    }

    private void handleCache(CommandSender sender, String[] args) {
        if (args.length < 3) {
            reply(sender, "Usage: /berlioz cache <radius> <url>", NamedTextColor.RED);
            return;
        }
        int radius = parseRadius(sender, args[1]);
        if (radius < 0) return;
        Location loc = locationOf(sender);
        if (loc == null) {
            reply(sender, "This must be run by a player or a command block.", NamedTextColor.RED);
            return;
        }
        BerliozNetwork.broadcastCache(loc, radius, args[2]);
        reply(sender, "Pre-caching (radius " + radius + "): " + args[2], NamedTextColor.AQUA);
    }

    private int parseRadius(CommandSender sender, String arg) {
        try {
            int radius = Integer.parseInt(arg);
            if (radius <= 0) {
                reply(sender, "Radius must be a positive number.", NamedTextColor.RED);
                return -1;
            }
            return radius;
        } catch (NumberFormatException e) {
            reply(sender, "Radius must be a number: " + arg, NamedTextColor.RED);
            return -1;
        }
    }

    private static Location locationOf(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getLocation();
        }
        if (sender instanceof BlockCommandSender block) {
            return block.getBlock().getLocation();
        }
        return null;
    }

    private static boolean isAllowed(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission(BerliozNetwork.COMMAND_PERMISSION);
    }

    private void sendUsage(CommandSender sender) {
        reply(sender, "Usage: /berlioz <play <radius> <url> | stop | cache <radius> <url>>", NamedTextColor.GRAY);
    }

    private static void reply(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }
}
