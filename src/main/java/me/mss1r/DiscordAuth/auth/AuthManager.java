package me.mss1r.DiscordAuth.auth;

import me.mss1r.DiscordAuth.config.LocalizationManager;
import me.mss1r.DiscordAuth.network.DiscordBotApi;
import me.mss1r.DiscordAuth.storage.LocalStorage;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.UUID;

public class AuthManager {

    // Загрузка Дискорд сессий
    public static void init() {
        LocalStorage.loadAll();
    }

    // Без авторизации на небеса
    public static void handleLogin(ServerPlayer player) {
        String lang = player.getLanguage();
        Session session = LocalStorage.getSession(player.getUUID());

        // Уже авторизован — выход из limbo
        if (session != null && !session.isExpired() && DiscordBotApi.hasRole(session.getDiscordId())) {
            LimboManager.remove(player);
            player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.already_authorized", lang));
            return;
        }

        // Первый раз — в limbo
        if (!LimboManager.isInLimbo(player.getUUID())) {
            LimboManager.add(player);
            showLimboTitle(player, lang);
        }

        // Сообщение и эффекты limbo
        player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.login_required", lang));
        giveLimboEffects(player);
    }

    // Привязка аккаунта через /discordlink <код>
    public static void linkAccount(ServerPlayer player, String code) {
        String lang = player.getLanguage();

        if (!LimboManager.isInLimbo(player.getUUID())) {
            player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.already_linked", lang));
            return;
        }

        DiscordBotApi.LinkResult result = DiscordBotApi.verifyCode(code, player.getName().getString());
        if (result == null || !result.hasRole() || result.getDiscordId() == null || result.getDiscordId().isEmpty()) {
            player.sendSystemMessage(LocalizationManager.getTranslation(
                    result == null ? "discordauth.message.auth_failed" : "discordauth.message.role_missing", lang));
            return;
        }

        // Проверка, не привязан ли Discord ID к другому UUID
        UUID existingUuid = LocalStorage.getUuidByDiscordId(result.getDiscordId());
        if (existingUuid != null && !existingUuid.equals(player.getUUID())) {
            player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.discord_already_linked", lang));
            return;
        }

        LocalStorage.saveSession(new Session(player.getUUID(), result.getDiscordId(), System.currentTimeMillis()));
        LimboManager.remove(player);
        player.sendSystemMessage(LocalizationManager.getTranslation("discordauth.message.auth_success", lang));
    }

    // Мона эффекты добавить
    private static void giveLimboEffects(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.INVISIBILITY)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0, false, false, false));
        }
    }

    // Удалить эффекты limbo
    private static void removeLimboEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.BLINDNESS);
    }

    // Показать титлы limbo при входе
    private static void showLimboTitle(ServerPlayer player, String lang) {
        player.displayClientMessage(LocalizationManager.getTranslation("discordauth.message.limbo_info", lang), false);
        player.connection.send(new ClientboundSetTitleTextPacket(
                LocalizationManager.getTranslation("discordauth.message.limbo_title", lang)
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                LocalizationManager.getTranslation("discordauth.message.limbo_subtitle", lang)
        ));
    }
}
