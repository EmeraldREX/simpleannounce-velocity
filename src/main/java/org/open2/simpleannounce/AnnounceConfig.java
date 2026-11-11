
package org.open2.simpleannounce;

import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class AnnounceConfig {

    private final Path file;
    private final Logger logger;

    private Map<String, Object> root = new LinkedHashMap<>();

    AnnounceConfig(Path dataDirectory, Logger logger)
            throws IOException {

        this.file = dataDirectory.resolve("config.yml");
        this.logger = logger;

        Files.createDirectories(dataDirectory);
    }

    synchronized void load() throws IOException {
        if (!Files.exists(file)) {
            root = defaults();
            save();
            return;
        }

        LoaderOptions options = new LoaderOptions();

        Object loaded;

        try (Reader reader = Files.newBufferedReader(
                file,
                StandardCharsets.UTF_8)) {

            loaded = new Yaml(
                    new SafeConstructor(options)
            ).load(reader);
        }

        if (loaded instanceof Map<?, ?> map) {
            root = new LinkedHashMap<>();

            map.forEach((key, value) -> {
                if (key instanceof String s) {
                    root.put(s, value);
                }
            });

        } else {
            logger.warn(
                    "config.yml is empty or invalid; using defaults."
            );

            root = defaults();
        }

        root.putIfAbsent(
                "fixed-announce",
                defaults().get("fixed-announce")
        );

        root.putIfAbsent(
                "announce",
                "ようこそ！\\n公式Discord: https://discord.com/"
        );

        root.putIfAbsent(
                "discord",
                new LinkedHashMap<String, Object>()
        );

        saveIfMissingDiscordDefaults();
    }

    synchronized String getAnnouncement() {
        Object value = root.get("announce");

        return value == null ? "" : String.valueOf(value);
    }

    synchronized String getFixedAnnouncement() {
        Object value = root.get("fixed-announce");
        return value == null ? "" : String.valueOf(value);
    }

    synchronized void setAnnouncement(String message)
            throws IOException {

        String previous = getAnnouncement();

        root.put("announce", message);

        try {
            save();
        } catch (IOException e) {
            root.put("announce", previous);
            throw e;
        }
    }

    synchronized String getDiscordToken() {
        return discordString("token", "");
    }

    synchronized String getDiscordGuildId() {
        return discordString("guild-id", "");
    }

    synchronized Set<String> getDiscordAllowedUserIds() {
        Object discordValue = root.get("discord");

        if (!(discordValue instanceof Map<?, ?> discord)) {
            return Set.of();
        }

        Object value = discord.get("allowed-user-ids");

        if (!(value instanceof List<?> list)) {
            return Set.of();
        }

        return list.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String discordString(
            String key,
            String fallback
    ) {
        Object discordValue = root.get("discord");

        if (!(discordValue instanceof Map<?, ?> discord)) {
            return fallback;
        }

        Object value = discord.get(key);

        return value == null
                ? fallback
                : String.valueOf(value);
    }

    private void saveIfMissingDiscordDefaults()
            throws IOException {

        Object value = root.get("discord");

        Map<String, Object> discord;

        if (value instanceof Map<?, ?> map) {
            discord = new LinkedHashMap<>();

            map.forEach((key, val) -> {
                if (key instanceof String s) {
                    discord.put(s, val);
                }
            });

        } else {
            discord = new LinkedHashMap<>();
        }

        discord.putIfAbsent(
                "token",
                "PUT_BOT_TOKEN_HERE"
        );

        discord.putIfAbsent(
                "guild-id",
                "PUT_GUILD_ID_HERE"
        );

        discord.putIfAbsent(
                "announce-channel-id",
                ""
        );

        discord.putIfAbsent(
                "allowed-user-ids",
                new ArrayList<String>()
        );

        root.put("discord", discord);

        save();
    }

    private Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();

        defaults.put(
                "fixed-announce",
                """
                スマートフォン入手コマンド:/smartphone
                dynmap:http://alphabbs.net:8123/
                【サーバー公式アカウント】
                https://twitter.com/MCserver2026
                【過去のイベント配信】
                https://www.youtube.com/@alpha6000-apsc
                ===【イベント日程】===
                """.stripTrailing()
        );



        defaults.put(
                "announce",
                "ようこそ！\\n公式Discord: https://discord.com/"
        );

        Map<String, Object> discord = new LinkedHashMap<>();

        discord.put("token", "PUT_BOT_TOKEN_HERE");
        discord.put("guild-id", "PUT_GUILD_ID_HERE");
        discord.put("announce-channel-id", "");
        discord.put(
                "allowed-user-ids",
                new ArrayList<String>()
        );

        defaults.put("discord", discord);

        return defaults;
    }

    private synchronized void save() throws IOException {
        DumperOptions options = new DumperOptions();

        options.setDefaultFlowStyle(
                DumperOptions.FlowStyle.BLOCK
        );

        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);

        Path temporary = Files.createTempFile(
                file.getParent(),
                "config-",
                ".tmp"
        );

        try {
            try (Writer writer = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8)) {

                yaml.dump(root, writer);
            }

            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    synchronized String getDiscordAnnounceChannelId() {
        return discordString("announce-channel-id", "");
    }

}
