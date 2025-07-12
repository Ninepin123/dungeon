package me.ninepin.dungeonSystem.ranking;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import me.ninepin.dungeonSystem.Dungeon.Dungeon;
import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankingHologramManager {
    private final DungeonSystem plugin;
    // 緩存排行榜數據以減少數據加載頻率 - 使用標準化ID作為key
    private final Map<String, List<JsonDataManager.PlayerRankingData>> rankingCache = new HashMap<>();
    private final Map<String, Long> cacheTimestamps = new HashMap<>();

    private static final int CACHE_EXPIRY_SECONDS = 300; // 緩存過期時間(秒)
    private static final int MAX_DISPLAY_ENTRIES = 5;    // 顯示前幾名(縮短)
    private static final String GLOBAL_HOLOGRAM_PREFIX = "global_ranking_";
    // 全息圖名稱前綴
    private static final String TEMP_HOLOGRAM_PREFIX = "dungeon_ranking_";
    private static final String PERM_HOLOGRAM_PREFIX = "permanent_ranking_";
    private BukkitRunnable updateTask;
    private final HologramConfigManager configManager;

    public RankingHologramManager(DungeonSystem plugin) {
        this.plugin = plugin;
        this.configManager = new HologramConfigManager(plugin);

        // 延遲載入以確保世界已載入
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("RankingHologramManager 開始初始化...");
                loadSavedHolograms();
                startPeriodicUpdateTask();
            }
        }.runTaskLater(plugin, 20L); // 延遲1秒
    }

    // ==================== 初始化和任务管理 ====================

    /**
     * 启动定期更新任务
     */
    private void startPeriodicUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllVisibleHolograms();
            }
        };

        // 每5秒執行一次（5秒 = 100 ticks）
        updateTask.runTaskTimer(plugin, 100L, 100L);
        plugin.getLogger().info("已啟動全息圖定期更新任務，間隔: 5秒");
    }

    public HologramConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 創建臨時全局排行榜
     */
    public void createGlobalRanking(Location location, Player requester) {
        String hologramName = GLOBAL_HOLOGRAM_PREFIX + "temp";

        plugin.getLogger().info("創建臨時全局排行榜");

        // 獲取全局排行榜數據
        List<JsonDataManager.GlobalPlayerRankingData> topPlayers =
                plugin.getRankingManager().getGlobalRanking(10);

        // 構建hologram內容
        List<String> lines = buildGlobalHologram(topPlayers, false);
        lines.add("§7§o(30秒后消失)");

        // 移除現有的全局hologram
        removeGlobalRanking();

        // 創建新的hologram
        Hologram hologram = DHAPI.createHologram(hologramName, location, lines);
        if (hologram != null) {
            plugin.getLogger().info("成功創建臨時全局排行榜");

            // 設置自動消失
            new BukkitRunnable() {
                @Override
                public void run() {
                    removeGlobalRanking();
                }
            }.runTaskLater(plugin, 30 * 20L);
        } else {
            plugin.getLogger().severe("創建全局排行榜hologram失败: " + hologramName);
            requester.sendMessage("§c創建全局排行榜hologram失败");
        }
    }

    /**
     * 創建永久全局排行榜（自動生成位置ID）
     */
    public String createPermanentGlobalRanking(Location location) {
        // 使用特殊的副本ID來標識全局排行榜
        String globalDungeonId = "GLOBAL_RANKING";

        // 保存配置并获取生成的位置ID
        String locationId = configManager.saveHologramWithAutoId(globalDungeonId, location);

        // 創建全息圖
        String hologramName = getPermanentGlobalHologramName(locationId);
        createGlobalHologramAtLocation(location, hologramName);

        plugin.getLogger().info("成功創建永久全局排行榜，位置ID: " + locationId);
        return locationId;
    }

    /**
     * 删除特定位置的永久全局排行榜
     */
    public void removePermanentGlobalRanking(String locationId) {
        String hologramName = getPermanentGlobalHologramName(locationId);

        // 删除全息圖
        Hologram hologram = DHAPI.getHologram(hologramName);
        if (hologram != null) {
            DHAPI.removeHologram(hologramName);
            plugin.getLogger().info("已删除全局排行榜全息圖: " + hologramName);
        }

        // 從配置文件中移除
        configManager.removeHologramById("GLOBAL_RANKING", locationId);

        plugin.getLogger().info("已删除永久全局排行榜，位置ID: " + locationId);
    }

    /**
     * 更新特定位置的永久全局排行榜
     */
    public void updatePermanentGlobalRanking(String locationId) {
        String hologramName = getPermanentGlobalHologramName(locationId);

        Hologram existingHologram = DHAPI.getHologram(hologramName);
        if (existingHologram != null) {
            plugin.getLogger().info("更新永久全局排行榜，位置ID: " + locationId);

            // 更新hologram内容
            Location location = existingHologram.getLocation();
            createGlobalHologramAtLocation(location, hologramName);
        } else {
            plugin.getLogger().warning("找不到要更新的永久全局排行榜: " + hologramName);
        }
    }

    /**
     * 删除臨時全局排行榜hologram
     */
    public void removeGlobalRanking() {
        String hologramName = GLOBAL_HOLOGRAM_PREFIX + "temp";
        Hologram hologram = DHAPI.getHologram(hologramName);
        if (hologram != null) {
            DHAPI.removeHologram(hologramName);
        }
    }

    /**
     * 在指定位置創建全局排行榜全息圖的輔助方法
     */
    private void createGlobalHologramAtLocation(Location location, String hologramName) {
        // 獲取全局排行榜數據
        List<JsonDataManager.GlobalPlayerRankingData> topPlayers =
                plugin.getRankingManager().getGlobalRanking(10);

        // 構建hologram内容
        List<String> lines = buildGlobalHologram(topPlayers, true);

        // 檢查是否已存在
        Hologram existingHologram = DHAPI.getHologram(hologramName);
        if (existingHologram != null) {
            // 更新現有hologram
            DHAPI.setHologramLines(existingHologram, lines);
        } else {
            // 創建新hologram
            Hologram createdHologram = DHAPI.createHologram(hologramName, location, lines);
            if (createdHologram != null) {
                plugin.getLogger().info("成功創建永久全局排行榜全息圖: " + hologramName);
            } else {
                plugin.getLogger().severe("創建永久全局排行榜全息圖失敗: " + hologramName);
            }
        }
    }

    /**
     * 構建全局排行榜hologram内容
     */
    private List<String> buildGlobalHologram(List<JsonDataManager.GlobalPlayerRankingData> topPlayers,
                                             boolean isPermanent) {
        List<String> lines = new ArrayList<>();

        // 標題
        lines.add("§6§l✦ 全服副本排行榜 ✦");
        lines.add("§e§l總通關次數排行");

        // 如果没有記錄
        if (topPlayers.isEmpty()) {
            lines.add("§7暂无記錄");
            if (isPermanent) {
                lines.add("%dungeonrank_global_info%");
            }
            return lines;
        }

        // 顯示前10名
        for (int i = 0; i < Math.min(10, topPlayers.size()); i++) {
            JsonDataManager.GlobalPlayerRankingData data = topPlayers.get(i);

            String rankText;
            String rankColor;
            String symbol;

            switch (i) {
                case 0:
                    rankText = "第一名";
                    rankColor = "§6"; // 金色
                    symbol = "👑";
                    break;
                case 1:
                    rankText = "第二名";
                    rankColor = "§d"; // 浅紫色
                    symbol = "🥈";
                    break;
                case 2:
                    rankText = "第三名";
                    rankColor = "§9"; // 蓝色
                    symbol = "🥉";
                    break;
                default:
                    rankText = "第" + (i + 1) + "名";
                    rankColor = "§7"; // 灰色
                    symbol = "▶";
                    break;
            }

            // 顯示格式：符號 + 排名 + 玩家名稱 + 總次數 + 副本數
            lines.add(rankColor + symbol + " " + rankText + " §f" + data.playerName);
            lines.add("  §a總計: §f" + data.totalCompletions + "次 §7(" +
                    data.getDungeonsCompletedCount() + "個副本)");

            // 在前3名之間添加分隔
            if (i < 3 && i < topPlayers.size() - 1) {
                lines.add("§7§m─────────");
            }
        }

        // 添加底部信息
        lines.add("§7§m═══════════");

        if (isPermanent) {
            // 使用個人化占位符（用於永久排行榜）
            lines.add("%dungeonrank_global_info%");
        }

        return lines;
    }

    /**
     * 獲取永久全局排行榜全息圖名稱
     */
    private String getPermanentGlobalHologramName(String locationId) {
        return PERM_HOLOGRAM_PREFIX + "global_" + locationId;
    }

    /**
     * 獲取所有永久全局排行榜位置信息
     */
    public Map<String, Location> getGlobalPermanentRankings() {
        return configManager.getDungeonLocations("GLOBAL_RANKING");
    }

    /**
     * 檢查是否有永久全局排行榜在特定位置
     */
    public boolean hasGlobalPermanentRankingAtLocation(String locationId) {
        return configManager.hasHologramConfigById("GLOBAL_RANKING", locationId);
    }

    /**
     * 加载所有保存的全息图（新版多位置）
     */
    private void loadSavedHolograms() {
        Map<String, Map<String, Location>> allHolograms = configManager.loadAllHologramsWithLocations();
        plugin.getLogger().info("从配置文件载入 " + allHolograms.size() + " 个副本的永久排行榜配置");

        int totalLoaded = 0;
        for (Map.Entry<String, Map<String, Location>> dungeonEntry : allHolograms.entrySet()) {
            String normalizedDungeonId = dungeonEntry.getKey();
            Map<String, Location> locations = dungeonEntry.getValue();

            for (Map.Entry<String, Location> locationEntry : locations.entrySet()) {
                String locationId = locationEntry.getKey();
                Location location = locationEntry.getValue();

                try {
                    if ("GLOBAL_RANKING".equals(normalizedDungeonId)) {
                        // 全局排行榜使用專用方法和名稱
                        String hologramName = getPermanentGlobalHologramName(locationId);
                        createGlobalHologramAtLocation(location, hologramName);
                    } else {
                        // 副本排行榜使用原有方法
                        String hologramName = getPermanentHologramNameWithId(normalizedDungeonId, locationId);
                        createHologramAtLocation(normalizedDungeonId, location, hologramName);
                    }

                    plugin.getLogger().info("已载入永久排行榜: " + normalizedDungeonId +
                            " 位置ID: " + locationId +
                            " 位置: " + locationToString(location));
                    totalLoaded++;
                } catch (Exception e) {
                    plugin.getLogger().warning("载入永久排行榜失败 " + normalizedDungeonId +
                            "." + locationId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        plugin.getLogger().info("完成载入 " + totalLoaded + " 个永久排行榜");
    }

    /**
     * 停止所有任务
     */
    public void shutdown() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            plugin.getLogger().info("已停止全息图定期更新任务");
        }
    }

    /**
     * 修改更新间隔
     */
    public void setUpdateInterval(int seconds) {
        // 停止现有任务
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }

        // 启动新的任务
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllVisibleHolograms();
            }
        };

        long ticks = seconds * 20L;
        updateTask.runTaskTimer(plugin, ticks, ticks);
        plugin.getLogger().info("已更新全息图更新间隔为: " + seconds + "秒");
    }

    // ==================== 永久排行榜管理 ====================

    /**
     * 创建永久排行榜（自动生成位置ID）
     */
    public String createPermanentRankingWithAutoId(String dungeonId, Location location) {
        String normalizedDungeonId = normalizeDungeonId(dungeonId);

        // 保存配置并获取生成的位置ID
        String locationId = configManager.saveHologramWithAutoId(dungeonId, location);

        // 创建全息图
        String hologramName = getPermanentHologramNameWithId(normalizedDungeonId, locationId);
        createHologramAtLocation(normalizedDungeonId, location, hologramName);

        plugin.getLogger().info("成功创建永久排行榜: " + normalizedDungeonId + " 位置ID: " + locationId);
        return locationId;
    }

    /**
     * 删除特定位置的永久排行榜
     */
    public void removePermanentRankingById(String dungeonId, String locationId) {
        String normalizedId = normalizeDungeonId(dungeonId);
        String hologramName = getPermanentHologramNameWithId(normalizedId, locationId);

        // 删除全息图
        Hologram hologram = DHAPI.getHologram(hologramName);
        if (hologram != null) {
            DHAPI.removeHologram(hologramName);
            plugin.getLogger().info("已删除全息图: " + hologramName);
        }

        // 从配置文件中移除
        configManager.removeHologramById(dungeonId, locationId);

        // 清除缓存
        clearCacheForDungeon(normalizedId);

        plugin.getLogger().info("已删除永久排行榜: " + normalizedId + " 位置ID: " + locationId);
    }

    /**
     * 通过位置ID更新特定位置的永久排行榜
     */
    public void updatePermanentRankingById(String dungeonId, String locationId) {
        String normalizedDungeonId = normalizeDungeonId(dungeonId);
        String hologramName = getPermanentHologramNameWithId(normalizedDungeonId, locationId);

        Hologram existingHologram = DHAPI.getHologram(hologramName);
        if (existingHologram != null) {
            plugin.getLogger().info("更新永久排行榜: " + dungeonId + " -> " + normalizedDungeonId +
                    " 位置ID: " + locationId);

            // 强制清除缓存以获取最新数据
            clearCacheForDungeon(normalizedDungeonId);

            // 更新hologram内容
            Location location = existingHologram.getLocation();
            createHologramAtLocation(normalizedDungeonId, location, hologramName);
        } else {
            plugin.getLogger().warning("找不到要更新的永久排行榜: " + hologramName);
        }
    }

    // ==================== 临时排行榜管理 ====================

    /**
     * 创建或更新临时排行榜hologram
     */
    public void createOrUpdateRanking(String dungeonId, Location location, Player requester) {
        // 使用标准化ID进行所有操作
        String normalizedDungeonId = normalizeDungeonId(dungeonId);
        String hologramName = TEMP_HOLOGRAM_PREFIX + normalizedDungeonId.replace(" ", "_");

        plugin.getLogger().info("创建临时排行榜: " + dungeonId + " -> " + normalizedDungeonId);

        Dungeon dungeon = findDungeon(dungeonId);
        String displayName = dungeon != null ? dungeon.getDisplayName() : normalizedDungeonId;

        // 获取统一格式化的排行榜数据
        List<JsonDataManager.PlayerRankingData> topPlayers = getUnifiedRankingData(normalizedDungeonId, 10);

        // 构建hologram内容
        List<String> lines = buildCompactHologram(displayName, topPlayers, normalizedDungeonId);
        lines.add("§7§o(30秒后消失)");

        // 移除现有的hologram
        removeRanking(normalizedDungeonId);

        // 创建新的hologram
        Hologram hologram = DHAPI.createHologram(hologramName, location, lines);
        if (hologram != null) {
            plugin.getLogger().info("成功创建临时排行榜: " + normalizedDungeonId);
            // 设置自动消失
            new BukkitRunnable() {
                @Override
                public void run() {
                    removeRanking(normalizedDungeonId);
                }
            }.runTaskLater(plugin, 30 * 20L);
        } else {
            plugin.getLogger().severe("创建hologram失败: " + hologramName);
            requester.sendMessage("§c创建排行榜hologram失败");
        }
    }

    /**
     * 删除临时排行榜hologram
     */
    public void removeRanking(String dungeonId) {
        String normalizedId = normalizeDungeonId(dungeonId);
        String hologramName = TEMP_HOLOGRAM_PREFIX + normalizedId.replace(" ", "_");
        Hologram hologram = DHAPI.getHologram(hologramName);
        if (hologram != null) {
            DHAPI.removeHologram(hologramName);
        }
    }

    // ==================== 全息图更新逻辑 ====================

    /**
     * 更新所有可见的全息图
     */
    private void updateAllVisibleHolograms() {
        try {
            // 更新所有永久排行榜 - 基於配置文件中的記錄（多位置版本）
            Map<String, Map<String, Location>> allHolograms = configManager.loadAllHologramsWithLocations();

            for (Map.Entry<String, Map<String, Location>> dungeonEntry : allHolograms.entrySet()) {
                String normalizedDungeonId = dungeonEntry.getKey();
                Map<String, Location> locations = dungeonEntry.getValue();

                // 檢查是否為全局排行榜
                if ("GLOBAL_RANKING".equals(normalizedDungeonId)) {
                    // 更新全局排行榜
                    for (Map.Entry<String, Location> locationEntry : locations.entrySet()) {
                        String locationId = locationEntry.getKey();
                        String hologramName = getPermanentGlobalHologramName(locationId);
                        Hologram existingHologram = DHAPI.getHologram(hologramName);

                        if (existingHologram != null) {
                            Location location = existingHologram.getLocation();
                            createGlobalHologramAtLocation(location, hologramName);
                        } else {
                            plugin.getLogger().warning("找不到永久全局排行榜全息圖: " + hologramName);
                        }
                    }
                } else {
                    // 原有的副本排行榜更新邏輯
                    // 强制清除缓存以获取最新数据
                    clearCacheForDungeon(normalizedDungeonId);

                    for (Map.Entry<String, Location> locationEntry : locations.entrySet()) {
                        String locationId = locationEntry.getKey();
                        String hologramName = getPermanentHologramNameWithId(normalizedDungeonId, locationId);
                        Hologram existingHologram = DHAPI.getHologram(hologramName);

                        if (existingHologram != null) {
                            // 更新全息圖内容
                            Location location = existingHologram.getLocation();
                            createHologramAtLocation(normalizedDungeonId, location, hologramName);
                        } else {
                            plugin.getLogger().warning("找不到永久全息圖: " + hologramName);
                        }
                    }
                }
            }

            // 更新所有臨時排行榜
            updateTemporaryHolograms();

        } catch (Exception e) {
            plugin.getLogger().warning("定期更新全息圖時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 更新临时全息图
     */
    private void updateTemporaryHolograms() {
        try {
            // 获取所有现有的临时全息图
            Map<String, Hologram> existingTempHolograms = new HashMap<>();

            // 检查所有可能的临时全息图名称
            for (String dungeonId : plugin.getDungeonManager().getAllDungeons().keySet()) {
                String normalizedId = normalizeDungeonId(dungeonId);
                String hologramName = TEMP_HOLOGRAM_PREFIX + normalizedId.replace(" ", "_");
                Hologram tempHologram = DHAPI.getHologram(hologramName);

                if (tempHologram != null) {
                    existingTempHolograms.put(normalizedId, tempHologram);
                }
            }

            // 更新找到的临时全息图
            for (Map.Entry<String, Hologram> entry : existingTempHolograms.entrySet()) {
                String normalizedId = entry.getKey();
                Hologram hologram = entry.getValue();

                // 清除缓存以获取最新数据
                clearCacheForDungeon(normalizedId);

                // 更新临时全息图
                updateTemporaryHologramContent(normalizedId, hologram);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("更新临时全息图时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 更新临时全息图内容
     */
    private void updateTemporaryHologramContent(String normalizedDungeonId, Hologram hologram) {
        try {
            Dungeon dungeon = findDungeon(normalizedDungeonId);
            String displayName = dungeon != null ? dungeon.getDisplayName() : normalizedDungeonId;

            // 获取统一格式化后的排行榜数据
            List<JsonDataManager.PlayerRankingData> topPlayers = getUnifiedRankingData(normalizedDungeonId, 10);

            // 构建全息图内容
            List<String> lines = buildCompactHologram(displayName, topPlayers, normalizedDungeonId);
            lines.add("§7§o(30秒后消失)");

            // 更新hologram
            DHAPI.setHologramLines(hologram, lines);

        } catch (Exception e) {
            plugin.getLogger().warning("更新临时全息图 " + normalizedDungeonId + " 时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 在指定位置创建全息图的辅助方法
     */
    private void createHologramAtLocation(String normalizedDungeonId, Location location, String hologramName) {
        Dungeon dungeon = findDungeon(normalizedDungeonId);
        String displayName = dungeon != null ? dungeon.getDisplayName() : normalizedDungeonId;

        // 获取统一格式化的排行榜数据
        List<JsonDataManager.PlayerRankingData> topPlayers = getUnifiedRankingData(normalizedDungeonId, 10);

        // 构建hologram内容
        List<String> lines = buildCompactHologram(displayName, topPlayers, normalizedDungeonId);

        // 检查是否已存在
        Hologram existingHologram = DHAPI.getHologram(hologramName);
        if (existingHologram != null) {
            // 更新现有hologram
            DHAPI.setHologramLines(existingHologram, lines);
        } else {
            // 创建新hologram
            Hologram createdHologram = DHAPI.createHologram(hologramName, location, lines);
            if (createdHologram != null) {
                plugin.getLogger().info("成功创建永久排行榜全息图: " + hologramName);
            } else {
                plugin.getLogger().severe("创建永久排行榜全息图失败: " + hologramName);
            }
        }
    }

    /**
     * 手动强制更新所有全息图 - 用于调试
     */
    public void forceUpdateAll() {
        plugin.getLogger().info("手动强制更新所有全息图");

        // 清除所有缓存
        cacheTimestamps.clear();
        rankingCache.clear();

        // 强制更新
        updateAllVisibleHolograms();
    }

    // ==================== 数据管理和辅助方法 ====================

    /**
     * 标准化副本ID - 移除实例后缀（如 _1, _2）
     */
    private String normalizeDungeonId(String dungeonId) {
        if (dungeonId == null) return dungeonId;

        // 检查是否以 _数字 结尾
        int lastUnderscoreIndex = dungeonId.lastIndexOf('_');
        if (lastUnderscoreIndex > 0) { // 确保不是以 _ 开头
            String suffix = dungeonId.substring(lastUnderscoreIndex + 1);
            try {
                // 如果后缀是数字，则移除它
                Integer.parseInt(suffix);
                return dungeonId.substring(0, lastUnderscoreIndex);
            } catch (NumberFormatException e) {
                // 如果不是数字后缀，保持原样
                return dungeonId;
            }
        }
        return dungeonId;
    }

    /**
     * 获取统一格式化后的排行榜数据 - 核心方法
     */
    private List<JsonDataManager.PlayerRankingData> getUnifiedRankingData(String dungeonId, int limit) {
        // 确保使用标准化ID
        String normalizedDungeonId = normalizeDungeonId(dungeonId);

        long currentTime = System.currentTimeMillis();
        Long cacheTime = cacheTimestamps.get(normalizedDungeonId);

        // 检查缓存
        if (cacheTime != null && (currentTime - cacheTime) / 1000 < CACHE_EXPIRY_SECONDS
                && rankingCache.containsKey(normalizedDungeonId)) {
            return rankingCache.get(normalizedDungeonId);
        }

        // 从数据管理器获取统一的排行榜数据
        List<JsonDataManager.PlayerRankingData> data =
                plugin.getRankingManager().getDungeonRanking(normalizedDungeonId, limit);

        // 显示详细的数据信息（调试用）
        if (data.isEmpty()) {
            plugin.getLogger().warning("统一排行榜数据为空: " + normalizedDungeonId);
        }

        // 更新缓存
        rankingCache.put(normalizedDungeonId, data);
        cacheTimestamps.put(normalizedDungeonId, currentTime);

        return data;
    }

    /**
     * 清除指定副本的缓存
     */
    private void clearCacheForDungeon(String normalizedDungeonId) {
        cacheTimestamps.remove(normalizedDungeonId);
        rankingCache.remove(normalizedDungeonId);
    }

    /**
     * 构建简化版的预设hologram内容
     */
    private List<String> buildCompactHologram(String displayName,
                                              List<JsonDataManager.PlayerRankingData> topPlayers,
                                              String dungeonId) {
        List<String> lines = new ArrayList<>();

        // 标题
        lines.add("§6§l" + displayName + " 排行");

        // 如果没有记录
        if (topPlayers.isEmpty()) {
            lines.add("§7暂无记录");
            // 使用个人化占位符
            lines.add("%dungeonrank_" + dungeonId + "_info%");
            return lines;
        }

        // 只显示前几名
        for (int i = 0; i < Math.min(MAX_DISPLAY_ENTRIES, topPlayers.size()); i++) {
            JsonDataManager.PlayerRankingData data = topPlayers.get(i);

            String rankText;
            String rankColor;

            switch (i) {
                case 0:
                    rankText = "第一名";
                    rankColor = "§6"; // 金色
                    break;
                case 1:
                    rankText = "第二名";
                    rankColor = "§d"; // 浅紫色
                    break;
                case 2:
                    rankText = "第三名";
                    rankColor = "§9"; // 蓝色
                    break;
                default:
                    rankText = "第" + (i + 1) + "名";
                    rankColor = "§7"; // 灰色
                    break;
            }

            // 显示格式：颜色 + 排名 + 箭头 + 玩家名称 + 次数
            lines.add(rankColor + rankText + " §f༻ " + rankColor + data.playerName + " §a" + data.completionCount + "次");
        }

        // 添加分隔线
        lines.add("§7§m───────");

        // 使用个人化占位符
        lines.add("%dungeonrank_" + dungeonId + "_info%");

        return lines;
    }

    /**
     * 智能寻找副本
     */
    private Dungeon findDungeon(String dungeonId) {
        // 首先尝试直接查找原始ID
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(dungeonId);
        if (dungeon != null) {
            return dungeon;
        }

        // 如果原始ID找不到，尝试标准化ID
        String normalizedId = normalizeDungeonId(dungeonId);
        dungeon = plugin.getDungeonManager().getDungeon(normalizedId);
        if (dungeon != null) {
            return dungeon;
        }

        // 搜索相关实例
        for (Map.Entry<String, Dungeon> entry : plugin.getDungeonManager().getAllDungeons().entrySet()) {
            String instanceId = entry.getKey();

            // 检查是否匹配标准化后的ID
            if (normalizeDungeonId(instanceId).equals(normalizedId)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 位置转换为字符串
     */
    private String locationToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return String.format("%s, %.2f, %.2f, %.2f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    // ==================== 公共接口方法 ====================

    /**
     * 获取带位置ID的全息图名称
     */
    public String getPermanentHologramNameWithId(String dungeonId, String locationId) {
        String normalizedId = normalizeDungeonId(dungeonId);
        return PERM_HOLOGRAM_PREFIX + normalizedId.replace(" ", "_") + "_" + locationId;
    }

    /**
     * 获取副本的所有永久排行榜位置信息
     */
    public Map<String, Location> getDungeonPermanentRankings(String dungeonId) {
        return configManager.getDungeonLocations(dungeonId);
    }

    /**
     * 检查副本的特定位置是否有永久排行榜
     */
    public boolean hasPermanentRankingAtLocation(String dungeonId, String locationId) {
        return configManager.hasHologramConfigById(dungeonId, locationId);
    }

    /**
     * 获取所有永久排行榜副本ID
     */
    public List<String> getAllPermanentRankingDungeons() {
        List<String> dungeonIds = new ArrayList<>();
        // 直接从配置管理器获取所有标准化的副本ID
        java.util.Set<String> normalizedIds = configManager.getAllNormalizedDungeonIds();
        dungeonIds.addAll(normalizedIds);
        return dungeonIds;
    }

    /**
     * 获取全息图位置的字符串表示
     */
    public String getHologramLocation(String hologramName) {
        Hologram hologram = DHAPI.getHologram(hologramName);
        if (hologram != null) {
            return locationToString(hologram.getLocation());
        }
        return "未知位置";
    }

    /**
     * 计算玩家在排行榜中的排名
     */
    public int calculatePlayerRank(List<JsonDataManager.PlayerRankingData> topPlayers, String playerName) {
        for (int i = 0; i < topPlayers.size(); i++) {
            if (topPlayers.get(i).playerName.equals(playerName)) {
                return i + 1;
            }
        }
        return -1;
    }
}