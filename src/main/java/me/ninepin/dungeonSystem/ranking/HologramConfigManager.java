package me.ninepin.dungeonSystem.ranking;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HologramConfigManager {
    private final DungeonSystem plugin;
    private final File configFile;
    private FileConfiguration config;
    private static final String GLOBAL_RANKING_ID = "GLOBAL_RANKING";

    public HologramConfigManager(DungeonSystem plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "holograms.yml");
        loadConfig();
    }

    /**
     * 在加载配置时调用验证
     */
    private void loadConfig() {
        if (!configFile.exists()) {
            // 如果文件不存在，创建一个空的配置文件
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建全息图配置文件: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // 加载后验证配置
        validateAndCleanConfig();
    }

    /**
     * 验证和清理配置文件中的无效数据
     */
    public void validateAndCleanConfig() {
        plugin.getLogger().info("开始验证全息图配置...");

        ConfigurationSection section = config.getConfigurationSection("permanent_holograms");
        if (section == null) {
            plugin.getLogger().info("没有找到全息图配置，跳过验证");
            return;
        }

        List<String> toRemove = new ArrayList<>();
        int cleanedCount = 0;

        for (String dungeonId : section.getKeys(false)) {
            ConfigurationSection dungeonSection = config.getConfigurationSection("permanent_holograms." + dungeonId);
            if (dungeonSection == null) continue;

            List<String> invalidLocations = new ArrayList<>();

            for (String locationId : dungeonSection.getKeys(false)) {
                String worldName = dungeonSection.getString(locationId + ".world");

                // 检查数据完整性
                if (worldName == null || worldName.trim().isEmpty()) {
                    plugin.getLogger().warning("发现无效配置: " + dungeonId + "." + locationId + " - 世界名称为空");
                    invalidLocations.add(locationId);
                    cleanedCount++;
                    continue;
                }

                // 检查世界是否存在
                if (Bukkit.getWorld(worldName) == null) {
                    plugin.getLogger().warning("发现无效配置: " + dungeonId + "." + locationId + " - 世界 " + worldName + " 不存在");
                    invalidLocations.add(locationId);
                    cleanedCount++;
                    continue;
                }

                // 检查坐标是否有效
                try {
                    double x = dungeonSection.getDouble(locationId + ".x");
                    double y = dungeonSection.getDouble(locationId + ".y");
                    double z = dungeonSection.getDouble(locationId + ".z");

                    if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                        plugin.getLogger().warning("发现无效配置: " + dungeonId + "." + locationId + " - 坐标无效");
                        invalidLocations.add(locationId);
                        cleanedCount++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("发现无效配置: " + dungeonId + "." + locationId + " - 坐标格式错误");
                    invalidLocations.add(locationId);
                    cleanedCount++;
                }
            }

            // 移除无效的位置配置
            for (String locationId : invalidLocations) {
                config.set("permanent_holograms." + dungeonId + "." + locationId, null);
            }

            // 如果副本下没有有效位置了，标记整个副本节点为待移除
            ConfigurationSection updatedDungeonSection = config.getConfigurationSection("permanent_holograms." + dungeonId);
            if (updatedDungeonSection == null || updatedDungeonSection.getKeys(false).isEmpty()) {
                toRemove.add(dungeonId);
            }
        }

        // 移除空的副本节点
        for (String dungeonId : toRemove) {
            config.set("permanent_holograms." + dungeonId, null);
            plugin.getLogger().info("移除空的副本配置节点: " + dungeonId);
        }

        if (cleanedCount > 0) {
            saveConfig();
            plugin.getLogger().info("配置验证完成，清理了 " + cleanedCount + " 个无效配置项");
        } else {
            plugin.getLogger().info("配置验证完成，所有配置都有效");
        }
    }

    /**
     * 重新加载配置文件
     */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("已重新加载全息图配置文件");
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
     * 載入所有全息圖配置
     */
    public Map<String, Map<String, Location>> loadAllHologramsWithLocations() {
        Map<String, Map<String, Location>> allHolograms = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("permanent_holograms");

        if (section != null) {
            for (String dungeonId : section.getKeys(false)) {
                Map<String, Location> dungeonLocations = getDungeonLocations(dungeonId);
                if (!dungeonLocations.isEmpty()) {
                    allHolograms.put(dungeonId, dungeonLocations);
                }
            }
        }

        return allHolograms;
    }

    /**
     * 獲取所有標準化的副本ID列表
     */
    public java.util.Set<String> getAllNormalizedDungeonIds() {
        ConfigurationSection section = config.getConfigurationSection("permanent_holograms");
        if (section != null) {
            return section.getKeys(false);
        }
        return new java.util.HashSet<>();
    }

    private void saveConfig() {
        try {
            config.save(configFile);
            plugin.getLogger().info("已保存全息圖配置檔案");
        } catch (IOException e) {
            plugin.getLogger().severe("無法保存全息圖配置: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 保存全息图配置 - 支持多位置（自动生成ID）
     */
    public String saveHologramWithAutoId(String dungeonId, Location location) {
        String normalizedDungeonId = normalizeDungeonId(dungeonId);

        // 获取下一个可用的位置ID
        String locationId = getNextAvailableLocationId(normalizedDungeonId);

        String path = "permanent_holograms." + normalizedDungeonId + "." + locationId;
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());

        saveConfig();

        plugin.getLogger().info("已保存全息图配置: " + normalizedDungeonId + " 位置ID: " + locationId);
        return locationId;
    }

    /**
     * 移除特定位置的全息图配置
     */
    public void removeHologramById(String dungeonId, String locationId) {
        String normalizedDungeonId = normalizeDungeonId(dungeonId);

        config.set("permanent_holograms." + normalizedDungeonId + "." + locationId, null);

        // 如果这个副本没有其他位置了，删除整个副本节点
        ConfigurationSection dungeonSection = config.getConfigurationSection("permanent_holograms." + normalizedDungeonId);
        if (dungeonSection == null || dungeonSection.getKeys(false).isEmpty()) {
            config.set("permanent_holograms." + normalizedDungeonId, null);
        }

        saveConfig();

        plugin.getLogger().info("已移除全息图配置: " + normalizedDungeonId + " 位置ID: " + locationId);
    }

    /**
     * 获取某个副本的所有位置配置
     */
    public Map<String, Location> getDungeonLocations(String dungeonId) {
        String normalizedDungeonId = normalizeDungeonId(dungeonId);
        Map<String, Location> locations = new HashMap<>();

        ConfigurationSection dungeonSection = config.getConfigurationSection("permanent_holograms." + normalizedDungeonId);

        if (dungeonSection != null) {
            for (String locationId : dungeonSection.getKeys(false)) {
                String worldName = dungeonSection.getString(locationId + ".world");
                double x = dungeonSection.getDouble(locationId + ".x");
                double y = dungeonSection.getDouble(locationId + ".y");
                double z = dungeonSection.getDouble(locationId + ".z");

                if (Bukkit.getWorld(worldName) != null) {
                    Location location = new Location(Bukkit.getWorld(worldName), x, y, z);
                    locations.put(locationId, location);
                } else {
                    plugin.getLogger().warning("无法加载全息图配置 " + normalizedDungeonId + "." + locationId + ": 世界 " + worldName + " 不存在");
                }
            }
        }

        return locations;
    }

    /**
     * 获取下一个可用的位置ID
     */
    private String getNextAvailableLocationId(String normalizedDungeonId) {
        ConfigurationSection dungeonSection = config.getConfigurationSection("permanent_holograms." + normalizedDungeonId);

        if (dungeonSection == null) {
            return "1";
        }

        int maxId = 0;
        for (String locationId : dungeonSection.getKeys(false)) {
            try {
                int id = Integer.parseInt(locationId);
                maxId = Math.max(maxId, id);
            } catch (NumberFormatException e) {
                // 忽略非数字的ID
            }
        }

        return String.valueOf(maxId + 1);
    }

    /**
     * 检查特定位置是否存在配置
     */
    public boolean hasHologramConfigById(String dungeonId, String locationId) {
        String normalizedDungeonId = normalizeDungeonId(dungeonId);
        return config.contains("permanent_holograms." + normalizedDungeonId + "." + locationId);
    }
    public String saveGlobalRankingWithAutoId(Location location) {
        return saveHologramWithAutoId(GLOBAL_RANKING_ID, location);
    }

    public Map<String, Location> getGlobalRankingLocations() {
        return getDungeonLocations(GLOBAL_RANKING_ID);
    }

    public boolean hasGlobalRankingConfigById(String locationId) {
        return hasHologramConfigById(GLOBAL_RANKING_ID, locationId);
    }

    public void removeGlobalRankingById(String locationId) {
        removeHologramById(GLOBAL_RANKING_ID, locationId);
    }

    public void reload() {
        reloadConfig();
    }
    /**
     * 获取某个副本的位置数量
     */
    public int getDungeonLocationCount(String dungeonId) {
        String normalizedDungeonId = normalizeDungeonId(dungeonId);
        ConfigurationSection dungeonSection = config.getConfigurationSection("permanent_holograms." + normalizedDungeonId);

        if (dungeonSection != null) {
            return dungeonSection.getKeys(false).size();
        }
        return 0;
    }

    /**
     * 获取某个副本的所有位置ID
     */
    public Set<String> getDungeonLocationIds(String dungeonId) {
        String normalizedDungeonId = normalizeDungeonId(dungeonId);
        ConfigurationSection dungeonSection = config.getConfigurationSection("permanent_holograms." + normalizedDungeonId);

        if (dungeonSection != null) {
            return dungeonSection.getKeys(false);
        }
        return new HashSet<>();
    }
}