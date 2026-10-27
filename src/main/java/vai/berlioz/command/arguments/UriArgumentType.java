package vai.berlioz.command.arguments;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.Component;

import java.net.MalformedURLException;
import java.net.URI;

public class UriArgumentType implements CustomArgumentType.Converted<URI, String> {
    public final SimpleCommandExceptionType ERROR_INVALID_FORMAT = new SimpleCommandExceptionType(
            MessageComponentSerializer.message().serialize(
                    Component.text("Argument is not a valid URL.")
            )
    );

    @Override
    public URI convert(String nativeType) throws CommandSyntaxException {
        try {
            var uri = URI.create(nativeType);
            uri.toURL();
            return uri;
        } catch (IllegalArgumentException|MalformedURLException e) {
            throw ERROR_INVALID_FORMAT.create();
        }
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.greedyString();
    }
}
