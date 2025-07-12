package me.ninepin.dungeonSystem.ranking;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class DungeonRankingManager {
    private final DungeonSystem plugin;
    private final me.ninepin.dungeonSystem.ranking.JsonDataManager dataManager;

    public DungeonRankingManager(DungeonSystem plugin) {
        this.plugin = plugin;
        this.dataManager = new me.ninepin.dungeonSystem.ranking.JsonDataManager(plugin);
    }

    // ==================== 副本排行榜功能 ====================

    /**
     * 記錄玩家完成副本
     */
    public void recordCompletion(UUID playerId, String dungeonId, String playerName) {
        dataManager.recordCompletion(playerId, dungeonId, playerName);
    }

    /**
     * 記錄玩家完成副本（重載方法，自動獲取玩家名稱）
     */
    public void recordCompletion(Player player, String dungeonId) {
        recordCompletion(player.getUniqueId(), dungeonId, player.getName());
    }

    /**
     * 獲取副本排行榜
     */
    public List<me.ninepin.dungeonSystem.ranking.JsonDataManager.PlayerRankingData> getDungeonRanking(String dungeonId, int limit) {
        return dataManager.getDungeonRanking(dungeonId, limit);
    }

    /**
     * 獲取玩家數據
     */
    public me.ninepin.dungeonSystem.ranking.JsonDataManager.PlayerRankingData getPlayerData(UUID playerId, String dungeonId) {
        return dataManager.getPlayerData(playerId, dungeonId);
    }

    // ==================== 全局排行榜功能 ====================

    /**
     * 獲取全局排行榜 - 所有玩家的總副本通關次數
     */
    public List<me.ninepin.dungeonSystem.ranking.JsonDataManager.GlobalPlayerRankingData> getGlobalRanking(int limit) {
        return dataManager.getGlobalRanking(limit);
    }

    /**
     * 獲取特定玩家的全局統計數據
     */
    public me.ninepin.dungeonSystem.ranking.JsonDataManager.GlobalPlayerRankingData getPlayerGlobalStats(UUID playerId) {
        return dataManager.getPlayerGlobalStats(playerId);
    }

    /**
     * 獲取特定玩家的全局統計數據（重載方法，使用 Player 對象）
     */
    public me.ninepin.dungeonSystem.ranking.JsonDataManager.GlobalPlayerRankingData getPlayerGlobalStats(Player player) {
        return getPlayerGlobalStats(player.getUniqueId());
    }

    // ==================== 工具方法 ====================

    /**
     * 獲取玩家在全局排行榜中的排名
     */
    public int getPlayerGlobalRank(UUID playerId) {
        List<me.ninepin.dungeonSystem.ranking.JsonDataManager.GlobalPlayerRankingData> globalRanking =
                getGlobalRanking(Integer.MAX_VALUE);

        String playerIdStr = playerId.toString();
        for (int i = 0; i < globalRanking.size(); i++) {
            // 需要通過某種方式匹配玩家，這裡假設可以通過玩家名稱匹配
            // 如果 GlobalPlayerRankingData 需要包含 playerId，可以修改數據結構
            me.ninepin.dungeonSystem.ranking.JsonDataManager.GlobalPlayerRankingData globalData =
                    getPlayerGlobalStats(playerId);

            if (globalData != null) {
                for (int j = 0; j < globalRanking.size(); j++) {
                    if (globalRanking.get(j).playerName.equals(globalData.playerName)) {
                        return j + 1;
                    }
                }
            }
        }
        return -1; // 未上榜
    }

    /**
     * 獲取玩家在全局排行榜中的排名（重載方法，使用 Player 對象）
     */
    public int getPlayerGlobalRank(Player player) {
        return getPlayerGlobalRank(player.getUniqueId());
    }

    /**
     * 獲取玩家在特定副本的排名
     */
    public int getPlayerDungeonRank(UUID playerId, String dungeonId) {
        List<me.ninepin.dungeonSystem.ranking.JsonDataManager.PlayerRankingData> dungeonRanking =
                getDungeonRanking(dungeonId, Integer.MAX_VALUE);

        me.ninepin.dungeonSystem.ranking.JsonDataManager.PlayerRankingData playerData =
                getPlayerData(playerId, dungeonId);

        if (playerData != null) {
            for (int i = 0; i < dungeonRanking.size(); i++) {
                if (dungeonRanking.get(i).playerName.equals(playerData.playerName)) {
                    return i + 1;
                }
            }
        }
        return -1; // 未上榜
    }

    /**
     * 獲取玩家在特定副本的排名（重載方法，使用 Player 對象）
     */
    public int getPlayerDungeonRank(Player player, String dungeonId) {
        return getPlayerDungeonRank(player.getUniqueId(), dungeonId);
    }

    /**
     * 檢查玩家是否有任何副本記錄
     */
    public boolean hasAnyRecord(UUID playerId) {
        me.ninepin.dungeonSystem.ranking.JsonDataManager.GlobalPlayerRankingData globalData =
                getPlayerGlobalStats(playerId);
        return globalData != null && globalData.totalCompletions > 0;
    }

    /**
     * 檢查玩家是否有任何副本記錄（重載方法，使用 Player 對象）
     */
    public boolean hasAnyRecord(Player player) {
        return hasAnyRecord(player.getUniqueId());
    }

    /**
     * 獲取玩家完成過的副本列表
     */
    public java.util.Set<String> getPlayerCompletedDungeons(UUID playerId) {
        me.ninepin.dungeonSystem.ranking.JsonDataManager.GlobalPlayerRankingData globalData =
                getPlayerGlobalStats(playerId);
        return globalData != null ? globalData.dungeonsCompleted : new java.util.HashSet<>();
    }

    /**
     * 獲取玩家完成過的副本列表（重載方法，使用 Player 對象）
     */
    public java.util.Set<String> getPlayerCompletedDungeons(Player player) {
        return getPlayerCompletedDungeons(player.getUniqueId());
    }

    // ==================== 數據管理 ====================

    /**
     * 手動保存數據
     */
    public void saveData() {
        dataManager.saveData();
    }

    /**
     * 獲取底層數據管理器（如果需要直接訪問）
     */
    public me.ninepin.dungeonSystem.ranking.JsonDataManager getDataManager() {
        return dataManager;
    }
}