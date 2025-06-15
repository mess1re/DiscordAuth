package me.mss1r.DiscordAuth.auth;

import java.util.UUID;

public class Session {
    private UUID uuid;
    private String discordId;
    private long loginTime;
    private static final long SESSION_DURATION = 1000L * 60 * 60 * 24 * 7; // 7 дней

    // Сессия: связка UUID игрока и Discord ID
    public Session(UUID uuid, String discordId, long loginTime) {
        this.uuid = uuid;
        this.discordId = discordId;
        this.loginTime = loginTime;
    }

    // Проверка, истекла ли сессия
    public boolean isExpired() {
        return System.currentTimeMillis() - loginTime > SESSION_DURATION;
    }

    public UUID getUuid() { return uuid; }
    public String getDiscordId() { return discordId; }
    public long getLoginTime() { return loginTime; }
}