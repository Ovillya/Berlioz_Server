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
import vai.berlioz.Messages;
import vai.berlioz.Messages.Lang;
import vai.berlioz.network.BerliozNetwork;


public final class BerliozCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Lang lang = Messages.langOf(sender);
        if (!isAllowed(sender)) {
            reply(sender, Messages.get(lang, "cmd.noPermission"), NamedTextColor.RED);
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender, lang);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "play" -> handlePlay(sender, args, lang);
            case "stop" -> handleStop(sender, lang);
            case "cache" -> handleCache(sender, args, lang);
            default -> sendUsage(sender, lang);
        }
        return true;
    }

    private void handleStop(CommandSender sender, Lang lang) {
        Location loc = locationOf(sender);
        if (loc == null) {
            reply(sender, Messages.get(lang, "cmd.needPlayerOrBlock"), NamedTextColor.RED);
            return;
        }
        BerliozNetwork.stopServerBroadcast(loc);
        reply(sender, Messages.get(lang, "cmd.stopped"), NamedTextColor.AQUA);
    }

    private void handlePlay(CommandSender sender, String[] args, Lang lang) {
        if (args.length < 3) {
            reply(sender, Messages.get(lang, "cmd.usagePlay"), NamedTextColor.RED);
            return;
        }
        int radius = parseRadius(sender, args[1], lang);
        if (radius < 0) return;
        Location loc = locationOf(sender);
        if (loc == null) {
            reply(sender, Messages.get(lang, "cmd.needPlayerOrBlock"), NamedTextColor.RED);
            return;
        }
        String url = args[2];
        BerliozNetwork.startServerBroadcast(loc, radius, url, sender instanceof BlockCommandSender);
        reply(sender, Messages.get(lang, "cmd.broadcasting", radius, url), NamedTextColor.AQUA);
    }

    private void handleCache(CommandSender sender, String[] args, Lang lang) {
        if (args.length < 3) {
            reply(sender, Messages.get(lang, "cmd.usageCache"), NamedTextColor.RED);
            return;
        }
        int radius = parseRadius(sender, args[1], lang);
        if (radius < 0) return;
        Location loc = locationOf(sender);
        if (loc == null) {
            reply(sender, Messages.get(lang, "cmd.needPlayerOrBlock"), NamedTextColor.RED);
            return;
        }
        BerliozNetwork.broadcastCache(loc, radius, args[2]);
        reply(sender, Messages.get(lang, "cmd.precaching", radius, args[2]), NamedTextColor.AQUA);
    }

    private int parseRadius(CommandSender sender, String arg, Lang lang) {
        try {
            int radius = Integer.parseInt(arg);
            if (radius <= 0) {
                reply(sender, Messages.get(lang, "cmd.radiusPositive"), NamedTextColor.RED);
                return -1;
            }
            return radius;
        } catch (NumberFormatException e) {
            reply(sender, Messages.get(lang, "cmd.radiusNumber", arg), NamedTextColor.RED);
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

    private void sendUsage(CommandSender sender, Lang lang) {
        reply(sender, Messages.get(lang, "cmd.usage"), NamedTextColor.GRAY);
    }

    private static void reply(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }
}
