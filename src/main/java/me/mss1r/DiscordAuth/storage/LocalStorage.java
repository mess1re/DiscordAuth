package me.mss1r.DiscordAuth.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.mss1r.DiscordAuth.DiscordAuth;
import me.mss1r.DiscordAuth.auth.Session;

import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Локальное хранилище Discord-сессий
public class LocalStorage {
    private static final File FILE = new File("discordauth_sessions.json");
    private static final Gson GSON = new Gson();
    private static final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    // Загружаем все сессии из файла
    public static void loadAll() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                ConcurrentHashMap<UUID, Session> loaded = GSON.fromJson(reader, new TypeToken<ConcurrentHashMap<UUID, Session>>(){}.getType());
                if (loaded != null) sessions.putAll(loaded);
            } catch (Exception e) {
                // Бэкап битого файла
                File corrupt = new File(FILE.getParent(), "discordauth_sessions_corrupt_" + System.currentTimeMillis() + ".json");
                FILE.renameTo(corrupt);
                DiscordAuth.LOGGER.error("Failed to load sessions, file backed up as {}: {}", corrupt.getName(), e.getMessage());
                sessions.clear();
            }
        }
    }

    // Сохраняем одну сессию
    public static void saveSession(Session session) {
        sessions.put(session.getUuid(), session);
        saveToFile();
    }

    // Получаем сессию по UUID (если протухла — удаляем)
    public static Session getSession(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s != null && s.isExpired()) {
            removeSession(uuid);
            return null;
        }
        return s;
    }
    public static UUID getUuidByDiscordId(String discordId) {
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            if (entry.getValue().getDiscordId().equals(discordId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Удаляем сессию по UUID
    public static void removeSession(UUID uuid) {
        sessions.remove(uuid);
        saveToFile();
    }

    // Сохраняем все сессии в файл (атомарно)
    private static synchronized void saveToFile() {
        File tmp = new File(FILE.getAbsolutePath() + ".tmp");
        try (FileWriter writer = new FileWriter(tmp)) {
            GSON.toJson(sessions, writer);
            writer.flush();
            if (!tmp.renameTo(FILE)) throw new IOException("Atomic write failed");
        } catch (Exception e) {
            DiscordAuth.LOGGER.error("Failed to save sessions: {}", e.getMessage());
        }
    }
    public static void removeSessionByDiscordId(String discordId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().getDiscordId().equals(discordId));
        saveToFile();
        DiscordAuth.LOGGER.info("Removed session for Discord ID: " + discordId);
    }
}
