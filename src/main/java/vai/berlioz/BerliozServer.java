package vai.berlioz;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import vai.berlioz.command.BerliozCommand;
import vai.berlioz.network.BerliozNetwork;

/** Main plugin class. */
public class BerliozServer extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BerliozNetwork.init(this);
        getLogger().info("BerliozServer enabled.");

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            var registrar = commands.registrar();

            registrar.register(
                new BerliozCommand(this).createCommand().build()
            );
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("BerliozServer disabled.");
    }
}
