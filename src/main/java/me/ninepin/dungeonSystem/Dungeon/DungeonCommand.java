package me.ninepin.dungeonSystem.Dungeon;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final DungeonSystem plugin;
    private final DungeonManager dungeonManager;

    public DungeonCommand(DungeonSystem plugin) {
        this.plugin = plugin;
        this.dungeonManager = plugin.getDungeonManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此指令只能由玩家執行");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "join":
                handleJoinCommand(player, args);
                break;
            case "list":
                handleListCommand(player);
                break;
            case "leave":
                handleLeaveCommand(player);
                break;
            case "reload":
                handleReloadCommand(player);
                break;
            case "key":
                handleKeyCommand(player, args);
                break;
            case "revive":
                handleReviveCommand(player, args);
                break;
            default:
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    /**
     * 處理加入副本指令
     */
    private void handleJoinCommand(Player player, String[] args) {
        // 檢查玩家是否有進入副本的權限
        if (!player.hasPermission("dungeon.join")) {
            player.sendMessage("§c你沒有權限使用此指令");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /dungeon join <副本名稱>");
            return;
        }
        if (dungeonManager.getPlayerDungeon(player.getUniqueId()) != null) {
            player.sendMessage("§c你已經在副本中，無法再次進入其他副本");
            return;
        }
        // 檢查玩家是否為隊伍隊長
        me.ninepin.dungeonSystem.party.Party party = plugin.getPartyManager().getPlayerParty(player.getUniqueId());

        if (party == null) {
            player.sendMessage("§c你必須加入一個隊伍才能進入副本");
            return;
        }

        if (!party.isOwner(player.getUniqueId())) {
            player.sendMessage("§c只有隊長才能使用此指令");
            return;
        }

        String dungeonId = args[1];

        // 嘗試進入副本（現在使用的是副本類型ID，而不是具體的實例ID）
        boolean success = dungeonManager.joinDungeon(player, dungeonId);

        // 如果沒有成功進入（可能是因為沒有可用實例），joinDungeon 方法會自己顯示錯誤信息
        if (!success) {
            // 這裡不需要額外的錯誤訊息，因為 joinDungeon 方法內已經處理了
        }
    }

    /**
     * 處理列出所有副本指令
     */
    private void handleListCommand(Player player) {
        Map<String, Dungeon> availableDungeons = dungeonManager.getAllDungeons();

        if (availableDungeons.isEmpty()) {
            player.sendMessage("§c没有可用的副本");
            return;
        }

        player.sendMessage("§6=== 可用副本列表 ===");

        for (Dungeon dungeon : availableDungeons.values()) {
            // 获取副本类型并显示
            String dungeonType = dungeon.getType();
            String typeDisplay = "normal".equals(dungeonType) ? "§7普通" : "§b波次";

            // 如果是波次副本，显示波次信息
            String waveInfo = "";
            if (dungeon instanceof WaveDungeon) {
                WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                waveInfo = String.format(" §a| §b波次數: §f%d", waveDungeon.getTotalWaves());
            }

            player.sendMessage(String.format(
                    "§e%s §7(§f%s§7) §7- §a等级要求: §f%d §a| §a最大人数: §f%d%s",
                    dungeon.getId(),
                    typeDisplay,
                    dungeon.getLevelRequired(),
                    dungeon.getMaxPlayers(),
                    waveInfo
            ));
        }
    }

    /**
     * 處理離開副本指令
     */
    private void handleLeaveCommand(Player player) {
        String dungeonId = dungeonManager.getPlayerDungeon(player.getUniqueId());

        if (dungeonId == null) {
            player.sendMessage("§c你當前不在任何副本中");
            return;
        }

        boolean success = dungeonManager.leaveDungeon(player);

        if (success) {
            player.sendMessage("§a你已成功離開副本");
        } else {
            player.sendMessage("§c離開副本失敗");
        }
    }

    /**
     * 處理獲取副本鑰匙指令
     */
    /**
     * 處理獲取副本鑰匙指令
     */
    private void handleKeyCommand(Player player, String[] args) {
        if (!player.hasPermission("dungeonsystem.admin")) {
            player.sendMessage("§c你沒有權限執行此指令");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /dungeon key <副本ID> [數量]");
            return;
        }

        String dungeonId = args[1];
        int amount = 1;

        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) amount = 1;
            } catch (NumberFormatException e) {
                player.sendMessage("§c數量必須是正整數");
                return;
            }
        }

        // 檢查是否有此副本
        if (!plugin.getDungeonManager().isDungeonAvailable(dungeonId)) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        // 获取副本对象，检查类型
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(dungeonId);
        boolean isWaveDungeon = (dungeon instanceof WaveDungeon);

        // 显示更多关于副本类型的信息
        if (isWaveDungeon) {
            WaveDungeon waveDungeon = (WaveDungeon) dungeon;
            player.sendMessage("§b正在生成 §e" + dungeonId + " §b的波次副本入場卷 (總波數: " + waveDungeon.getTotalWaves() + ")");
        } else {
            player.sendMessage("§a正在生成 §e" + dungeonId + " §a的普通副本入場卷");
        }

        plugin.getKeyManager().giveKey(player, dungeonId, amount);
    }

    /**
     * 處理重新加載副本配置指令
     */
    private void handleReloadCommand(Player player) {
        if (!player.hasPermission("dungeonsystem.admin")) {
            player.sendMessage("§c你沒有權限執行此指令");
            return;
        }

        plugin.reloadAllConfigs();
        player.sendMessage("§a副本配置已重新加載");
    }

    /**
     * 處理獲取復活裝置指令
     */
    private void handleReviveCommand(Player player, String[] args) {
        if (!player.hasPermission("dungeonsystem.admin")) {
            player.sendMessage("§c你沒有權限執行此指令");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /dungeon revive <normal|advanced> [數量]");
            return;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("normal") && !type.equals("advanced")) {
            player.sendMessage("§c復活裝置類型必須是 normal 或 advanced");
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) amount = 1;
            } catch (NumberFormatException e) {
                player.sendMessage("§c數量必須是正整數");
                return;
            }
        }

        plugin.getReviveItemManager().giveReviveItem(player, type, amount);
    }

    /**
     * 發送幫助訊息
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§6=== DungeonSystem 幫助 ===");
        player.sendMessage("§e/dungeon join <副本名稱> §7- 加入指定副本");
        player.sendMessage("§e/dungeon list §7- 列出所有可用副本");
        player.sendMessage("§e/dungeon leave §7- 離開當前副本");
        if (player.hasPermission("dungeonsystem.admin")) {
            player.sendMessage("§e/dungeon reload §7- 重新加載副本配置");
            player.sendMessage("§e/dungeon key <副本ID> [數量] §7- 獲取副本入場卷");
            player.sendMessage("§e/dungeon revive <normal|advanced> [數量] §7- 獲取復活裝置");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("join", "list", "leave"));

            if (sender.hasPermission("dungeonsystem.admin")) {
                completions.add("reload");
                completions.add("key");
                completions.add("revive"); // 添加新的子指令
            }

            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // 當輸入 /dungeon join 或 /dungeon key 後，提供副本名稱的自動補全
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join") ||
                    (args[0].equalsIgnoreCase("key") && sender.hasPermission("dungeonsystem.admin"))) {
                return plugin.getDungeonManager().getAvailableDungeonIds().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("revive") && sender.hasPermission("dungeonsystem.admin")) {
                // 為復活裝置提供選項
                List<String> options = Arrays.asList("normal", "advanced");
                return options.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return null;
    }
}
