////package org.open2.simpleannounce;
////
////import net.kyori.adventure.text.Component;
////import net.kyori.adventure.text.TextComponent;
////import net.kyori.adventure.text.event.ClickEvent;
////import net.kyori.adventure.text.format.NamedTextColor;
////import org.bukkit.Bukkit;
////import org.bukkit.ChatColor;
////import org.bukkit.command.*;
////import org.bukkit.entity.Player;
////import org.bukkit.event.*;
////import org.bukkit.event.player.*;
////import org.bukkit.plugin.java.JavaPlugin;
////
////import java.util.*;
////import java.util.regex.Matcher;
////import java.util.regex.Pattern;
////
////public class SimpleAnnounce extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
////
////    private String announceMessage = "";
////    private final Set<UUID> waitingRegister = new HashSet<>();
////
////    private static final String PERM = "simpleannounce.use";
////
////    @Override
////    public void onEnable() {
////        Bukkit.getPluginManager().registerEvents(this, this);
////
////        PluginCommand cmd = getCommand("announce");
////        if (cmd != null) {
////            cmd.setExecutor(this);
////            cmd.setTabCompleter(this);
////        } else {
////            getLogger().severe("announceコマンドが見つかりません！");
////        }
////
////        saveDefaultConfig();
////        announceMessage = getConfig().getString("announce", "");
////    }
////
////    @Override
////    public void onDisable() {
////        getConfig().set("announce", announceMessage);
////        saveConfig();
////    }
////
////    @EventHandler
////    public void onJoin(PlayerJoinEvent e) {
////        if (announceMessage != null && !announceMessage.isEmpty()) {
////            e.getPlayer().sendMessage(buildComponent(announceMessage));
////        }
////    }
////
////    @EventHandler(priority = EventPriority.HIGHEST)
////    public void onChat(AsyncPlayerChatEvent e) {
////        Player p = e.getPlayer();
////
////        if (waitingRegister.contains(p.getUniqueId())) {
////            e.setCancelled(true);
////            waitingRegister.remove(p.getUniqueId());
////
////            announceMessage = e.getMessage();
////            p.sendMessage(ChatColor.GREEN + "告知を登録しました！");
////        }
////    }
////
////    @Override
////    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
////
////        if (!(sender instanceof Player p)) {
////            sender.sendMessage("プレイヤーのみ実行可能");
////            return true;
////        }
////
////        // 👇 権限チェック（OP or 権限）
////        if (!p.isOp() && !p.hasPermission(PERM)) {
////            return true;
////        }
////
////        if (args.length == 0) {
////            p.sendMessage("/announce register または /announce @a");
////            return true;
////        }
////
////        if (args[0].equalsIgnoreCase("register")) {
////            waitingRegister.add(p.getUniqueId());
////            p.sendMessage(ChatColor.YELLOW + "チャットに告知文を入力してください");
////            return true;
////        }
////
////        if (args[0].equalsIgnoreCase("@a")) {
////            if (announceMessage == null || announceMessage.isEmpty()) {
////                p.sendMessage(ChatColor.RED + "告知が登録されていません");
////                return true;
////            }
////
////            Component comp = buildComponent(announceMessage);
////
////            for (Player target : Bukkit.getOnlinePlayers()) {
////                target.sendMessage(comp);
////            }
////
////            p.sendMessage(ChatColor.GREEN + "全体に告知を送信しました");
////            return true;
////        }
////
////        return false;
////    }
////
////    @Override
////    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
////
////        if (!(sender instanceof Player p)) return Collections.emptyList();
////        if (!p.isOp() && !p.hasPermission(PERM)) return Collections.emptyList();
////
////        if (args.length == 1) {
////            return Arrays.asList("register", "@a");
////        }
////
////        return Collections.emptyList();
////    }
////
////    private Component buildComponent(String message) {
////
////        message = message.replace("\\n", "\n");
////
////        TextComponent.Builder builder = Component.text();
////
////        Pattern pattern = Pattern.compile("(/server\\s+\\w+)");
////        Matcher matcher = pattern.matcher(message);
////
////        int lastEnd = 0;
////
////        while (matcher.find()) {
////            builder.append(Component.text(message.substring(lastEnd, matcher.start())));
////
////            String cmd = matcher.group();
////
////            builder.append(
////                    Component.text(cmd)
////                            .color(NamedTextColor.GREEN)
////                            .clickEvent(ClickEvent.runCommand(cmd))
////            );
////
////            lastEnd = matcher.end();
////        }
////
////        builder.append(Component.text(message.substring(lastEnd)));
////
////        return builder.build();
////    }
////}
//package org.open2.simpleannounce;
//
//import net.kyori.adventure.text.Component;
//import net.kyori.adventure.text.TextComponent;
//import net.kyori.adventure.text.event.ClickEvent;
//import net.kyori.adventure.text.format.NamedTextColor;
//import org.bukkit.*;
//import org.bukkit.command.*;
//import org.bukkit.entity.Player;
//import org.bukkit.event.*;
//import org.bukkit.event.player.*;
//import org.bukkit.inventory.ItemStack;
//import org.bukkit.inventory.meta.BookMeta;
//import org.bukkit.persistence.PersistentDataContainer;
//import org.bukkit.persistence.PersistentDataType;
//import org.bukkit.plugin.java.JavaPlugin;
//
//import java.util.*;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//public class SimpleAnnounce extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
//
//    private String announceMessage = "";
//
//    private final Map<UUID, List<String>> editingMap = new HashMap<>();
//    private final Set<UUID> bookEditing = new HashSet<>();
//
//    private static final String PERM = "simpleannounce.use";
//    private NamespacedKey bookKey;
//
//    @Override
//    public void onEnable() {
//        Bukkit.getPluginManager().registerEvents(this, this);
//
//        bookKey = new NamespacedKey(this, "announce_book");
//
//        PluginCommand cmd = getCommand("announce");
//        if (cmd != null) {
//            cmd.setExecutor(this);
//            cmd.setTabCompleter(this);
//        } else {
//            getLogger().severe("announceコマンドが見つかりません！");
//        }
//
//        saveDefaultConfig();
//        announceMessage = getConfig().getString("announce", "");
//    }
//
//    @Override
//    public void onDisable() {
//        getConfig().set("announce", announceMessage);
//        saveConfig();
//    }
//
//    // 参加時表示
//    @EventHandler
//    public void onJoin(PlayerJoinEvent e) {
//        if (!announceMessage.isEmpty()) {
//            e.getPlayer().sendMessage(buildComponent(announceMessage));
//        }
//    }
//
//    // チャット入力（非公開）
//    @EventHandler(priority = EventPriority.HIGHEST)
//    public void onChat(AsyncPlayerChatEvent e) {
//        Player p = e.getPlayer();
//
//        if (editingMap.containsKey(p.getUniqueId())) {
//            e.setCancelled(true);
//            editingMap.get(p.getUniqueId()).add(e.getMessage());
//            p.sendMessage(ChatColor.GRAY + "追加: " + e.getMessage());
//        }
//    }
//
//    // 本編集処理
//    @EventHandler
//    public void onBookEdit(PlayerEditBookEvent e) {
//        Player p = e.getPlayer();
//
//        if (!bookEditing.contains(p.getUniqueId())) return;
//
//        ItemStack item = p.getInventory().getItemInMainHand();
//        if (!isOurBook(item)) return;
//
//        // 署名してない = キャンセル
//        if (!e.isSigning()) {
//            bookEditing.remove(p.getUniqueId());
//            Bukkit.getScheduler().runTaskLater(this, () -> removeOurBook(p), 1L);
//            p.sendMessage(ChatColor.RED + "編集をキャンセルしました");
//            return;
//        }
//
//        // 保存
//        BookMeta meta = e.getNewBookMeta();
//        List<String> pages = meta.getPages();
//
//// 🔥 空ページ除外
//        List<String> filtered = new ArrayList<>();
//        for (String page : pages) {
//            if (page != null && !page.trim().isEmpty()) {
//                filtered.add(page);
//            }
//        }
//
//        announceMessage = String.join("\n", filtered);
//        p.sendMessage(ChatColor.GREEN + "告知を更新しました！");
//
//        bookEditing.remove(p.getUniqueId());
//
//        // 本削除
//        Bukkit.getScheduler().runTaskLater(this, () -> removeOurBook(p), 1L);
//    }
//
//    // 本識別
//    private boolean isOurBook(ItemStack item) {
//        if (item == null || item.getItemMeta() == null) return false;
//
//        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
//        return container.has(bookKey, PersistentDataType.INTEGER);
//    }
//
//    // 本削除（安全）
//    private void removeOurBook(Player p) {
//        for (ItemStack item : p.getInventory().getContents()) {
//            if (isOurBook(item)) {
//                p.getInventory().remove(item);
//                break;
//            }
//        }
//    }
//
//    // コマンド
//    @Override
//    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
//
//        if (!(sender instanceof Player p)) return true;
//
//        if (!p.isOp() && !p.hasPermission(PERM)) {
//            p.sendMessage(ChatColor.RED + "権限がありません");
//            return true;
//        }
//
//        if (args.length == 0) {
//            p.sendMessage("/announce register | book | end | @a");
//            return true;
//        }
//
//        // チャット登録開始
//        if (args[0].equalsIgnoreCase("register")) {
//            editingMap.put(p.getUniqueId(), new ArrayList<>());
//            p.sendMessage(ChatColor.YELLOW + "1行ずつ入力 /announce endで終了");
//            return true;
//        }
//
//        // チャット登録終了
//        if (args[0].equalsIgnoreCase("end")) {
//            List<String> list = editingMap.remove(p.getUniqueId());
//
//            if (list == null) {
//                p.sendMessage(ChatColor.RED + "編集中ではありません");
//                return true;
//            }
//
//            announceMessage = String.join("\n", list);
//            p.sendMessage(ChatColor.GREEN + "告知を登録しました！");
//            return true;
//        }
//
//        // 本入力
//        if (args[0].equalsIgnoreCase("book")) {
//            bookEditing.add(p.getUniqueId());
//
//            ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
//            BookMeta meta = (BookMeta) book.getItemMeta();
//
//            // 既存内容をセット
//            if (!announceMessage.isEmpty()) {
//                String[] lines = announceMessage.split("\n");
//                meta.setPages(Arrays.asList(lines));
//            }
//
//            // タグ
//            meta.getPersistentDataContainer().set(bookKey, PersistentDataType.INTEGER, 1);
//
//            book.setItemMeta(meta);
//
//            p.getInventory().addItem(book);
//
//            p.sendMessage(ChatColor.YELLOW + "本を編集してください（署名で保存 / 閉じるとキャンセル）");
//            return true;
//        }
//
//        // 全体送信
//        if (args[0].equalsIgnoreCase("@a")) {
//            if (announceMessage.isEmpty()) {
//                p.sendMessage(ChatColor.RED + "告知が登録されていません");
//                return true;
//            }
//
//            Component comp = buildComponent(announceMessage);
//
//            for (Player target : Bukkit.getOnlinePlayers()) {
//                target.sendMessage(comp);
//            }
//
//            p.sendMessage(ChatColor.GREEN + "送信しました");
//            return true;
//        }
//
//        return true;
//    }
//
//    // Tab補完
//    @Override
//    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
//
//        if (!(sender instanceof Player p)) return Collections.emptyList();
//        if (!p.isOp() && !p.hasPermission(PERM)) return Collections.emptyList();
//
//        if (args.length == 1) {
//            return Arrays.asList("register", "book", "end", "@a");
//        }
//
//        return Collections.emptyList();
//    }
//
//    // メッセージ生成
//    private Component buildComponent(String message) {
//
//        message = message.replace("\\n", "\n");
//
//        TextComponent.Builder builder = Component.text();
//
//        // URL または /server xxx を検出
//        Pattern pattern = Pattern.compile(
//                "(https?://\\S+)|(/server\\s+\\w+)"
//        );
//
//        Matcher matcher = pattern.matcher(message);
//
//        int lastEnd = 0;
//
//        while (matcher.find()) {
//
//            // 通常テキスト
//            builder.append(Component.text(message.substring(lastEnd, matcher.start())));
//
//            String matched = matcher.group();
//
//            // URL
//            if (matched.startsWith("http://") || matched.startsWith("https://")) {
//
//                builder.append(
//                        Component.text(matched)
//                                .color(NamedTextColor.AQUA)
//                                .clickEvent(ClickEvent.openUrl(matched))
//                );
//
//            } else {
//                // /server xxx
//                builder.append(
//                        Component.text(matched)
//                                .color(NamedTextColor.GREEN)
//                                .clickEvent(ClickEvent.runCommand(matched))
//                );
//            }
//
//            lastEnd = matcher.end();
//        }
//
//        // 残り
//        builder.append(Component.text(message.substring(lastEnd)));
//
//        return builder.build();
//    }
//}