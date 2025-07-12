package me.ninepin.dungeonSystem.ranking;

// 使用示例 - 在您的命令處理類中添加以下代碼

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 處理全局排行榜相關命令的示例
 */
public class RankingCommands {

    private final DungeonSystem plugin;
    private final RankingHologramManager hologramManager;

    public RankingCommands(DungeonSystem plugin) {
        this.plugin = plugin;
        this.hologramManager = plugin.getHologramManager(); // 假設您有這個getter方法
    }

    /**
     * 處理 /dungeon ranking global 命令
     */
    public void handleGlobalRankingCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /dungeon ranking global [temp|perm] [create|remove|update]");
            return;
        }

        String type = args[1]; // temp 或 perm
        String action = args[2]; // create, remove, update

        switch (type.toLowerCase()) {
            case "temp":
                handleTempGlobalRanking(player, action);
                break;
            case "perm":
                handlePermGlobalRanking(player, action, args);
                break;
            default:
                player.sendMessage("§c無效的類型！使用 temp 或 perm");
                break;
        }
    }

    /**
     * 處理臨時全局排行榜
     */
    private void handleTempGlobalRanking(Player player, String action) {
        switch (action.toLowerCase()) {
            case "create":
                Location loc = player.getLocation().clone().add(0, 2, 0);
                hologramManager.createGlobalRanking(loc, player);
                player.sendMessage("§a已在您的位置創建臨時全局排行榜！");
                break;
            case "remove":
                hologramManager.removeGlobalRanking();
                player.sendMessage("§a已移除臨時全局排行榜！");
                break;
            default:
                player.sendMessage("§c臨時排行榜只支持 create 和 remove 操作");
                break;
        }
    }

    /**
     * 處理永久全局排行榜
     */
    private void handlePermGlobalRanking(Player player, String action, String[] args) {
        switch (action.toLowerCase()) {
            case "create":
                Location loc = player.getLocation().clone().add(0, 2, 0);
                String locationId = hologramManager.createPermanentGlobalRanking(loc);
                player.sendMessage("§a已創建永久全局排行榜！位置ID: §e" + locationId);
                break;

            case "remove":
                if (args.length < 4) {
                    player.sendMessage("§c用法: /dungeon ranking global perm remove <位置ID>");
                    showGlobalRankingLocations(player);
                    return;
                }
                String removeId = args[3];
                hologramManager.removePermanentGlobalRanking(removeId);
                player.sendMessage("§a已移除永久全局排行榜！位置ID: §e" + removeId);
                break;

            case "update":
                if (args.length < 4) {
                    player.sendMessage("§c用法: /dungeon ranking global perm update <位置ID>");
                    showGlobalRankingLocations(player);
                    return;
                }
                String updateId = args[3];
                hologramManager.updatePermanentGlobalRanking(updateId);
                player.sendMessage("§a已更新永久全局排行榜！位置ID: §e" + updateId);
                break;

            case "list":
                showGlobalRankingLocations(player);
                break;

            default:
                player.sendMessage("§c永久排行榜支持操作: create, remove, update, list");
                break;
        }
    }

    /**
     * 顯示所有全局排行榜位置
     */
    private void showGlobalRankingLocations(Player player) {
        Map<String, Location> locations = hologramManager.getGlobalPermanentRankings();

        if (locations.isEmpty()) {
            player.sendMessage("§e目前沒有永久全局排行榜");
            return;
        }

        player.sendMessage("§6=== 永久全局排行榜列表 ===");
        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            String locationId = entry.getKey();
            Location loc = entry.getValue();
            String locStr = String.format("§7%s, %.1f, %.1f, %.1f",
                    loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
            player.sendMessage("§e位置ID: §a" + locationId + " §7- " + locStr);
        }
    }

    /**
     * 處理查看個人全局統計命令
     */
    public void handlePlayerGlobalStats(Player player, String[] args) {
        String targetPlayerName = args.length > 0 ? args[0] : player.getName();

        // 嘗試通過名稱找到玩家UUID（您可能需要調整這部分邏輯）
        UUID targetUUID = getPlayerUUID(targetPlayerName);
        if (targetUUID == null) {
            player.sendMessage("§c找不到玩家: " + targetPlayerName);
            return;
        }

        JsonDataManager.GlobalPlayerRankingData stats =
                plugin.getRankingManager().getPlayerGlobalStats(targetUUID);

        if (stats == null) {
            player.sendMessage("§e玩家 " + targetPlayerName + " 還沒有任何副本記錄");
            return;
        }

        player.sendMessage("§6=== " + targetPlayerName + " 的全服統計 ===");
        player.sendMessage("§a總通關次數: §f" + stats.totalCompletions + "次");
        player.sendMessage("§a完成副本數: §f" + stats.getDungeonsCompletedCount() + "個");

        if (stats.lastCompleted > 0) {
            String lastTime = formatTime(stats.lastCompleted);
            player.sendMessage("§a最後完成: §f" + lastTime);
        }

        // 顯示該玩家在全局排行榜中的排名
        List<JsonDataManager.GlobalPlayerRankingData> globalRanking =
                plugin.getRankingManager().getGlobalRanking(100); // 獲取前100名來計算排名

        int rank = -1;
        for (int i = 0; i < globalRanking.size(); i++) {
            if (globalRanking.get(i).playerName.equals(targetPlayerName)) {
                rank = i + 1;
                break;
            }
        }

        if (rank > 0) {
            player.sendMessage("§a全服排名: §e第" + rank + "名");
        } else {
            player.sendMessage("§7未進入前100名");
        }
    }

    // 輔助方法
    private UUID getPlayerUUID(String playerName) {
        // 這裡需要實現根據玩家名稱獲取UUID的邏輯
        // 可以使用 Bukkit.getOfflinePlayer(playerName).getUniqueId()
        // 或者您自己的玩家數據管理方式
        return Bukkit.getOfflinePlayer(playerName).getUniqueId();
    }

    private String formatTime(long timestamp) {
        // 實現時間格式化邏輯
        long diff = System.currentTimeMillis() - timestamp;
        long days = diff / (24 * 60 * 60 * 1000);
        if (days > 0) {
            return days + "天前";
        }
        long hours = diff / (60 * 60 * 1000);
        if (hours > 0) {
            return hours + "小時前";
        }
        long minutes = diff / (60 * 1000);
        return minutes + "分鐘前";
    }
}
