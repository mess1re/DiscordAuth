package me.mss1r.DiscordAuth.auth;

import me.mss1r.DiscordAuth.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LimboManager {
    // UUID игроков в limbo + время входа
    private static final Map<UUID, Long> limboPlayers = new ConcurrentHashMap<>();
    // Корды для возврата из limbo
    private static final Map<UUID, BlockPos> savedPositions = new ConcurrentHashMap<>();
    private static final long TIMEOUT_MS = Config.TIMEOUT_MS.get();

    // Помещаем игрока в limbo
    public static void add(ServerPlayer player) {
        UUID uuid = player.getUUID();
        limboPlayers.put(uuid, System.currentTimeMillis());
        savedPositions.put(uuid, player.blockPosition());
        teleportToLimbo(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);

        // Тут!
        player.setInvisible(true);
        player.setInvulnerable(true);
    }

    public static void remove(ServerPlayer player) {
        UUID uuid = player.getUUID();
        limboPlayers.remove(uuid);
        BlockPos oldPos = savedPositions.remove(uuid);
        if (oldPos != null) {
            player.teleportTo(player.serverLevel(), oldPos.getX() + 0.5, oldPos.getY(), oldPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
        player.removeAllEffects();
        // И тут!
        player.setInvisible(false);
        player.setInvulnerable(false);
    }

    // Проверяем, находится ли игрок в limbo
    public static boolean isInLimbo(UUID uuid) {
        return limboPlayers.containsKey(uuid);
    }

    // Сколько времени осталось до кика
    public static long timeLeft(UUID uuid) {
        if (!limboPlayers.containsKey(uuid)) return 0;
        long start = limboPlayers.get(uuid);
        long elapsed = System.currentTimeMillis() - start;
        return Math.max(0, TIMEOUT_MS - elapsed);
    }

    // Истёк ли лимит времени limbo
    public static boolean isTimeout(UUID uuid) {
        if (!limboPlayers.containsKey(uuid)) return false;
        return (System.currentTimeMillis() - limboPlayers.get(uuid)) > TIMEOUT_MS;
    }

    // Список всех игроков в limbo
    public static Iterable<UUID> getAll() {
        return limboPlayers.keySet();
    }

    // Тп в limbo
    private static void teleportToLimbo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int maxHeight = level.getMaxBuildHeight();
        int limboY = Math.min(300, maxHeight - 10);
        player.teleportTo(level, player.getX(), limboY, player.getZ(), player.getYRot(), player.getXRot());
    }
}
