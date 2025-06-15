package me.mss1r.DiscordAuth.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.mss1r.DiscordAuth.DiscordAuth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.*;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;

public class LocalizationManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> LOCALIZATIONS = new HashMap<>();
    private static final String DEFAULT_LANG = "ru_ru";

    public static void loadLocalizations() {
        File langDir = Config.LANG_DIR;
        if (!langDir.exists() || !langDir.isDirectory()) {
            createDefaultLocalization();
        }

        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (langFiles == null || langFiles.length == 0) {
            createDefaultLocalization();
            return;
        }

        for (File file : langFiles) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                Map<String, String> translations = new HashMap<>();
                json.entrySet().forEach(entry -> translations.put(entry.getKey(), entry.getValue().getAsString()));
                String langCode = file.getName().replace(".json", "");
                LOCALIZATIONS.put(langCode, translations);
            } catch (Exception e) {
                DiscordAuth.LOGGER.error("Failed to load localization from {}: {}", file.getName(), e.getMessage());
            }
        }
    }

    private static void createDefaultLocalization() {
        Map<String, String> ruLang = new HashMap<>();
        ruLang.put("discordauth.message.limbo_info", "§cВы в режиме авторизации!");
        ruLang.put("discordauth.message.limbo_title", "§eАвторизация Discord");
        ruLang.put("discordauth.message.limbo_subtitle", "§7Введите §b/discordlink <код>§7 из Discord!");
        ruLang.put("discordauth.message.login_required", "§cЭтот сервер требует авторизации через Discord. Введите §b/discordlink <код>§c, который вы получили от Discord-бота.");
        ruLang.put("discordauth.message.already_authorized", "§aВы уже авторизованы через Discord.");
        ruLang.put("discordauth.message.already_linked", "§cВы уже авторизованы.");
        ruLang.put("discordauth.message.auth_failed", "§cОшибка: код недействителен или произошла ошибка.");
        ruLang.put("discordauth.message.role_missing", "§cОшибка: у вашего Discord-аккаунта нет роли 'игрок'.");
        ruLang.put("discordauth.message.auth_success", "§aУспешно! Аккаунт привязан к Discord.");
        ruLang.put("discordauth.message.cannot_command", "§cСначала авторизуйтесь через Discord.");
        ruLang.put("discordauth.message.cannot_chat", "§cВы не можете писать в чат до авторизации.");
        ruLang.put("discordauth.message.cannot_interact", "§cВы не можете взаимодействовать с миром до авторизации.");
        ruLang.put("discordauth.message.time_left", "§eОсталось §b%s §eсекунд для авторизации.");
        ruLang.put("discordauth.message.timeout", "§cВремя на ввод кода истекло! Зайдите снова и попробуйте ещё раз.");
        ruLang.put("discordauth.message.discord_already_linked", "§cЭтот Discord-аккаунт уже привязан к другому игроку!");
        ruLang.put("discordauth.message.unlink_success", "§aВаша привязка к Discord удалена!");
        ruLang.put("discordauth.message.not_linked", "§cВаш аккаунт не был привязан к Discord.");
        LOCALIZATIONS.put(DEFAULT_LANG, ruLang);
        saveLocalization(DEFAULT_LANG, ruLang);
    }

    private static void saveLocalization(String langCode, Map<String, String> translations) {
        File file = new File(Config.LANG_DIR, langCode + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(translations, writer);
        } catch (Exception e) {
            DiscordAuth.LOGGER.error("Failed to save default localization {}: {}", langCode, e.getMessage());
        }
    }

    // Преобразует § в цветной текст
    private static String colorize(String input) {
        // Поддержка &c/&a и т.д. на всякий
        // input = input.replaceAll("&([0-9a-fk-or])", "§$1");
        return input;
    }

    public static MutableComponent getTranslation(String key, String langCode, Object... args) {
        if (langCode == null || langCode.isEmpty()) {
            langCode = Config.DEFAULT_LANGUAGE.get();
        }
        Map<String, String> translations = LOCALIZATIONS.getOrDefault(langCode, LOCALIZATIONS.get(DEFAULT_LANG));
        if (translations == null) {
            return Component.literal(key);
        }
        String translation = translations.getOrDefault(key, key);
        try {
            translation = String.format(translation, args);
        } catch (IllegalFormatException e) {
            DiscordAuth.LOGGER.error("Failed to format translation for key {} in language {}: {}", key, langCode, e.getMessage());
        }
        translation = colorize(translation);
        return Component.literal(translation);
    }

    public static MutableComponent getTranslation(String key, Object... args) {
        return getTranslation(key, Config.DEFAULT_LANGUAGE.get(), args);
    }
}
