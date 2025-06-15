package me.mss1r.DiscordAuth;

import com.mojang.logging.LogUtils;
import me.mss1r.DiscordAuth.auth.AuthManager;
import me.mss1r.DiscordAuth.auth.LimboManager;
import me.mss1r.DiscordAuth.command.DiscordLinkCommand;
import me.mss1r.DiscordAuth.config.Config;
import me.mss1r.DiscordAuth.config.LocalizationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.UUID;

@Mod("discordauth")
public class DiscordAuth {
    public static final String MODID = "discordauth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DiscordAuth() {
        Config.register();
        LocalizationManager.loadLocalizations();
        AuthManager.init();
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerCommand);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerChat);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerInteract);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    // Блокировка команд в Limbo (кроме /discordlink)
    private void onPlayerCommand(CommandEvent event) {
        if (event.getParseResults() == null || event.getParseResults().getContext() == null) return;
        var source = event.getParseResults().getContext().getSource();
        if (source.getEntity() instanceof ServerPlayer player && LimboManager.isInLimbo(player.getUUID())) {
            String cmd = event.getParseResults().getReader().getString();
            if (!cmd.trim().startsWith("/discordlink")) {
                player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.cannot_command", player.getLanguage()));
                event.setCanceled(true);
            }
        }
    }

    // Блокировка чата в Limbo
    private void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (LimboManager.isInLimbo(player.getUUID())) {
            player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.cannot_chat", player.getLanguage()));
            event.setCanceled(true);
        }
    }

    // Блокировка взаимодействия в Limbo
    private void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && LimboManager.isInLimbo(player.getUUID())) {
            player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.cannot_interact", player.getLanguage()));
            event.setCanceled(true);
        }
    }

    // Таймер и фиксация позиции
    private void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        if (LimboManager.isInLimbo(player.getUUID())) {
            if (event.phase == TickEvent.Phase.END && player.level().getGameTime() % 20 == 0) {
                long ms = LimboManager.timeLeft(player.getUUID());
                long seconds = ms / 1000;
                player.displayClientMessage(LocalizationManager.getTranslation("discordauth.message.time_left", player.getLanguage(), seconds), true); // true - action bar
            }
            int limboY = Math.min(300, player.serverLevel().getMaxBuildHeight() - 10);
            if (player.getY() < limboY) {
                player.teleportTo(player.serverLevel(), player.getX(), limboY, player.getZ(), player.getYRot(), player.getXRot());
            }
            player.setDeltaMovement(0, 0, 0);
            player.resetFallDistance();
        }
    }

    // Проверка таймаута для всех игроков в Limbo
    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        for (UUID uuid : LimboManager.getAll()) {
            if (LimboManager.isTimeout(uuid)) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    player.connection.disconnect(LocalizationManager.getTranslation("discordauth.message.timeout", player.getLanguage()));
                    LimboManager.remove(player);
                }
            }
        }
    }

    // Регистрация команд
    private void onRegisterCommands(RegisterCommandsEvent event) {
        DiscordLinkCommand.register(event.getDispatcher());
    }

    // Обработка входа игрока
    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            AuthManager.handleLogin(serverPlayer);
        }
    }
}