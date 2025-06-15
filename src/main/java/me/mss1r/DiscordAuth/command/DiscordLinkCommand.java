package me.mss1r.DiscordAuth.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.mss1r.DiscordAuth.auth.AuthManager;
import me.mss1r.DiscordAuth.auth.Session;
import me.mss1r.DiscordAuth.config.LocalizationManager;
import me.mss1r.DiscordAuth.storage.LocalStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

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
                        .then(Commands.literal("unlink")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    UUID uuid = player.getUUID();
                                    Session s = LocalStorage.getSession(uuid);
                                    if (s != null) {
                                        LocalStorage.removeSession(uuid);
                                        player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.unlink_success", player.getLanguage()));
                                    } else {
                                        player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.not_linked", player.getLanguage()));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }
}