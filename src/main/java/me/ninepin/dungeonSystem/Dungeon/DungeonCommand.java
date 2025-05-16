package me.ninepin.dungeonSystem.Dungeon;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
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
            case "mob":
                handleMobCommand(player, args);
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
        // 檢查復活系統是否啟用
        if (!plugin.isRevivalSystemEnabled()) {
            player.sendMessage("§c復活系統目前已被禁用，無法使用此命令");
            return;
        }

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
            player.sendMessage("§e/dungeon mob <副本ID> <怪物ID> <數量> <半徑> §7- 在當前位置添加怪物到副本配置");
            player.sendMessage("§e/dungeon mob <副本ID> <怪物ID> <數量> <半徑> [波次] §7- 添加怪物到副本");
            player.sendMessage("§7  普通副本不需要波次參數，波次副本需要指定波次");
            // 只有在復活系統啟用時才顯示復活裝置相關命令
            if (plugin.isRevivalSystemEnabled()) {
                player.sendMessage("§e/dungeon revive <normal|advanced> [數量] §7- 獲取復活裝置");
            }
        }
    }

    private void handleMobCommand(Player player, String[] args) {
        if (!player.hasPermission("dungeonsystem.admin")) {
            player.sendMessage("§c你沒有權限執行此指令");
            return;
        }

        if (args.length < 5) {
            player.sendMessage("§c用法:");
            player.sendMessage("§c  普通副本: /dungeon mob <副本ID> <怪物ID> <數量> <半徑>");
            player.sendMessage("§c  波次副本: /dungeon mob <副本ID> <怪物ID> <數量> <半徑> <波次>");
            return;
        }

        String dungeonId = args[1];
        String mobId = args[2];

        int amount;
        double radius;
        try {
            amount = Integer.parseInt(args[3]);
            radius = Double.parseDouble(args[4]);

            if (amount < 1) {
                player.sendMessage("§c數量必須大於 0");
                return;
            }

            if (radius < 0) {
                player.sendMessage("§c半徑不能為負數");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§c數量必須是整數，半徑必須是數字");
            return;
        }

        // 直接檢查副本實例是否存在，而不是使用 isDungeonAvailable
        Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        Location location = player.getLocation();

        // 檢查是否為波次副本
        if (dungeon instanceof WaveDungeon) {
            WaveDungeon waveDungeon = (WaveDungeon) dungeon;

            // 波次副本需要指定波次
            if (args.length < 6) {
                player.sendMessage("§c波次副本需要指定波次！");
                player.sendMessage("§c用法: /dungeon mob <副本ID> <怪物ID> <數量> <半徑> <波次>");
                player.sendMessage("§e副本 " + dungeonId + " 共有 " + waveDungeon.getTotalWaves() + " 波");
                return;
            }

            int wave;
            try {
                wave = Integer.parseInt(args[5]);
                if (wave < 1 || wave > waveDungeon.getTotalWaves()) {
                    player.sendMessage("§c波次必須在 1 到 " + waveDungeon.getTotalWaves() + " 之間");
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c波次必須是整數");
                return;
            }

            // 添加到波次副本
            boolean success = dungeonManager.addMobToWaveDungeon(dungeonId, mobId, location, amount, radius, wave);

            if (success) {
                player.sendMessage("§a成功在波次副本 §e" + dungeonId + " §a的第 §e" + wave + " §a波添加怪物 §e" + mobId);
                player.sendMessage("§a數量: " + amount + ", 半徑: " + radius);
                player.sendMessage("§7位置: " + dungeonManager.locationToString(location));
            } else {
                player.sendMessage("§c添加怪物失敗，請檢查控制台錯誤信息");
            }
        } else {
            // 普通副本
            if (args.length >= 6) {
                player.sendMessage("§e這是普通副本，不需要指定波次");
            }

            boolean success = dungeonManager.addMobToDungeon(dungeonId, mobId, location, amount, radius);

            if (success) {
                player.sendMessage("§a成功在普通副本 §e" + dungeonId + " §a添加怪物 §e" + mobId);
                player.sendMessage("§a數量: " + amount + ", 半徑: " + radius);
                player.sendMessage("§7位置: " + dungeonManager.locationToString(location));
            } else {
                player.sendMessage("§c添加怪物失敗，請檢查控制台錯誤信息");
            }
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
                completions.add("mob");
                // 只有在復活系統啟用時才顯示 revive 選項
                if (plugin.isRevivalSystemEnabled()) {
                    completions.add("revive");
                }
            }

            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // 當輸入 /dungeon join、/dungeon key 或 /dungeon mob 後，提供副本名稱的自動補全
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join") ||
                    (args[0].equalsIgnoreCase("key") && sender.hasPermission("dungeonsystem.admin"))) {
                // 對於 join 和 key 指令，使用基礎副本ID（不包含實例後綴）
                return plugin.getDungeonManager().getAvailableDungeonIds().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
                // 對於 mob 指令，使用所有副本實例ID（包含實例後綴）
                return plugin.getDungeonManager().getAllDungeons().keySet().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("revive") && sender.hasPermission("dungeonsystem.admin")
                    && plugin.isRevivalSystemEnabled()) {
                // 為復活裝置提供選項
                List<String> options = Arrays.asList("normal", "advanced");
                return options.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // 為 /dungeon mob 指令提供 MythicMobs 怪物ID的自動補全
        if (args.length == 3 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            return getMythicMobIds().stream()
                    .filter(mobId -> mobId.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // 為 /dungeon mob 指令提供數量的建議
        if (args.length == 4 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            return Arrays.asList("1", "2", "3", "4", "5", "10");
        }

        // 為 /dungeon mob 指令提供半徑的建議
        if (args.length == 5 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            return Arrays.asList("0", "1", "2", "3", "5", "10");
        }

        // 為波次副本的 /dungeon mob 指令提供波次編號的自動補全
        if (args.length == 6 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            String dungeonId = args[1];
            // 檢查副本是否存在，使用實際的副本實例ID
            Dungeon dungeon = plugin.getDungeonManager().getDungeon(dungeonId);
            if (dungeon instanceof WaveDungeon) {
                WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                List<String> waves = new ArrayList<>();
                for (int i = 1; i <= waveDungeon.getTotalWaves(); i++) {
                    waves.add(String.valueOf(i));
                }
                return waves;
            }
        }

        return null;
    }

    /**
     * 獲取所有 MythicMobs 怪物ID
     *
     * @return MythicMobs 怪物ID列表
     */
    private List<String> getMythicMobIds() {
        List<String> mobIds = new ArrayList<>();

        try {
            // 使用 MythicBukkit API 獲取所有怪物類型
            io.lumine.mythic.bukkit.MythicBukkit mythicBukkit = io.lumine.mythic.bukkit.MythicBukkit.inst();
            if (mythicBukkit != null && mythicBukkit.getMobManager() != null) {
                // 使用正確的方法 getMobNames() 獲取所有怪物名稱
                Collection<String> mobNames = mythicBukkit.getMobManager().getMobNames();
                mobIds.addAll(mobNames);
            }
        } catch (Exception e) {
            // 如果無法獲取 MythicMobs 的怪物列表，返回空列表
            plugin.getLogger().warning("無法獲取 MythicMobs 怪物列表: " + e.getMessage());
            e.printStackTrace();
        }

        // 如果無法從 MythicBukkit 獲取，提供一些常見的怪物ID作為後備
        if (mobIds.isEmpty()) {
            mobIds.addAll(Arrays.asList(
                    "Skeleton", "Zombie", "Spider", "Creeper", "Enderman",
                    "Witch", "Blaze", "Ghast", "Slime", "Villager"
            ));
        }

        return mobIds;
    }
}
