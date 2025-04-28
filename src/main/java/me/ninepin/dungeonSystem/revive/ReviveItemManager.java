package me.ninepin.dungeonSystem.revive;

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
import java.util.List;

public class ReviveItemManager {
    private final DungeonSystem plugin;
    private final File reviveItemFile;
    private FileConfiguration reviveItemConfig;
    private final NamespacedKey reviveItemKey;

    // 兩種復活道具的類型標識
    public static final String NORMAL_REVIVE = "normal";
    public static final String ADVANCED_REVIVE = "advanced";

    public ReviveItemManager(DungeonSystem plugin) {
        this.plugin = plugin;
        this.reviveItemFile = new File(plugin.getDataFolder(), "reviveitem.yml");
        this.reviveItemKey = new NamespacedKey(plugin, "revive_item");
        loadConfig();
    }

    private void loadConfig() {
        if (!reviveItemFile.exists()) {
            plugin.saveResource("reviveitem.yml", false);
        }
        reviveItemConfig = YamlConfiguration.loadConfiguration(reviveItemFile);
        initializeItems();
    }

    private void initializeItems() {
        // 初始化普通復活道具設定
        if (!reviveItemConfig.contains("items." + NORMAL_REVIVE)) {
            reviveItemConfig.set("items." + NORMAL_REVIVE + ".name", "§e復活裝置");
            reviveItemConfig.set("items." + NORMAL_REVIVE + ".lore", List.of(
                    "§7使用此裝置可以復活已死亡的隊友",
                    "§c需要等待10秒才能完成復活",
                    "§7右鍵點擊使用"));
            reviveItemConfig.set("items." + NORMAL_REVIVE + ".material", "TOTEM_OF_UNDYING");
            reviveItemConfig.set("items." + NORMAL_REVIVE + ".custom_model_data", 1);
        }

        // 初始化高級復活道具設定
        if (!reviveItemConfig.contains("items." + ADVANCED_REVIVE)) {
            reviveItemConfig.set("items." + ADVANCED_REVIVE + ".name", "§6高級復活裝置");
            reviveItemConfig.set("items." + ADVANCED_REVIVE + ".lore", List.of(
                    "§7使用此裝置可以立即復活已死亡的隊友",
                    "§a無需等待時間",
                    "§7右鍵點擊使用"));
            reviveItemConfig.set("items." + ADVANCED_REVIVE + ".material", "TOTEM_OF_UNDYING");
            reviveItemConfig.set("items." + ADVANCED_REVIVE + ".custom_model_data", 2);
        }

        saveConfig();
    }

    private void saveConfig() {
        try {
            reviveItemConfig.save(reviveItemFile);
        } catch (IOException e) {
            plugin.getLogger().severe("無法保存 reviveitem.yml: " + e.getMessage());
        }
    }

    public void reload() {
        reviveItemConfig = null;
        loadConfig();
        plugin.getLogger().info("重新加載了復活裝置設定");
    }

    public ItemStack createReviveItem(String type) {
        if (!reviveItemConfig.contains("items." + type)) {
            return null;
        }

        String materialName = reviveItemConfig.getString("items." + type + ".material", "TOTEM_OF_UNDYING");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.TOTEM_OF_UNDYING;
        }

        ItemStack reviveItem = new ItemStack(material);
        ItemMeta meta = reviveItem.getItemMeta();
        if (meta != null) {
            String name = reviveItemConfig.getString("items." + type + ".name");
            List<String> lore = reviveItemConfig.getStringList("items." + type + ".lore");

            meta.setDisplayName(name);
            meta.setLore(lore);

            int customModelData = reviveItemConfig.getInt("items." + type + ".custom_model_data", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            // 存儲復活道具類型到物品中
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(reviveItemKey, PersistentDataType.STRING, type);

            reviveItem.setItemMeta(meta);
        }

        return reviveItem;
    }

    public void giveReviveItem(Player player, String type, int amount) {
        ItemStack reviveItem = createReviveItem(type);
        if (reviveItem == null) {
            player.sendMessage("§c找不到該復活裝置的設定");
            return;
        }

        reviveItem.setAmount(amount);
        player.getInventory().addItem(reviveItem);
        player.sendMessage("§a你獲得了 §e" + amount + "個 " + reviveItem.getItemMeta().getDisplayName());
    }

    public boolean isReviveItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(reviveItemKey, PersistentDataType.STRING);
    }

    public String getReviveItemType(ItemStack item) {
        if (!isReviveItem(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(reviveItemKey, PersistentDataType.STRING);
    }

    public NamespacedKey getReviveItemKey() {
        return reviveItemKey;
    }
}
