package me.ninepin.dungeonSystem.ranking;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DungeonRankingPlaceholder extends PlaceholderExpansion {
    private final DungeonSystem plugin;

    public DungeonRankingPlaceholder(DungeonSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "dungeonrank";
    }

    @NotNull
    @Override
    public String getAuthor() {
        return "YourName";
    }

    @NotNull
    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        // 檢查是否為全局排行榜占位符
        if (params.startsWith("global_")) {
            return handleGlobalPlaceholder(player, params.substring(7)); // 移除 "global_" 前綴
        }

        // 原有的副本排行榜邏輯
        // 改為從後面分割，最後一個底線後的部分是 type
        int lastUnderscoreIndex = params.lastIndexOf("_");
        if (lastUnderscoreIndex == -1) return "";

        String dungeonId = params.substring(0, lastUnderscoreIndex);
        String type = params.substring(lastUnderscoreIndex + 1);

        // first case 不需要玩家參數，可以直接處理
        if ("first".equals(type)) {
            return getFirstPlayer(dungeonId);
        }

        // 其他 case 需要玩家參數
        if (player == null) return "";

        switch (type) {
            case "rank":
                return getPlayerRank(player, dungeonId);
            case "count":
                return getPlayerCount(player, dungeonId);
            case "info":
                return getPlayerInfo(player, dungeonId);
            default:
                return "";
        }
    }

    // ==================== 全局排行榜占位符處理 ====================

    /**
     * 處理全局排行榜相關的占位符
     */
    private String handleGlobalPlaceholder(Player player, String type) {
        try {
            switch (type) {
                case "first":
                    return getGlobalFirstPlayer();
                case "rank":
                    return player != null ? getPlayerGlobalRank(player) : "";
                case "count":
                    return player != null ? getPlayerGlobalCount(player) : "";
                case "dungeons":
                    return player != null ? getPlayerCompletedDungeons(player) : "";
                case "info":
                    return player != null ? getPlayerGlobalInfo(player) : "";
                case "top":
                    return getGlobalTopPlayers();
                default:
                    return "";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("處理全局占位符時出錯 (" + type + "): " + e.getMessage());
            return "錯誤";
        }
    }

    /**
     * 獲取全服排行榜第一名玩家
     * 占位符: %dungeonrank_global_first%
     */
    private String getGlobalFirstPlayer() {
        try {
            List<JsonDataManager.GlobalPlayerRankingData> ranking =
                    plugin.getRankingManager().getGlobalRanking(1);

            if (!ranking.isEmpty()) {
                JsonDataManager.GlobalPlayerRankingData firstPlayer = ranking.get(0);
                return firstPlayer.playerName;
            } else {
                return "無紀錄";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("獲取全服第一名時出錯: " + e.getMessage());
            return "錯誤";
        }
    }

    /**
     * 獲取玩家的全服排名
     * 占位符: %dungeonrank_global_rank%
     */
    private String getPlayerGlobalRank(Player player) {
        try {
            List<JsonDataManager.GlobalPlayerRankingData> allPlayers =
                    plugin.getRankingManager().getGlobalRanking(Integer.MAX_VALUE);

            for (int i = 0; i < allPlayers.size(); i++) {
                if (allPlayers.get(i).playerName.equals(player.getName())) {
                    return String.valueOf(i + 1);
                }
            }
            return "未上榜";
        } catch (Exception e) {
            plugin.getLogger().warning("獲取玩家全服排名時出錯: " + e.getMessage());
            return "錯誤";
        }
    }

    /**
     * 獲取玩家的總完成次數
     * 占位符: %dungeonrank_global_count%
     */
    private String getPlayerGlobalCount(Player player) {
        try {
            JsonDataManager.GlobalPlayerRankingData globalData =
                    plugin.getRankingManager().getPlayerGlobalStats(player.getUniqueId());
            return globalData != null ? String.valueOf(globalData.totalCompletions) : "0";
        } catch (Exception e) {
            plugin.getLogger().warning("獲取玩家總完成次數時出錯: " + e.getMessage());
            return "0";
        }
    }

    /**
     * 獲取玩家完成的副本數量
     * 占位符: %dungeonrank_global_dungeons%
     */
    private String getPlayerCompletedDungeons(Player player) {
        try {
            JsonDataManager.GlobalPlayerRankingData globalData =
                    plugin.getRankingManager().getPlayerGlobalStats(player.getUniqueId());
            return globalData != null ? String.valueOf(globalData.getDungeonsCompletedCount()) : "0";
        } catch (Exception e) {
            plugin.getLogger().warning("獲取玩家完成副本數時出錯: " + e.getMessage());
            return "0";
        }
    }

    /**
     * 獲取玩家的全服詳細信息
     * 占位符: %dungeonrank_global_info%
     */
    private String getPlayerGlobalInfo(Player player) {
        try {
            JsonDataManager.GlobalPlayerRankingData globalData =
                    plugin.getRankingManager().getPlayerGlobalStats(player.getUniqueId());

            if (globalData != null) {
                String rank = getPlayerGlobalRank(player);
                return "§b" + player.getName() + " §7全服排名:§e#" + rank +
                        " §7總計:§a" + globalData.totalCompletions + "次 " +
                        "§7副本:§6" + globalData.getDungeonsCompletedCount() + "個";
            } else {
                return "§b" + player.getName() + " §7尚未攻略任何副本";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("獲取玩家全服資訊時出錯: " + e.getMessage());
            return "§b" + player.getName() + " §7資料載入中...";
        }
    }

    /**
     * 獲取全服前三名簡要信息（用於小型顯示）
     * 占位符: %dungeonrank_global_top%
     */
    private String getGlobalTopPlayers() {
        try {
            List<JsonDataManager.GlobalPlayerRankingData> ranking =
                    plugin.getRankingManager().getGlobalRanking(3);

            if (ranking.isEmpty()) {
                return "§7暫無排行榜數據";
            }

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < ranking.size(); i++) {
                JsonDataManager.GlobalPlayerRankingData data = ranking.get(i);
                String rankColor = i == 0 ? "§6" : i == 1 ? "§d" : "§9";

                if (i > 0) result.append(" §7| ");
                result.append(rankColor).append(data.playerName)
                        .append("§f:§a").append(data.totalCompletions);
            }

            return result.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("獲取前三名時出錯: " + e.getMessage());
            return "§c數據錯誤";
        }
    }

    // ==================== 原有的副本排行榜方法 ====================

    private String getFirstPlayer(String dungeonId) {
        try {
            List<JsonDataManager.PlayerRankingData> ranking =
                    plugin.getRankingManager().getDungeonRanking(dungeonId, 1);

            if (!ranking.isEmpty()) {
                JsonDataManager.PlayerRankingData firstPlayer = ranking.get(0);
                return firstPlayer.playerName;
            } else {
                return "無紀錄";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("獲取排名第一玩家時出錯: " + e.getMessage());
            return "錯誤";
        }
    }

    private String getPlayerRank(Player player, String dungeonId) {
        try {
            List<JsonDataManager.PlayerRankingData> allPlayers =
                    plugin.getRankingManager().getDungeonRanking(dungeonId, Integer.MAX_VALUE);
            // 使用 HologramManager 的 calculatePlayerRank 方法
            int rank = plugin.getHologramManager().calculatePlayerRank(allPlayers, player.getName());
            return rank > 0 ? String.valueOf(rank) : "未上榜";
        } catch (Exception e) {
            plugin.getLogger().warning("獲取玩家排名時出錯: " + e.getMessage());
            return "錯誤";
        }
    }

    private String getPlayerCount(Player player, String dungeonId) {
        try {
            JsonDataManager.PlayerRankingData playerData =
                    plugin.getRankingManager().getPlayerData(player.getUniqueId(), dungeonId);
            return playerData != null ? String.valueOf(playerData.completionCount) : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String getPlayerInfo(Player player, String dungeonId) {
        try {
            JsonDataManager.PlayerRankingData playerData =
                    plugin.getRankingManager().getPlayerData(player.getUniqueId(), dungeonId);

            if (playerData != null) {
                String rank = getPlayerRank(player, dungeonId);
                return "§b" + player.getName() + " §7排名:§e#" + rank +
                        " §7完成:§a" + playerData.completionCount + "次";
            } else {
                return "§b" + player.getName() + " §7尚未攻略此副本";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("獲取玩家資訊時出錯: " + e.getMessage());
            return "§b" + player.getName() + " §7資料載入中...";
        }
    }
}