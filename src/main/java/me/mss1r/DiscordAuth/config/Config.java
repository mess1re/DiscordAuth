package me.mss1r.DiscordAuth.config;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import java.io.File;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static ForgeConfigSpec.ConfigValue<String> BOT_API_URL;
    public static ForgeConfigSpec.LongValue TIMEOUT_MS;
    public static ForgeConfigSpec.ConfigValue<String> DEFAULT_LANGUAGE;
    public static final File CONFIG_DIR = new File("config/discordauth");
    public static final File LANG_DIR = new File(CONFIG_DIR, "lang");

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Настройки мода DiscordAuth");
        builder.push("general");
        BOT_API_URL = builder
                .comment("URL API Discord-бота")
                .define("bot_api_url", "http://127.0.0.1:5000");
        TIMEOUT_MS = builder
                .comment("Таймаут ввода кода (в миллисекундах)")
                .defineInRange("timeout_ms", 30000L, 10000L, 60000L);
        DEFAULT_LANGUAGE = builder
                .comment("Язык по умолчанию (например, 'ru_ru')")
                .define("default_language", "ru_ru");
        builder.pop();
        SPEC = builder.build();
    }

    public static void register() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        if (!LANG_DIR.exists()) {
            LANG_DIR.mkdirs();
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "discordauth/discordauth.toml");
    }
}