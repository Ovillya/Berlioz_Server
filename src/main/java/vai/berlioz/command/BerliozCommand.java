package vai.berlioz.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.BlockCommandSender;
import org.jspecify.annotations.NonNull;
import vai.berlioz.BerliozServer;
import vai.berlioz.command.arguments.UriArgumentType;
import vai.berlioz.network.BerliozNetwork;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class BerliozCommand {

    public BerliozCommand(@NonNull BerliozServer plugin) {}

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        var command = Commands.literal("berlioz");

        var stopArgument = Commands.literal("stop");
        var playArgument = Commands.literal("play");
        var preloadArgument = Commands.literal("preload")
                .then(Commands.argument(
                        "radius", IntegerArgumentType.integer(1)
                )
                        .then(
                                Commands.argument(
                                        "uri", new UriArgumentType()
                                ).executes(this::preload)
                        ));

        stopArgument.executes(this::stop);

        var radius = Commands.argument("radius", IntegerArgumentType.integer(1));
        var trackUriArgument = Commands.argument("uri", new UriArgumentType());

        playArgument.then(
                radius.then(
                        trackUriArgument.executes(this::play)
                )
        );

        return command
                .then(stopArgument)
                .then(playArgument)
                .then(preloadArgument)
                .requires(source -> source.getSender().hasPermission(BerliozNetwork.COMMAND_PERMISSION));
    }

    private int preload(CommandContext<CommandSourceStack> ctx) {
        var radius = ctx.getArgument("radius", Integer.class);
        var location = ctx.getSource().getLocation();
        var trackUri = ctx.getArgument("uri", URI.class);

        var sender = ctx.getSource().getSender();

        try {
            var trackUrl = trackUri.toURL();

            BerliozNetwork.broadcastCache(
                    location,
                    radius,
                    trackUrl.toString()
            );

            ctx.getSource().getSender().sendMessage(
                    Component.text(
                            "Preloading (radius "+ radius +") :",
                            NamedTextColor.AQUA
                    ).append(Component.newline()).append(
                            Component.text(trackUrl.toString(), NamedTextColor.GRAY)
                                    .clickEvent(ClickEvent.openUrl(trackUrl))
                    )
            );
        }catch (MalformedURLException e) {
            sender.sendMessage(
                    Component.text(e.getMessage(), NamedTextColor.RED)
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private int play(CommandContext<CommandSourceStack> ctx) {
        var radiusArgument = ctx.getArgument("radius", Integer.class);
        var trackUri = ctx.getArgument("uri", URI.class);

        var location = ctx.getSource().getLocation();

        try {
            var trackUrl = trackUri.toURL();
            BerliozNetwork.startServerBroadcast(
                    location,
                    radiusArgument,
                    trackUrl.toString(),
                    ctx.getSource().getSender() instanceof BlockCommandSender
            );

            ctx.getSource().getSender().sendMessage(
                    Component.text(
                            "Broadcasting (radius "+ radiusArgument +") :",
                            NamedTextColor.AQUA
                    ).append(Component.newline()).append(
                            Component.text(trackUrl.toString(), NamedTextColor.GRAY)
                                    .clickEvent(ClickEvent.openUrl(trackUrl))
                    )
            );
        }catch (MalformedURLException e) {
            ctx.getSource().getSender().sendMessage(Component.text(
                    e.getMessage(),
                    NamedTextColor.RED
            ));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int stop(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var location = ctx.getSource().getLocation();

        if(location == null) {
            sender.sendMessage(
                    Component.text(
                            "This must be run at a location.",
                            NamedTextColor.RED
                    )
            );

            return Command.SINGLE_SUCCESS;
        }

        BerliozNetwork.stopServerBroadcast(location);
        sender.sendMessage(
                Component.text("Stopped the nearest broadcast.", NamedTextColor.AQUA)
        );

        return Command.SINGLE_SUCCESS;
    }
}
