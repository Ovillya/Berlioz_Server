package vai.berlioz;

import io.papermc.lib.PaperLib;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;
import vai.berlioz.command.BerliozCommand;
import vai.berlioz.network.BerliozNetwork;

/** Main plugin class. */
public class BerliozServer extends JavaPlugin {

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);
        saveDefaultConfig();
        BerliozNetwork.init(this);
        Objects.requireNonNull(getCommand("berlioz"), "command 'berlioz' missing from plugin.yml")
                .setExecutor(new BerliozCommand());
        getLogger().info("BerliozServer enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("BerliozServer disabled.");
    }
}
