package vai.berlioz;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Not that good, but the lang thing from mods doesnt exist */
public final class Messages {

    public enum Lang { EN, FR }

    private static final Map<String, String[]> TABLE = new HashMap<>(); // key -> {EN, FR}

    static {
        put("cmd.noPermission",
                "You don't have permission to use /berlioz.",
                "Vous n'avez pas la permission d'utiliser /berlioz.");
        put("cmd.needPlayerOrBlock",
                "This must be run by a player or a command block.",
                "Doit être exécuté par un joueur ou un bloc de commande.");
        put("cmd.stopped",
                "Stopped the nearest server broadcast.",
                "Diffusion serveur la plus proche arrêtée.");
        put("cmd.broadcasting",
                "Broadcasting (radius %d): %s",
                "Diffusion (rayon %d) : %s");
        put("cmd.precaching",
                "Pre-caching (radius %d): %s",
                "Pré-chargement (rayon %d) : %s");
        put("cmd.radiusPositive",
                "Radius must be a positive number.",
                "Le rayon doit être un nombre positif.");
        put("cmd.radiusNumber",
                "Radius must be a number: %s",
                "Le rayon doit être un nombre : %s");
        put("cmd.usagePlay",
                "Usage: /berlioz play <radius> <url>",
                "Usage : /berlioz play <rayon> <url>");
        put("cmd.usageCache",
                "Usage: /berlioz cache <radius> <url>",
                "Usage : /berlioz cache <rayon> <url>");
        put("cmd.usage",
                "Usage: /berlioz <play <radius> <url> | stop | cache <radius> <url>>",
                "Usage : /berlioz <play <rayon> <url> | stop | cache <rayon> <url>>");

        put("reminder.pre",
                "♪ This server supports live in-game music. You'll still get clickable links to "
                        + "what's playing - to hear it directly in-game, install ",
                "♪ Ce serveur propose de la musique en jeu. Vous recevrez quand même des liens "
                        + "cliquables vers ce qui joue - pour l'entendre directement en jeu, installez ");
        put("reminder.link", "Berlioz on Modrinth", "Berlioz sur Modrinth");
        put("reminder.post", " (or update your modpack).", " (ou mettez à jour votre modpack).");

        put("music.prefix", "[♪ Music playing: ", "[♪ Musique en cours : ");
        put("music.listen", "Click here", "Cliquez ici");
        put("music.suffix", " ♪]", " ♪]");
    }

    private Messages() {}

    public static Lang langOf(CommandSender sender) {
        if (sender instanceof Player player
                && "fr".equalsIgnoreCase(player.locale().getLanguage())) {
            return Lang.FR;
        }
        return Lang.EN;
    }

    public static String get(Lang lang, String key, Object... args) {
        String[] pair = TABLE.get(key);
        if (pair == null) {
            return key;
        }
        String template = pair[lang == Lang.FR ? 1 : 0];
        return args.length == 0 ? template : String.format(template, args);
    }

    private static void put(String key, String en, String fr) {
        TABLE.put(key, new String[] {en, fr});
    }
}
