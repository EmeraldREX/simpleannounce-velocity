
package org.open2.simpleannounce;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(
        id = "simpleannounce",
        name = "SimpleAnnounce",
        version = "2.0.0",
        description = "Proxy-wide announcements with Discord control"
)
public final class SimpleAnnounceVelocity {

    //private static final String PERMISSION = "simpleannounce.use";

    private static final Pattern CLICKABLE =
            Pattern.compile("(https?://\\S+)|(/server\\s+\\w+)");

    private static final MinecraftChannelIdentifier SYNC_CHANNEL =
            MinecraftChannelIdentifier.from("simpleannounce:sync");
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private AnnounceConfig config;
    private volatile JDA jda;

    @Inject
    public SimpleAnnounceVelocity(
            ProxyServer proxy,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            config = new AnnounceConfig(dataDirectory, logger);
            config.load();
        } catch (Exception e) {
            logger.error("Could not load SimpleAnnounce config", e);
            throw new IllegalStateException(e);
        }

        CommandManager manager = proxy.getCommandManager();
        CommandMeta meta = manager.metaBuilder("announce")
                .plugin(this)
                .build();

        manager.register(meta, new AnnounceCommand());
        proxy.getChannelRegistrar().register(SYNC_CHANNEL);

        CompletableFuture.runAsync(this::startDiscordBot);

        logger.info("SimpleAnnounce Velocity enabled.");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!SYNC_CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) return;
        byte[] data = event.getData();
        if (data.length < 1 || data.length > 32767) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int action = in.readUnsignedByte();
            if (action != 1 && action != 2) return;
            UUID id = new UUID(in.readLong(), in.readLong());
            var playerOpt = proxy.getPlayer(id);
            if (playerOpt.isEmpty()) return;
            var player = playerOpt.get();
            if (player.getCurrentServer().isEmpty()
                    || !player.getCurrentServer().get().getServer().equals(connection.getServer())) return;
            boolean allowed = player.hasPermission("simpleannounce.edit");
            if (action == 2) {
                long nonce = in.readLong();
                if (in.available() != 0) return;
                String message = allowed ? config.getAnnouncement() : "";
                if (message == null) message = "";
                // writeUTF and plugin messaging both have size limits
                if (message.getBytes(StandardCharsets.UTF_8).length > 24000) {
                    allowed = false;
                    message = "";
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (DataOutputStream out = new DataOutputStream(buffer)) {
                    out.writeByte(3);
                    out.writeLong(id.getMostSignificantBits());
                    out.writeLong(id.getLeastSignificantBits());
                    out.writeLong(nonce);
                    out.writeBoolean(allowed);
                    out.writeUTF(message);
                }
                connection.getServer().sendPluginMessage(SYNC_CHANNEL, buffer.toByteArray());
                return;
            }
            boolean silent = in.readBoolean();
            String message = in.readUTF();
            if (in.available() != 0 || !allowed || message.isBlank()
                    || message.getBytes(StandardCharsets.UTF_8).length > 24000) {
                logger.warn("Rejected announcement update from {}", id);
                return;
            }
            config.setAnnouncement(message);
            if (!silent) postAnnouncementToDiscord(message);
            logger.info("Announcement updated via Paper by {}", player.getUsername());
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Invalid Paper announcement message", e);
        } catch (Exception e) {
            logger.error("Paper announcement processing failed", e);
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        String message = getFullAnnouncement();

        if (!message.isBlank()) {
            event.getPlayer().sendMessage(buildComponent(message));
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        JDA bot = jda;

        if (bot != null) {
            bot.shutdownNow();
            jda = null;
        }
    }

    private void startDiscordBot() {
        String token = config.getDiscordToken();

        if (token == null
                || token.isBlank()
                || token.equals("PUT_BOT_TOKEN_HERE")) {

            logger.info("Discord bot disabled.");
            return;
        }

        try {
            JDA bot = JDABuilder.createDefault(token)
                    .addEventListeners(new DiscordListener())
                    .build();

            jda = bot;
            bot.awaitReady();

            String guildId = config.getDiscordGuildId();

            if (guildId == null
                    || guildId.isBlank()
                    || guildId.equals("PUT_GUILD_ID_HERE")) {

                logger.warn("Discord guild ID is not configured.");
                return;
            }

            var guild = bot.getGuildById(guildId);

            if (guild == null) {
                logger.error("Discord guild ID was not found.");
                return;
            }


            guild.updateCommands()
                    .addCommands(
                            Commands.slash(
                                    "announce-set",
                                    "告知文を変更してDiscordにも投稿"
                            ).addOption(
                                    OptionType.STRING,
                                    "message",
                                    "新しい告知文",
                                    true
                            ),

                            Commands.slash(
                                    "announce-silset",
                                    "Discordに投稿せず告知文を変更"
                            ).addOption(
                                    OptionType.STRING,
                                    "message",
                                    "新しい告知文",
                                    true
                            )
                    )
                    .queue(
                            success -> logger.info(
                                    "Discord /announce-set registered."
                            ),
                            error -> logger.error(
                                    "Could not register Discord command",
                                    error
                            )
                    );

            logger.info("Discord bot connected.");

        } catch (Exception e) {
            logger.error("Discord bot failed to start.", e);

            JDA bot = jda;
            if (bot != null) {
                bot.shutdownNow();
                jda = null;
            }
        }
    }

    private void postAnnouncementToDiscord(String message) {
        JDA bot = jda;

        if (bot == null || bot.getStatus() != JDA.Status.CONNECTED) {
            logger.warn("Discord announcement not sent: bot is not connected.");
            return;
        }

        String channelId = config.getDiscordAnnounceChannelId().trim();

        if (channelId.isEmpty()) {
            logger.warn(
                    "Discord announcement not sent: discord.announce-channel-id is not configured."
            );
            return;
        }

        var channel = bot.getTextChannelById(channelId);

        if (channel == null) {
            logger.warn(
                    "Discord announcement not sent: text channel {} not found or inaccessible.",
                    channelId
            );
            return;
        }

        String text = message.replace("\\n", "\n");

        if (text.isBlank()) {
            logger.warn("Discord announcement not sent: message is empty.");
            return;
        }

        if (text.length() > 2000) {
            logger.warn(
                    "Discord announcement not sent: exceeds 2000 characters."
            );
            return;
        }

        channel.sendMessage(text)
                .setAllowedMentions(java.util.Collections.emptyList())
                .queue(
                        success -> logger.info(
                                "Announcement posted to Discord channel {}",
                                channelId
                        ),
                        error -> logger.error(
                                "Failed to post announcement to Discord channel {}",
                                channelId,
                                error
                        )
                );
    }

    private final class DiscordListener extends ListenerAdapter {

        @Override
        public void onSlashCommandInteraction(
                SlashCommandInteractionEvent event
        ) {
            boolean silent = event.getName().equals("announce-silset");

            if (!event.getName().equals("announce-set") && !silent) {
                return;
            }

            String configuredGuild = config.getDiscordGuildId();

            if (event.getGuild() == null
                    || !event.getGuild().getId().equals(configuredGuild)) {

                event.reply("このサーバーでは使用できません。")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            Set<String> allowed = config.getDiscordAllowedUserIds();

            if (allowed.isEmpty()
                    || !allowed.contains(event.getUser().getId())) {

                event.reply("このコマンドを実行する権限がありません。")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            String message = event.getOption("message") == null
                    ? ""
                    : event.getOption("message").getAsString();

            if (message.isBlank()) {
                event.reply("告知文が空です。")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            try {
                config.setAnnouncement(message);

// 通常のsetの場合だけDiscordに投稿
                if (!silent) {
                    postAnnouncementToDiscord(message);
                }

                event.reply(
                        "告知文をconfig.ymlに保存しました。"
                ).setEphemeral(true).queue();

                logger.info(
                        "Announcement updated by Discord user {}",
                        event.getUser().getId()
                );

            } catch (Exception e) {
                logger.error("Failed to save announcement.", e);

                event.reply("設定の保存に失敗しました。")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }


    private final class AnnounceCommand implements SimpleCommand {

        private static final String SEND_PERMISSION = "simpleannounce.send";
        private static final String EDIT_PERMISSION = "simpleannounce.edit";
        private static final String ADMIN_PERMISSION = "simpleannounce.admin";

        @Override
        public void execute(Invocation invocation) {
            var source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 0) {
                sendUsage(source);
                return;
            }

            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {

                case "@a" -> {
                    if (!source.hasPermission(SEND_PERMISSION)) {
                        deny(source);
                        return;
                    }

                    String message = getFullAnnouncement();

                    if (message == null || message.isBlank()) {
                        source.sendMessage(Component.text(
                                "告知が登録されていません。",
                                NamedTextColor.RED
                        ));
                        return;
                    }

                    Component component = buildComponent(message);

                    proxy.getAllPlayers().forEach(
                            player -> player.sendMessage(component)
                    );

                    source.sendMessage(Component.text(
                            "全体に告知を送信しました。",
                            NamedTextColor.GREEN
                    ));
                }

                case "set", "silset" -> {
                    if (!source.hasPermission(EDIT_PERMISSION)) {
                        deny(source);
                        return;
                    }

                    if (args.length < 2) {
                        source.sendMessage(Component.text(
                                "使い方: /announce set <告知文>"
                        ));
                        return;
                    }

                    String message = String.join(
                            " ",
                            java.util.Arrays.copyOfRange(args, 1, args.length)
                    );

                    try {
                        config.setAnnouncement(message);

// setの場合のみDiscordへ投稿
                        if (args[0].equalsIgnoreCase("set")) {
                            postAnnouncementToDiscord(message);
                        }

                        source.sendMessage(Component.text(
                                "告知を保存しました。",
                                NamedTextColor.GREEN
                        ));

                    } catch (Exception e) {
                        logger.error("Failed to save announcement.", e);

                        source.sendMessage(Component.text(
                                "告知の保存に失敗しました。",
                                NamedTextColor.RED
                        ));
                    }
                }

                case "reload" -> {
                    if (!source.hasPermission(ADMIN_PERMISSION)) {
                        deny(source);
                        return;
                    }

                    try {
                        config.load();

                        source.sendMessage(Component.text(
                                "設定を再読み込みしました。",
                                NamedTextColor.GREEN
                        ));

                    } catch (Exception e) {
                        logger.error("Failed to reload config.", e);

                        source.sendMessage(Component.text(
                                "設定の再読み込みに失敗しました。",
                                NamedTextColor.RED
                        ));
                    }
                }

                default -> sendUsage(source);
            }
        }

        private void deny(
                com.velocitypowered.api.command.CommandSource source
        ) {
            source.sendMessage(Component.text(
                    "このコマンドを実行する権限がありません。",
                    NamedTextColor.RED
            ));
        }

        private void sendUsage(
                com.velocitypowered.api.command.CommandSource source
        ) {
            source.sendMessage(Component.text(
                    "/announce @a | /announce set <告知文> | " +
                            "/announce silset <告知文> | /announce reload"
            ));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return true;
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            var source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length > 1) {
                return List.of();
            }

            List<String> suggestions = new java.util.ArrayList<>();

            if (source.hasPermission(SEND_PERMISSION)) {
                suggestions.add("@a");
            }

            if (source.hasPermission(EDIT_PERMISSION)) {
                suggestions.add("set");
                suggestions.add("silset");
            }

            if (source.hasPermission(ADMIN_PERMISSION)) {
                suggestions.add("reload");
            }

            String prefix = args.length == 0
                    ? ""
                    : args[0].toLowerCase(java.util.Locale.ROOT);

            return suggestions.stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
    }

    private String getFullAnnouncement() {
        String fixed = config.getFixedAnnouncement();
        String variable = config.getAnnouncement();

        if (fixed == null || fixed.isBlank()) {
            return variable == null ? "" : variable;
        }

        if (variable == null || variable.isBlank()) {
            return fixed;
        }

        return fixed.stripTrailing() + "\n" + variable;
    }


    private Component buildComponent(String message) {
        message = message.replace("\\n", "\n");

        TextComponent.Builder builder = Component.text();
        Matcher matcher = CLICKABLE.matcher(message);

        int lastEnd = 0;

        while (matcher.find()) {
            builder.append(
                    Component.text(
                            message.substring(lastEnd, matcher.start())
                    )
            );

            String matched = matcher.group();

            if (matched.startsWith("http://")
                    || matched.startsWith("https://")) {

                builder.append(
                        Component.text(matched)
                                .color(NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.openUrl(matched))
                );

            } else {
                builder.append(
                        Component.text(matched)
                                .color(NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand(matched))
                );
            }

            lastEnd = matcher.end();
        }

        builder.append(
                Component.text(message.substring(lastEnd))
        );

        return builder.build();
    }
}

