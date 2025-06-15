package me.mss1r.DiscordAuth.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import me.mss1r.DiscordAuth.DiscordAuth;
import me.mss1r.DiscordAuth.config.Config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Работа с Discord Bot API
public class DiscordBotApi {
    private static String getApiUrl() {
        // URL из конфига
        return Config.BOT_API_URL.get();
    }
    private static final Gson GSON = new Gson();

    // Проверяем роль по Discord ID (GET /api/hasRole)
    public static boolean hasRole(String discordId) {
        if (discordId == null || discordId.isEmpty()) return false;
        try {
            URL url = new URL(getApiUrl() + "/api/hasRole?discordId=" + discordId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject resp = GSON.fromJson(reader, JsonObject.class);
                return resp.get("hasRole").getAsBoolean();
            }
        } catch (IOException | JsonSyntaxException e) {
            DiscordAuth.LOGGER.warn("Failed to check role for discordId {}: {}", discordId, e.getMessage());
            return false;
        }
    }

    // Проверяем код от Discord-бота (POST /api/verifyCode)
    public static LinkResult verifyCode(String code, String mcNick) {
        if (code == null || code.length() > 32) return null;
        try {
            URL url = new URL(getApiUrl() + "/api/verifyCode");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            JsonObject obj = new JsonObject();
            obj.addProperty("code", code);
            obj.addProperty("mcNick", mcNick);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(obj.toString().getBytes());
            }
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject resp = GSON.fromJson(reader, JsonObject.class);
                boolean hasRole = resp.has("hasRole") && resp.get("hasRole").getAsBoolean();
                String discordId = resp.has("discordId") ? resp.get("discordId").getAsString() : null;
                if (hasRole && discordId != null && !discordId.isEmpty()) {
                    return new LinkResult(true, discordId);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            DiscordAuth.LOGGER.warn("Failed to verify code {} for {}: {}", code, mcNick, e.getMessage());
        }
        return null;
    }

    // Результат проверки кода
    public static class LinkResult {
        private final boolean hasRole;
        private final String discordId;
        public LinkResult(boolean hasRole, String discordId) {
            this.hasRole = hasRole;
            this.discordId = discordId;
        }
        public boolean hasRole() { return hasRole; }
        public String getDiscordId() { return discordId; }
    }
    public static boolean unlink(String discordId) {
        if (discordId == null || discordId.isEmpty()) return false;
        try {
            URL url = new URL(getApiUrl() + "/api/unlink");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            JsonObject obj = new JsonObject();
            obj.addProperty("discordId", discordId);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(obj.toString().getBytes());
            }
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject resp = GSON.fromJson(reader, JsonObject.class);
                return resp.has("success") && resp.get("success").getAsBoolean();
            }
        } catch (IOException | JsonSyntaxException e) {
            DiscordAuth.LOGGER.warn("Failed to unlink Discord ID {}: {}", discordId, e.getMessage());
            return false;
        }
    }
}
