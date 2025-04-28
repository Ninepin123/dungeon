package me.ninepin.dungeonSystem.key;

import me.ninepin.dungeonSystem.Dungeon.Dungeon;
import me.ninepin.dungeonSystem.Dungeon.WaveDungeon;
import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyManager {
    private final DungeonSystem plugin;
    private final File keyFile;
    private FileConfiguration keyConfig;
    private final NamespacedKey dungeonKeyKey;
    private final NamespacedKey dungeonTypeKey;

    // 儲存副本基礎ID到實例ID的映射
    private final Map<String, String> baseToDungeonId;

    public KeyManager(DungeonSystem plugin) {
        this.plugin = plugin;
        this.keyFile = new File(plugin.getDataFolder(), "keys.yml");
        this.dungeonKeyKey = new NamespacedKey(plugin, "dungeon_key");
        this.dungeonTypeKey = new NamespacedKey(plugin, "dungeon_type");
        this.baseToDungeonId = new HashMap<>();
        loadConfig();
    }

    private void loadConfig() {
        if (!keyFile.exists()) {
            plugin.saveResource("keys.yml", false);
        }
        keyConfig = YamlConfiguration.loadConfiguration(keyFile);

        // 確保權限節點存在
        if (!keyConfig.contains("permissions")) {
            keyConfig.createSection("permissions");
            saveConfig();
        }

        initializeKeys();
    }

    public void reload() {
        // 清空現有的配置信息
        keyConfig = null;
        baseToDungeonId.clear();

        // 重新加載配置
        loadConfig();
        // 重新初始化副本鑰匙
        initializeKeys();
        plugin.getLogger().info("重新加載了副本入場卷設定");
    }

    private void initializeKeys() {
        // 先建立所有副本的基礎ID映射
        buildBaseIdMapping();

        // 遍歷所有副本的基礎ID，確保每個基礎ID都有對應的鑰匙配置
        for (String baseId : baseToDungeonId.keySet()) {
            // 获取一个实例ID来获取副本类型
            String instanceId = baseToDungeonId.get(baseId);
            Dungeon dungeon = plugin.getDungeonManager().getDungeon(instanceId);
            boolean isWaveDungeon = (dungeon instanceof WaveDungeon);

            // 設定權限ID
            String permission = "dungeonsystem.key." + baseId.toLowerCase();
            if (isWaveDungeon) {
                permission += ".wave";
            }

            // 將權限保存到配置中
            keyConfig.set("permissions." + baseId, permission);

            // 根据副本类型设置不同的默认配置
            if (!keyConfig.contains("keys." + baseId)) {
                String displayName;
                List<String> lore = new ArrayList<>();
                String material;
                int customModelData;

                if (isWaveDungeon) {
                    // 波次副本钥匙配置
                    displayName = "§b" + baseId + " 波次副本入場卷";
                    lore.add("§7使用此卷進入 " + baseId + " 波次副本");

                    // 如果是波次副本，添加波次信息
                    if (dungeon instanceof WaveDungeon) {
                        WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                        lore.add("§b總波數: §f" + waveDungeon.getTotalWaves());
                    }

                    lore.add("§7需要清理所有波次的怪物");
                    lore.add("§7右鍵點擊使用");
                    material = "BOOK";  // 使用不同的材质
                    customModelData = 2; // 不同的模型数据
                } else {
                    // 普通副本钥匙配置
                    displayName = "§e" + baseId + " 副本入場卷";
                    lore.add("§7使用此卷進入 " + baseId + " 副本");
                    lore.add("§7右鍵點擊使用");
                    material = "PAPER";
                    customModelData = 1;
                }

                keyConfig.set("keys." + baseId + ".name", displayName);
                keyConfig.set("keys." + baseId + ".lore", lore);
                keyConfig.set("keys." + baseId + ".material", material);
                keyConfig.set("keys." + baseId + ".custom_model_data", customModelData);
                keyConfig.set("keys." + baseId + ".is_wave", isWaveDungeon);
                saveConfig();

                plugin.getLogger().info("為副本 " + baseId + " 創建了鑰匙配置（類型：" + (isWaveDungeon ? "波次" : "普通") + "）");
            } else {
                // 检查现有条目是否需要更新波次状态
                keyConfig.set("keys." + baseId + ".is_wave", isWaveDungeon);

                // 如果副本类型发生变化(普通→波次或波次→普通)，可能需要更新配置
                boolean configIsWave = keyConfig.getBoolean("keys." + baseId + ".is_wave");
                if (configIsWave != isWaveDungeon) {
                    plugin.getLogger().info("副本 " + baseId + " 类型已变更，更新入场卷配置");
                    // 這裡可以添加邏輯來更新現有配置的名稱和描述，
                    // 但為避免覆蓋管理員的自定義設置，這裡不自動更新
                }
            }
        }
        saveConfig();
    }

    /**
     * 建立副本基礎ID到實例ID的映射
     */
    private void buildBaseIdMapping() {
        baseToDungeonId.clear();

        // 從DungeonManager獲取instanceToDungeon映射
        Map<String, Dungeon> allDungeons = plugin.getDungeonManager().getAllDungeons();

        for (Map.Entry<String, Dungeon> entry : allDungeons.entrySet()) {
            String instanceId = entry.getKey();

            // 從實例ID中提取基礎ID
            String baseId = instanceId;
            if (instanceId.contains("_")) {
                String[] parts = instanceId.split("_");
                if (parts.length > 1) {
                    try {
                        // 嘗試解析最後一部分是否為數字
                        Integer.parseInt(parts[parts.length - 1]);
                        StringBuilder baseIdBuilder = new StringBuilder(parts[0]);
                        for (int i = 1; i < parts.length - 1; i++) {
                            baseIdBuilder.append("_").append(parts[i]);
                        }
                        baseId = baseIdBuilder.toString();
                    } catch (NumberFormatException e) {
                        // 不是數字，則整個ID就是基礎ID
                        baseId = instanceId;
                    }
                }
            }

            // 存儲映射 (如果已有該基礎ID的映射，則不覆蓋)
            if (!baseToDungeonId.containsKey(baseId)) {
                baseToDungeonId.put(baseId, instanceId);
                plugin.getLogger().info("映射副本基礎ID " + baseId + " 到實例 " + instanceId);
            }
        }
    }

    private void saveConfig() {
        try {
            keyConfig.save(keyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("無法保存 keys.yml: " + e.getMessage());
        }
    }

    /**
     * 根據基礎ID創建副本鑰匙
     */
    public ItemStack createKey(String baseId) {
        if (!keyConfig.contains("keys." + baseId)) {
            plugin.getLogger().warning("找不到副本 " + baseId + " 的鑰匙配置");
            return null;
        }

        String materialName = keyConfig.getString("keys." + baseId + ".material", "PAPER");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.PAPER;
            plugin.getLogger().warning("無效的材質名稱: " + materialName + "，使用默認 PAPER");
        }

        ItemStack keyItem = new ItemStack(material);
        ItemMeta meta = keyItem.getItemMeta();
        if (meta != null) {
            String name = keyConfig.getString("keys." + baseId + ".name", "§e" + baseId + " 副本入場卷");
            List<String> lore = keyConfig.getStringList("keys." + baseId + ".lore");

            // 检查副本类型是否发生变化，如果是，则确保lore中包含正确的信息
            boolean isWave = keyConfig.getBoolean("keys." + baseId + ".is_wave");

            // 獲取一個實例ID來獲取副本對象
            String instanceId = baseToDungeonId.get(baseId);
            if (instanceId != null) {
                Dungeon dungeon = plugin.getDungeonManager().getDungeon(instanceId);

                if (dungeon instanceof WaveDungeon && !lore.stream().anyMatch(line -> line.contains("波次"))) {
                    // 添加波次相关描述
                    WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                    List<String> newLore = new ArrayList<>();
                    newLore.add("§7使用此卷進入 " + baseId + " 波次副本");
                    newLore.add("§b總波數: §f" + waveDungeon.getTotalWaves());
                    newLore.add("§7需要清理所有波次的怪物");
                    newLore.add("§7右鍵點擊使用");
                    lore = newLore;
                }
            }

            meta.setDisplayName(name);
            meta.setLore(lore);

            // 獲取並設置 custom_model_data
            int customModelData = keyConfig.getInt("keys." + baseId + ".custom_model_data", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            // 存儲副本基礎ID到物品中
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(dungeonKeyKey, PersistentDataType.STRING, baseId);

            // 存储副本类型
            pdc.set(dungeonTypeKey, PersistentDataType.STRING, isWave ? "wave" : "normal");

            keyItem.setItemMeta(meta);
        }

        return keyItem;
    }

    public void giveKey(Player player, String baseId, int amount) {
        // 檢查玩家是否有權限獲得這個鑰匙
        String permission = keyConfig.getString("permissions." + baseId);
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage("§c你沒有獲取此副本入場卷的權限");
            return;
        }

        ItemStack keyItem = createKey(baseId);
        if (keyItem == null) {
            player.sendMessage("§c找不到該副本的入場卷設定");
            return;
        }

        keyItem.setAmount(amount);
        player.getInventory().addItem(keyItem);
        player.sendMessage("§a你獲得了 §e" + amount + "個 " + keyItem.getItemMeta().getDisplayName());
    }

    public boolean isDungeonKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(dungeonKeyKey, PersistentDataType.STRING);
    }

    /**
     * 從鑰匙中獲取副本基礎ID
     */
    public String getDungeonIdFromKey(ItemStack item) {
        if (!isDungeonKey(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(dungeonKeyKey, PersistentDataType.STRING);
    }

    public String getDungeonTypeFromKey(ItemStack item) {
        if (!isDungeonKey(item)) {
            return "normal";
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(dungeonTypeKey, PersistentDataType.STRING)) {
            return pdc.get(dungeonTypeKey, PersistentDataType.STRING);
        }
        return "normal"; // 默认为普通副本
    }

    public boolean isWaveDungeonKey(ItemStack item) {
        return "wave".equals(getDungeonTypeFromKey(item));
    }

    /**
     * 檢查玩家是否有使用特定副本鑰匙的權限
     */
    public boolean hasKeyPermission(Player player, String baseId) {
        String permission = keyConfig.getString("permissions." + baseId);
        return permission == null || player.hasPermission(permission);
    }

    public NamespacedKey getDungeonKeyKey() {
        return dungeonKeyKey;
    }

    /**
     * 獲取所有可用的副本基礎ID
     */
    public List<String> getAvailableBaseIds() {
        return new ArrayList<>(baseToDungeonId.keySet());
    }

    /**
     * 根據基礎ID獲取一個可用的實例ID
     */
    public String getInstanceIdFromBaseId(String baseId) {
        // 首先檢查這個ID是否是直接的基礎ID映射
        if (baseToDungeonId.containsKey(baseId)) {
            return baseToDungeonId.get(baseId);
        }

        // 如果不是，則嘗試作為實例ID直接使用
        if (plugin.getDungeonManager().getDungeon(baseId) != null) {
            return baseId;
        }

        // 最後，嘗試尋找該基礎ID的可用實例
        return plugin.getDungeonManager().findAvailableInstance(baseId);
    }
}