package vai.berlioz.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NonNull;
import vai.berlioz.BerliozServer;
import vai.berlioz.command.arguments.UriArgumentType;
import vai.berlioz.network.BerliozNetwork;

import java.net.URI;

public class BerliozCommand {

    public BerliozCommand(@NonNull BerliozServer plugin) {}

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        var command = Commands.literal("berlioz");

        var stopArgument = Commands.literal("stop");
        var playArgument = Commands.literal("play");

        stopArgument.executes(this::stop);

        var playerSelector = Commands.argument("targets", ArgumentTypes.players());
        var trackUriArgument = Commands.argument("uri", new UriArgumentType());

        playArgument.then(
                playerSelector.then(
                        trackUriArgument.executes(this::play)
                )
        );

        return command
                .then(stopArgument)
                .then(playArgument);
    }

    private int play(CommandContext<CommandSourceStack> ctx) {
        var playerSelector = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
        var trackUri = ctx.getArgument("uri", URI.class);


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

        return Command.SINGLE_SUCCESS;
    }
}
