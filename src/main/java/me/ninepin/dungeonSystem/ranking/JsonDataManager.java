package me.ninepin.dungeonSystem.ranking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.ninepin.dungeonSystem.DungeonSystem;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JsonDataManager {
    private final DungeonSystem plugin;
    private final File dataFile;
    private final Gson gson;
    private Map<String, Map<String, PlayerRankingData>> dungeonRankings;

    public JsonDataManager(DungeonSystem plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "rankings.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dungeonRankings = new ConcurrentHashMap<>();

        // 確保資料夾存在
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        loadData();
    }
    public List<GlobalPlayerRankingData> getGlobalRanking(int limit) {
        Map<String, GlobalPlayerRankingData> globalStats = new HashMap<>();

        // 遍歷所有副本數據
        for (Map.Entry<String, Map<String, PlayerRankingData>> dungeonEntry : dungeonRankings.entrySet()) {
            String dungeonId = dungeonEntry.getKey();
            Map<String, PlayerRankingData> dungeonData = dungeonEntry.getValue();

            // 遍歷該副本的所有玩家數據
            for (Map.Entry<String, PlayerRankingData> playerEntry : dungeonData.entrySet()) {
                String playerId = playerEntry.getKey();
                PlayerRankingData playerData = playerEntry.getValue();

                // 獲取或創建全局玩家數據
                GlobalPlayerRankingData globalData = globalStats.computeIfAbsent(playerId,
                        k -> new GlobalPlayerRankingData(playerData.playerName));

                // 累加通關次數
                globalData.totalCompletions += playerData.completionCount;
                globalData.dungeonsCompleted.add(dungeonId);

                // 更新最後完成時間
                if (playerData.lastCompleted > globalData.lastCompleted) {
                    globalData.lastCompleted = playerData.lastCompleted;
                }
            }
        }

        // 排序並返回前N名
        return globalStats.values().stream()
                .sorted((a, b) -> Integer.compare(b.totalCompletions, a.totalCompletions))
                .limit(limit)
                .collect(ArrayList::new, (list, item) -> list.add(item), (list1, list2) -> list1.addAll(list2));
    }

    /**
     * 獲取特定玩家的全局統計數據
     */
    public GlobalPlayerRankingData getPlayerGlobalStats(UUID playerId) {
        String playerIdStr = playerId.toString();
        GlobalPlayerRankingData globalData = null;

        // 遍歷所有副本數據來計算該玩家的全局統計
        for (Map.Entry<String, Map<String, PlayerRankingData>> dungeonEntry : dungeonRankings.entrySet()) {
            String dungeonId = dungeonEntry.getKey();
            Map<String, PlayerRankingData> dungeonData = dungeonEntry.getValue();

            PlayerRankingData playerData = dungeonData.get(playerIdStr);
            if (playerData != null) {
                if (globalData == null) {
                    globalData = new GlobalPlayerRankingData(playerData.playerName);
                }

                globalData.totalCompletions += playerData.completionCount;
                globalData.dungeonsCompleted.add(dungeonId);

                if (playerData.lastCompleted > globalData.lastCompleted) {
                    globalData.lastCompleted = playerData.lastCompleted;
                }
            }
        }

        return globalData;
    }

    /**
     * 全局玩家排行數據類別
     */
    public static class GlobalPlayerRankingData {
        public String playerName;
        public int totalCompletions;           // 總通關次數
        public Set<String> dungeonsCompleted;  // 完成過的副本列表
        public long lastCompleted;             // 最後完成時間

        public GlobalPlayerRankingData(String playerName) {
            this.playerName = playerName;
            this.totalCompletions = 0;
            this.dungeonsCompleted = new HashSet<>();
            this.lastCompleted = 0;
        }

        /**
         * 獲取完成的副本數量
         */
        public int getDungeonsCompletedCount() {
            return dungeonsCompleted.size();
        }
    }
    /**
     * 標準化副本ID - 移除實例後綴（如 _1, _2）
     */
    private String normalizeDungeonId(String dungeonId) {
        if (dungeonId == null) return dungeonId;

        // 檢查是否以 _數字 結尾
        int lastUnderscoreIndex = dungeonId.lastIndexOf('_');
        if (lastUnderscoreIndex > 0) { // 確保不是以 _ 開頭
            String suffix = dungeonId.substring(lastUnderscoreIndex + 1);
            try {
                // 如果後綴是數字，則移除它
                Integer.parseInt(suffix);
                return dungeonId.substring(0, lastUnderscoreIndex);
            } catch (NumberFormatException e) {
                // 如果不是數字後綴，保持原樣
                return dungeonId;
            }
        }
        return dungeonId;
    }

    /**
     * 從 JSON 檔案載入數據
     */
    private void loadData() {
        if (!dataFile.exists()) {
            saveData();
            return;
        }

        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<RankingContainer>() {
            }.getType();
            RankingContainer container = gson.fromJson(reader, type);

            if (container != null && container.dungeons != null) {
                this.dungeonRankings = container.dungeons;
                plugin.getLogger().info("成功載入排行榜數據，共 " + dungeonRankings.size() + " 個副本");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("載入排行榜數據時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 儲存數據到 JSON 檔案
     */
    public void saveData() {
        try (Writer writer = new FileWriter(dataFile)) {
            RankingContainer container = new RankingContainer();
            container.dungeons = this.dungeonRankings;
            container.lastUpdated = System.currentTimeMillis();

            gson.toJson(container, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 記錄玩家完成副本
     */
    public void recordCompletion(UUID playerId, String dungeonId, String playerName) {
        // 標準化副本ID
        String normalizedDungeonId = normalizeDungeonId(dungeonId);

        Map<String, PlayerRankingData> dungeonData = dungeonRankings.computeIfAbsent(normalizedDungeonId, k -> new ConcurrentHashMap<>());

        PlayerRankingData playerData = dungeonData.computeIfAbsent(playerId.toString(), k -> new PlayerRankingData());
        playerData.playerName = playerName;
        playerData.completionCount++;
        playerData.lastCompleted = System.currentTimeMillis();

        plugin.getLogger().info("記錄玩家 " + playerName + " 完成副本: " + dungeonId + " -> " + normalizedDungeonId +
                " (總計: " + playerData.completionCount + " 次)");

        // 立即儲存
        saveData();
    }

    /**
     * 獲取副本排行榜 - 簡化版本，因為數據已經合併存儲
     */
    public List<PlayerRankingData> getDungeonRanking(String dungeonId, int limit) {
        // 標準化副本ID
        String normalizedDungeonId = normalizeDungeonId(dungeonId);

        Map<String, PlayerRankingData> dungeonData = dungeonRankings.get(normalizedDungeonId);

        if (dungeonData == null || dungeonData.isEmpty()) {
            return new ArrayList<>();
        }

        // 排序並返回
        List<PlayerRankingData> result = dungeonData.values().stream()
                .sorted((a, b) -> Integer.compare(b.completionCount, a.completionCount))
                .limit(limit)
                .collect(ArrayList::new, (list, item) -> list.add(item), (list1, list2) -> list1.addAll(list2));

        return result;
    }

    /**
     * 獲取玩家在特定副本的數據 - 簡化版本
     */
    public PlayerRankingData getPlayerData(UUID playerId, String dungeonId) {
        // 標準化副本ID
        String normalizedDungeonId = normalizeDungeonId(dungeonId);
        String playerIdStr = playerId.toString();

        Map<String, PlayerRankingData> dungeonData = dungeonRankings.get(normalizedDungeonId);

        if (dungeonData != null && dungeonData.containsKey(playerIdStr)) {
            return dungeonData.get(playerIdStr);
        }

        return null;
    }

    /**
     * 數據容器類別
     */
    private static class RankingContainer {
        Map<String, Map<String, PlayerRankingData>> dungeons;
        long lastUpdated;
    }

    /**
     * 玩家排行數據類別
     */
    public static class PlayerRankingData {
        public String playerName;
        public int completionCount;
        public long lastCompleted;

        public PlayerRankingData() {
            this.completionCount = 0;
            this.lastCompleted = 0;
        }
    }
}