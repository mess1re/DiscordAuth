package me.mss1r.DiscordAuth.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.mss1r.DiscordAuth.auth.AuthManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class DiscordLinkCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("discordlink")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String code = StringArgumentType.getString(ctx, "code");
                                    AuthManager.linkAccount(player, code);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }
}