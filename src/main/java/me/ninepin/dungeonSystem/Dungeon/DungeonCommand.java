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
            case "rank":
                handleRankCommand(player, args);
                break;
            case "permrank":
                handlePermRankCommand(player, args);
                break;
            case "globalrank":  // 新增的全局排行榜命令
                handleGlobalRankCommand(player, args);
                break;
            case "stats":       // 新增的個人統計命令
                handleStatsCommand(player, args);
                break;

            default:
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    private void handlePermRankCommand(Player player, String[] args) {
        if (!player.hasPermission("dungeonsystem.admin")) {
            player.sendMessage("§c你沒有權限執行此指令");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法:");
            player.sendMessage("§c  /dungeon permrank add <副本名稱> - 在當前位置新增永久排行榜");
            player.sendMessage("§c  /dungeon permrank remove <副本名稱> - 刪除副本所有永久排行榜");
            player.sendMessage("§c  /dungeon permrank remove-at <副本名稱> <位置ID> - 刪除特定位置的排行榜");
            player.sendMessage("§c  /dungeon permrank update <副本名稱> - 更新副本所有永久排行榜");
            player.sendMessage("§c  /dungeon permrank update-at <副本名稱> <位置ID> - 更新特定位置的排行榜");
            player.sendMessage("§c  /dungeon permrank updateall - 更新所有永久排行榜");
            player.sendMessage("§c  /dungeon permrank list - 列出所有永久排行榜");
            player.sendMessage("§c  /dungeon permrank list-locations <副本名稱> - 列出副本的所有排行榜位置");
            return;
        }

        String subCmd = args[1].toLowerCase();

        switch (subCmd) {
            case "add":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /dungeon permrank add <副本名稱>");
                    return;
                }
                handleAddPermRank(player, args[2]);
                break;

            case "remove":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /dungeon permrank remove <副本名稱>");
                    return;
                }
                handleRemovePermRank(player, args[2]);
                break;

            case "remove-at":
                if (args.length < 4) {
                    player.sendMessage("§c用法: /dungeon permrank remove-at <副本名稱> <位置ID>");
                    return;
                }
                handleRemovePermRankById(player, args[2], args[3]);
                break;

            case "update":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /dungeon permrank update <副本名稱>");
                    return;
                }
                handleUpdatePermRank(player, args[2]);
                break;

            case "update-at":
                if (args.length < 4) {
                    player.sendMessage("§c用法: /dungeon permrank update-at <副本名稱> <位置ID>");
                    return;
                }
                handleUpdatePermRankById(player, args[2], args[3]);
                break;

            case "updateall":
                handleUpdateAllPermRank(player);
                break;

            case "list":
                handleListPermRank(player);
                break;

            case "list-locations":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /dungeon permrank list-locations <副本名稱>");
                    return;
                }
                handleListPermRankLocations(player, args[2]);
                break;

            default:
                handlePermRankCommand(player, new String[]{"permrank"});
                break;
        }
    }

    private void handleGlobalRankCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /dungeon globalrank <temp|perm>");
            return;
        }

        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "temp":
                Location location = player.getLocation().add(0, 3, 0);
                plugin.getHologramManager().createGlobalRanking(location, player);
                player.sendMessage("§a已在您的上方顯示全服副本排行榜！");
                break;
            case "perm":
                if (!player.hasPermission("dungeonsystem.admin")) {
                    player.sendMessage("§c你沒有權限執行此指令");
                    return;
                }
                handlePermGlobalRank(player, args);
                break;
            default:
                player.sendMessage("§c用法: /dungeon globalrank <temp|perm>");
                break;
        }
    }

    private void handlePermGlobalRank(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /dungeon globalrank perm <add|remove|update|updateall|list>");
            return;
        }

        String action = args[2].toLowerCase();
        switch (action) {
            case "add":
                Location location = player.getLocation();
                String locationId = plugin.getHologramManager().createPermanentGlobalRanking(location);
                player.sendMessage("§a已創建永久全局排行榜！位置ID: §e" + locationId);
                break;
            case "remove":
                if (args.length < 4) {
                    player.sendMessage("§c用法: /dungeon globalrank perm remove <位置ID>");
                    return;
                }
                plugin.getHologramManager().removePermanentGlobalRanking(args[3]);
                player.sendMessage("§a已刪除位置ID §e" + args[3] + " §a的永久全局排行榜");
                break;
            case "update":
                if (args.length < 4) {
                    player.sendMessage("§c用法: /dungeon globalrank perm update <位置ID>");
                    return;
                }
                plugin.getHologramManager().updatePermanentGlobalRanking(args[3]);
                player.sendMessage("§a已更新位置ID §e" + args[3] + " §a的永久全局排行榜");
                break;
            case "updateall":
                Map<String, Location> locations = plugin.getHologramManager().getGlobalPermanentRankings();
                // 將迴圈中的變數改名，例如改為 id 或 existingId
                for (String id : locations.keySet()) {
                    plugin.getHologramManager().updatePermanentGlobalRanking(id);
                }
                player.sendMessage("§a已更新所有永久全局排行榜！共 " + locations.size() + " 個位置");
                break;
            case "list":
                Map<String, Location> allLocations = plugin.getHologramManager().getGlobalPermanentRankings();
                if (allLocations.isEmpty()) {
                    player.sendMessage("§e目前沒有永久全局排行榜");
                } else {
                    player.sendMessage("§6=== 永久全局排行榜列表 ===");
                    for (Map.Entry<String, Location> entry : allLocations.entrySet()) {
                        player.sendMessage("§e" + entry.getKey() + " §7- " + locationToString(entry.getValue()));
                    }
                }
                break;
            default:
                player.sendMessage("§c用法: /dungeon globalrank perm <add|remove|update|updateall|list>");
                break;
        }
    }

    private void handleStatsCommand(Player player, String[] args) {
        String targetPlayerName = args.length > 1 ? args[1] : player.getName();

        if (!targetPlayerName.equals(player.getName()) && !player.hasPermission("dungeonsystem.admin")) {
            player.sendMessage("§c你沒有權限查看其他玩家的統計");
            return;
        }

        org.bukkit.OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(targetPlayerName);
        if (!targetPlayer.hasPlayedBefore()) {
            player.sendMessage("§c找不到玩家: " + targetPlayerName);
            return;
        }

        me.ninepin.dungeonSystem.ranking.JsonDataManager.GlobalPlayerRankingData globalStats =
                plugin.getRankingManager().getPlayerGlobalStats(targetPlayer.getUniqueId());

        if (globalStats == null || globalStats.totalCompletions == 0) {
            player.sendMessage("§e該玩家還沒有任何副本記錄");
            return;
        }

        int globalRank = plugin.getRankingManager().getPlayerGlobalRank(targetPlayer.getUniqueId());
        String rankDisplay = globalRank > 0 ? "第" + globalRank + "名" : "未上榜";

        player.sendMessage("§6=== " + targetPlayerName + " 的副本統計 ===");
        player.sendMessage("§e全服排名: " + rankDisplay);
        player.sendMessage("§a總通關次數: " + globalStats.totalCompletions + "次");
        player.sendMessage("§6完成副本數: " + globalStats.getDungeonsCompletedCount() + "個");
    }

    /**
     * 處理列出副本的所有排行榜位置
     */
    private void handleListPermRankLocations(Player player, String dungeonId) {
        // 檢查副本是否存在
        Dungeon dungeon = findDungeonFlexible(dungeonId);
        if (dungeon == null) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        String actualDungeonId = dungeon.getId();
        Map<String, Location> locations = plugin.getHologramManager().getDungeonPermanentRankings(actualDungeonId);

        if (locations.isEmpty()) {
            player.sendMessage("§e副本 " + dungeon.getDisplayName() + " 沒有任何永久排行榜");
            player.sendMessage("§7使用 §e/dungeon permrank add " + dungeonId + " §7創建排行榜");
            return;
        }

        player.sendMessage("§6=== " + dungeon.getDisplayName() + " 排行榜位置 ===");
        player.sendMessage("§7副本ID: " + actualDungeonId);
        player.sendMessage("§7共 " + locations.size() + " 個排行榜位置:");

        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            String locationId = entry.getKey();
            Location location = entry.getValue();
            String locationStr = locationToString(location);

            // 獲取全息圖狀態
            String hologramName = plugin.getHologramManager().getPermanentHologramNameWithId(actualDungeonId, locationId);
            boolean hologramExists = eu.decentsoftware.holograms.api.DHAPI.getHologram(hologramName) != null;
            String status = hologramExists ? "§a正常" : "§c異常";

            player.sendMessage("§7  位置ID §e" + locationId + " §7- " + status);
            player.sendMessage("§7    位置: " + locationStr);
            player.sendMessage("§7    全息圖: " + hologramName);
        }

        player.sendMessage("§7");
        player.sendMessage("§7指令提示:");
        player.sendMessage("§7  §e/dungeon permrank add " + dungeonId + " §7- 新增位置");
        player.sendMessage("§7  §e/dungeon permrank remove-at " + dungeonId + " <位置ID> §7- 刪除特定位置");
        player.sendMessage("§7  §e/dungeon permrank update " + dungeonId + " §7- 更新所有位置");
    }

    /**
     * 處理新增永久排行榜
     */
    private void handleAddPermRank(Player player, String dungeonId) {
        // 檢查副本是否存在
        Dungeon dungeon = findDungeonFlexible(dungeonId);
        if (dungeon == null) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        String actualDungeonId = dungeon.getId();
        Location location = player.getLocation();

        // 在當前位置新增永久排行榜
        String locationId = plugin.getHologramManager().createPermanentRankingWithAutoId(actualDungeonId, location);

        int totalLocations = plugin.getHologramManager().getDungeonPermanentRankings(actualDungeonId).size();

        player.sendMessage("§a已為副本 §e" + dungeon.getDisplayName() + " §a新增永久排行榜");
        player.sendMessage("§7位置ID: §e" + locationId + " §7| 位置: " + locationToString(location));
        player.sendMessage("§7該副本現在共有 §e" + totalLocations + " §7個排行榜位置");
    }

    /**
     * 處理刪除副本所有永久排行榜
     */
    private void handleRemovePermRank(Player player, String dungeonId) {
        // 檢查副本是否存在
        Dungeon dungeon = findDungeonFlexible(dungeonId);
        if (dungeon == null) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        String actualDungeonId = dungeon.getId();
        Map<String, Location> locations = plugin.getHologramManager().getDungeonPermanentRankings(actualDungeonId);

        if (locations.isEmpty()) {
            player.sendMessage("§c副本 §e" + dungeon.getDisplayName() + " §c沒有任何永久排行榜");
            return;
        }

        // 刪除所有位置的排行榜
        int removedCount = 0;
        for (String locationId : locations.keySet()) {
            plugin.getHologramManager().removePermanentRankingById(actualDungeonId, locationId);
            removedCount++;
        }

        player.sendMessage("§a已刪除副本 §e" + dungeon.getDisplayName() + " §a的所有永久排行榜");
        player.sendMessage("§7共刪除了 §e" + removedCount + " §7個排行榜位置");
    }

    /**
     * 處理刪除特定位置的永久排行榜
     */
    private void handleRemovePermRankById(Player player, String dungeonId, String locationId) {
        // 檢查副本是否存在
        Dungeon dungeon = findDungeonFlexible(dungeonId);
        if (dungeon == null) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        String actualDungeonId = dungeon.getId();

        if (!plugin.getHologramManager().hasPermanentRankingAtLocation(actualDungeonId, locationId)) {
            player.sendMessage("§c副本 §e" + dungeon.getDisplayName() + " §c在位置ID §e" + locationId + " §c沒有永久排行榜");

            // 顯示可用的位置ID
            Set<String> availableIds = plugin.getHologramManager().getDungeonPermanentRankings(actualDungeonId).keySet();
            if (!availableIds.isEmpty()) {
                player.sendMessage("§7可用的位置ID: §e" + String.join(", ", availableIds));
            }
            return;
        }

        plugin.getHologramManager().removePermanentRankingById(actualDungeonId, locationId);

        int remainingLocations = plugin.getHologramManager().getDungeonPermanentRankings(actualDungeonId).size();

        player.sendMessage("§a已刪除副本 §e" + dungeon.getDisplayName() + " §a位置ID §e" + locationId + " §a的永久排行榜");
        player.sendMessage("§7該副本還剩 §e" + remainingLocations + " §7個排行榜位置");
    }

    /**
     * 處理更新副本所有永久排行榜
     */
    private void handleUpdatePermRank(Player player, String dungeonId) {
        // 檢查副本是否存在
        Dungeon dungeon = findDungeonFlexible(dungeonId);
        if (dungeon == null) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        String actualDungeonId = dungeon.getId();
        Map<String, Location> locations = plugin.getHologramManager().getDungeonPermanentRankings(actualDungeonId);

        if (locations.isEmpty()) {
            player.sendMessage("§c副本 §e" + dungeon.getDisplayName() + " §c沒有任何永久排行榜");
            player.sendMessage("§7使用 §e/dungeon permrank add " + dungeonId + " §7創建永久排行榜");
            return;
        }

        // 更新所有位置的永久排行榜
        int updatedCount = 0;
        for (String locationId : locations.keySet()) {
            plugin.getHologramManager().updatePermanentRankingById(actualDungeonId, locationId);
            updatedCount++;
        }

        player.sendMessage("§a已更新副本 §e" + dungeon.getDisplayName() + " §a的所有永久排行榜");
        player.sendMessage("§7共更新了 §e" + updatedCount + " §7個排行榜位置");
    }

    /**
     * 處理更新特定位置的永久排行榜
     */
    private void handleUpdatePermRankById(Player player, String dungeonId, String locationId) {
        // 檢查副本是否存在
        Dungeon dungeon = findDungeonFlexible(dungeonId);
        if (dungeon == null) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        String actualDungeonId = dungeon.getId();

        if (!plugin.getHologramManager().hasPermanentRankingAtLocation(actualDungeonId, locationId)) {
            player.sendMessage("§c副本 §e" + dungeon.getDisplayName() + " §c在位置ID §e" + locationId + " §c沒有永久排行榜");

            // 顯示可用的位置ID
            Set<String> availableIds = plugin.getHologramManager().getDungeonPermanentRankings(actualDungeonId).keySet();
            if (!availableIds.isEmpty()) {
                player.sendMessage("§7可用的位置ID: §e" + String.join(", ", availableIds));
            }
            return;
        }

        plugin.getHologramManager().updatePermanentRankingById(actualDungeonId, locationId);
        player.sendMessage("§a已更新副本 §e" + dungeon.getDisplayName() + " §a位置ID §e" + locationId + " §a的永久排行榜");
    }

    /**
     * 處理更新所有永久排行榜
     */
    private void handleUpdateAllPermRank(Player player) {
        Map<String, Integer> updatedDungeons = new HashMap<>();

        // 獲取所有有永久排行榜的副本
        for (String dungeonId : dungeonManager.getAllDungeons().keySet()) {
            Map<String, Location> locations = plugin.getHologramManager().getDungeonPermanentRankings(dungeonId);
            if (!locations.isEmpty()) {
                // 更新該副本的所有位置
                for (String locationId : locations.keySet()) {
                    plugin.getHologramManager().updatePermanentRankingById(dungeonId, locationId);
                }
                updatedDungeons.put(dungeonId, locations.size());
            }
        }

        if (updatedDungeons.isEmpty()) {
            player.sendMessage("§e沒有找到任何永久排行榜");
        } else {
            int totalUpdated = updatedDungeons.values().stream().mapToInt(Integer::intValue).sum();
            player.sendMessage("§a已更新 " + updatedDungeons.size() + " 個副本的 " + totalUpdated + " 個永久排行榜:");

            for (Map.Entry<String, Integer> entry : updatedDungeons.entrySet()) {
                Dungeon dungeon = dungeonManager.getDungeon(entry.getKey());
                String displayName = dungeon != null ? dungeon.getDisplayName() : entry.getKey();
                player.sendMessage("§7  - " + displayName + " (" + entry.getValue() + " 個位置)");
            }
        }
    }

    /**
     * 處理列出所有永久排行榜
     */
    private void handleListPermRank(Player player) {
        Map<String, Map<String, Location>> allRankings = new HashMap<>();

        // 獲取所有有永久排行榜的副本
        for (String dungeonId : dungeonManager.getAllDungeons().keySet()) {
            Map<String, Location> locations = plugin.getHologramManager().getDungeonPermanentRankings(dungeonId);
            if (!locations.isEmpty()) {
                allRankings.put(dungeonId, locations);
            }
        }

        if (allRankings.isEmpty()) {
            player.sendMessage("§e目前沒有任何永久排行榜");
            return;
        }

        int totalLocations = allRankings.values().stream().mapToInt(Map::size).sum();

        player.sendMessage("§6=== 永久排行榜列表 ===");
        player.sendMessage("§7共 " + allRankings.size() + " 個副本，" + totalLocations + " 個排行榜位置");

        for (Map.Entry<String, Map<String, Location>> entry : allRankings.entrySet()) {
            String dungeonId = entry.getKey();
            Map<String, Location> locations = entry.getValue();

            Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
            String displayName = dungeon != null ? dungeon.getDisplayName() : dungeonId;

            player.sendMessage("§e" + displayName + " §7(" + dungeonId + ") - " + locations.size() + " 個位置");

            for (Map.Entry<String, Location> locationEntry : locations.entrySet()) {
                String locationId = locationEntry.getKey();
                Location location = locationEntry.getValue();
                String locationStr = locationToString(location);
                player.sendMessage("§7  位置ID §e" + locationId + "§7: " + locationStr);
            }
        }
    }


    // 智能查找副本方法（支持基礎ID和實例ID）
    private Dungeon findDungeonFlexible(String dungeonId) {
        // 首先嘗試直接查找
        Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
        if (dungeon != null) {
            return dungeon;
        }

        // 如果沒有找到，查找相關實例
        for (Map.Entry<String, Dungeon> entry : dungeonManager.getAllDungeons().entrySet()) {
            String instanceId = entry.getKey();

            // 檢查是否是相關實例
            if (instanceId.startsWith(dungeonId + "_")) {
                // 檢查後綴是否為數字
                String suffix = instanceId.substring(dungeonId.length() + 1);
                try {
                    Integer.parseInt(suffix);
                    return entry.getValue(); // 返回第一個匹配的實例
                } catch (NumberFormatException e) {
                    // 如果不是數字後綴，繼續查找
                }
            }
        }

        return null;
    }

    // 位置轉字符串輔助方法
    private String locationToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "unknown";
        return String.format("%s,%.1f,%.1f,%.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleRankCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /dungeon rank <副本名稱>");
            return;
        }

        String dungeonId = args[1];

        // 檢查副本是否存在
        if (!dungeonManager.isDungeonAvailable(dungeonId)) {
            player.sendMessage("§c找不到名為 §e" + dungeonId + " §c的副本");
            return;
        }

        // 在玩家當前位置創建排行榜全息圖
        Location location = player.getLocation().add(0, 3, 0); // 在玩家上方3格
        plugin.getHologramManager().createOrUpdateRanking(dungeonId, location, player);

        player.sendMessage("§a已在您的上方顯示 §e" + dungeonId + " §a的排行榜");
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
        // 檢查玩家是否為隊長
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
        player.sendMessage("§e/dungeon rank <副本名稱> §7- 查看副本排行榜");
        player.sendMessage("§e/dungeon list §7- 列出所有可用副本");
        player.sendMessage("§e/dungeon leave §7- 離開當前副本");
        if (player.hasPermission("dungeonsystem.admin")) {
            player.sendMessage("§e/dungeon reload §7- 重新加載副本配置");
            player.sendMessage("§e/dungeon key <副本ID> [數量] §7- 獲取副本入場卷");
            player.sendMessage("§e/dungeon mob <副本ID> <怪物ID> <數量> <半徑> §7- 在當前位置添加怪物到副本配置");
            player.sendMessage("§e/dungeon mob <副本ID> <怪物ID> <數量> <半徑> [波次] §7- 添加怪物到副本");
            player.sendMessage("§7  普通副本不需要波次參數，波次副本需要指定波次");
            player.sendMessage("§e/dungeon permrank <add|remove|update|updateall|list> §7- 管理永久排行榜");
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

        if (args.length < 6) {
            player.sendMessage("§c用法:");
            player.sendMessage("§c  普通副本: /dungeon mob <副本ID> <怪物ID> <數量> <半徑> <類型> [等級]");
            player.sendMessage("§c  波次副本: /dungeon mob <副本ID> <怪物ID> <數量> <半徑> <類型> <波次> [等級]");
            player.sendMessage("§7  類型: NORMAL 或 BOSS");
            player.sendMessage("§7  等級參數可選，預設為 1");
            return;
        }

        String dungeonId = args[1];
        String mobId = args[2];

        int amount;
        double radius;
        String type;

        try {
            amount = Integer.parseInt(args[3]);
            radius = Double.parseDouble(args[4]);
            type = args[5].toUpperCase();

            if (amount < 1) {
                player.sendMessage("§c數量必須大於 0");
                return;
            }

            if (radius < 0) {
                player.sendMessage("§c半徑不能為負數");
                return;
            }

            if (!type.equals("NORMAL") && !type.equals("BOSS")) {
                player.sendMessage("§c怪物類型必須是 NORMAL 或 BOSS");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§c數量必須是整數，半徑必須是數字");
            return;
        }

        // 直接檢查副本實例是否存在
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
            if (args.length < 7) {
                player.sendMessage("§c波次副本需要指定波次！");
                player.sendMessage("§c用法: /dungeon mob <副本ID> <怪物ID> <數量> <半徑> <類型> <波次> [等級]");
                player.sendMessage("§e副本 " + dungeonId + " 共有 " + waveDungeon.getTotalWaves() + " 波");
                return;
            }

            int wave;
            try {
                wave = Integer.parseInt(args[6]);
                if (wave < 1 || wave > waveDungeon.getTotalWaves()) {
                    player.sendMessage("§c波次必須在 1 到 " + waveDungeon.getTotalWaves() + " 之間");
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c波次必須是整數");
                return;
            }

            // 解析等級參數（可選）
            int level = 1; // 預設等級
            if (args.length >= 8) {
                try {
                    level = Integer.parseInt(args[7]);
                    if (level < 1) {
                        player.sendMessage("§c等級必須大於 0，使用預設等級 1");
                        level = 1;
                    } else if (level > 100) {
                        player.sendMessage("§e等級過高 (" + level + ")，請確認這是您想要的等級");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c等級必須是整數，使用預設等級 1");
                    level = 1;
                }
            }

            // 添加到波次副本（使用新的方法簽名）
            boolean success = dungeonManager.addMobToWaveDungeon(dungeonId, mobId, location, amount, radius, wave, level, type);

            if (success) {
                String typeDisplay = type.equals("BOSS") ? "§cBOSS" : "§a普通";
                player.sendMessage("§a成功在波次副本 §e" + dungeonId + " §a的第 §e" + wave + " §a波添加" + typeDisplay + "§a怪物 §e" + mobId);
                player.sendMessage("§a數量: " + amount + ", 半徑: " + radius + ", 等級: " + level + ", 類型: " + type);
                player.sendMessage("§7位置: " + dungeonManager.locationToString(location));

                // 提供縮放信息
                if (type.equals("NORMAL")) {
                    player.sendMessage("§7註: 普通怪物數量會根據進入副本的玩家數量進行縮放");
                } else if (type.equals("BOSS")) {
                    player.sendMessage("§7註: BOSS怪物等級會根據進入副本的玩家數量進行調整");
                }
            } else {
                player.sendMessage("§c添加怪物失敗，請檢查控制台錯誤信息");
            }
        } else {
            // 普通副本
            // 解析等級參數（可選）
            int level = 1; // 預設等級
            if (args.length >= 7) {
                try {
                    level = Integer.parseInt(args[6]);
                    if (level < 1) {
                        player.sendMessage("§c等級必須大於 0，使用預設等級 1");
                        level = 1;
                    } else if (level > 100) {
                        player.sendMessage("§e等級過高 (" + level + ")，請確認這是您想要的等級");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c等級必須是整數，使用預設等級 1");
                    level = 1;
                }
            }

            // 使用新的方法簽名添加怪物
            boolean success = dungeonManager.addMobToDungeon(dungeonId, mobId, location, amount, radius, level, type);

            if (success) {
                String typeDisplay = type.equals("BOSS") ? "§cBOSS" : "§a普通";
                player.sendMessage("§a成功在普通副本 §e" + dungeonId + " §a添加" + typeDisplay + "§a怪物 §e" + mobId);
                player.sendMessage("§a數量: " + amount + ", 半徑: " + radius + ", 等級: " + level + ", 類型: " + type);
                player.sendMessage("§7位置: " + dungeonManager.locationToString(location));

                // 提供縮放信息
                if (type.equals("NORMAL")) {
                    player.sendMessage("§7註: 普通怪物數量會根據進入副本的玩家數量進行縮放");
                } else if (type.equals("BOSS")) {
                    player.sendMessage("§7註: BOSS怪物等級會根據進入副本的玩家數量進行調整");
                }
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
            List<String> completions = new ArrayList<>(Arrays.asList("join", "list", "leave", "rank", "globalrank", "stats"));
            if (sender.hasPermission("dungeonsystem.admin")) {
                completions.add("reload");
                completions.add("key");
                completions.add("mob");
                completions.add("permrank");
                if (plugin.isRevivalSystemEnabled()) {
                    completions.add("revive");
                }
            }

            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join") ||
                    args[0].equalsIgnoreCase("rank") ||
                    (args[0].equalsIgnoreCase("key") && sender.hasPermission("dungeonsystem.admin"))) {
                return plugin.getDungeonManager().getAvailableDungeonIds().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
                return plugin.getDungeonManager().getAllDungeons().keySet().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("permrank") && sender.hasPermission("dungeonsystem.admin")) {
                return Arrays.asList("add", "remove", "remove-at", "update", "update-at", "updateall", "list", "list-locations").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("globalrank")) {
                List<String> options = new ArrayList<>(Arrays.asList("temp"));
                if (sender.hasPermission("dungeonsystem.admin")) {
                    options.add("perm");
                }
                return options.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("stats")) {
                if (sender.hasPermission("dungeonsystem.admin")) {
                    return plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (args[0].equalsIgnoreCase("revive") && sender.hasPermission("dungeonsystem.admin")
                    && plugin.isRevivalSystemEnabled()) {
                List<String> options = Arrays.asList("normal", "advanced");
                return options.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // globalrank perm 子命令
        if (args.length == 3 && args[0].equalsIgnoreCase("globalrank") &&
                args[1].equalsIgnoreCase("perm") && sender.hasPermission("dungeonsystem.admin")) {
            return Arrays.asList("add", "remove", "update", "updateall", "list").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // globalrank perm remove/update 位置ID補全
        if (args.length == 4 && args[0].equalsIgnoreCase("globalrank") &&
                args[1].equalsIgnoreCase("perm") && sender.hasPermission("dungeonsystem.admin")) {
            if (args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("update")) {
                Set<String> locationIds = plugin.getHologramManager().getGlobalPermanentRankings().keySet();
                return new ArrayList<>(locationIds).stream()
                        .filter(id -> id.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // permrank 子命令副本名稱補全
        if (args.length == 3 && args[0].equalsIgnoreCase("permrank") && sender.hasPermission("dungeonsystem.admin")) {
            if (args[1].equalsIgnoreCase("add") ||
                    args[1].equalsIgnoreCase("remove") ||
                    args[1].equalsIgnoreCase("update") ||
                    args[1].equalsIgnoreCase("list-locations") ||
                    args[1].equalsIgnoreCase("remove-at") ||
                    args[1].equalsIgnoreCase("update-at")) {
                return plugin.getDungeonManager().getAvailableDungeonIds().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // permrank 位置ID補全
        if (args.length == 4 && args[0].equalsIgnoreCase("permrank") && sender.hasPermission("dungeonsystem.admin")) {
            if (args[1].equalsIgnoreCase("remove-at") || args[1].equalsIgnoreCase("update-at")) {
                String dungeonId = args[2];
                Dungeon dungeon = findDungeonFlexible(dungeonId);
                if (dungeon != null) {
                    Set<String> locationIds = plugin.getHologramManager().getDungeonPermanentRankings(dungeon.getId()).keySet();
                    return new ArrayList<>(locationIds).stream()
                            .filter(id -> id.toLowerCase().startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        // mob 命令補全
        if (args.length == 3 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            return getMythicMobIds().stream()
                    .filter(mobId -> mobId.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            return Arrays.asList("1", "2", "3", "4", "5", "10");
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            return Arrays.asList("0", "1", "2", "3", "5", "10");
        }

        if (args.length == 6 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            return Arrays.asList("NORMAL", "BOSS").stream()
                    .filter(type -> type.toLowerCase().startsWith(args[5].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 7 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            String dungeonId = args[1];
            Dungeon dungeon = plugin.getDungeonManager().getDungeon(dungeonId);
            if (dungeon instanceof WaveDungeon) {
                WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                List<String> waves = new ArrayList<>();
                for (int i = 1; i <= waveDungeon.getTotalWaves(); i++) {
                    waves.add(String.valueOf(i));
                }
                return waves.stream()
                        .filter(wave -> wave.startsWith(args[6]))
                        .collect(Collectors.toList());
            }
        }

        if ((args.length == 7 || args.length == 8) && args[0].equalsIgnoreCase("mob") && sender.hasPermission("dungeonsystem.admin")) {
            String dungeonId = args[1];
            Dungeon dungeon = plugin.getDungeonManager().getDungeon(dungeonId);

            boolean isLevelParam = false;
            if (dungeon instanceof WaveDungeon && args.length == 8) {
                isLevelParam = true;
            } else if (!(dungeon instanceof WaveDungeon) && args.length == 7) {
                isLevelParam = true;
            }

            if (isLevelParam) {
                return Arrays.asList("1", "5", "10", "15", "20", "25", "30", "50").stream()
                        .filter(level -> level.startsWith(args[args.length - 1]))
                        .collect(Collectors.toList());
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
