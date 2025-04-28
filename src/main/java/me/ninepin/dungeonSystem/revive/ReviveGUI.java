package me.ninepin.dungeonSystem.revive;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

public class ReviveGUI {
    private final DungeonSystem plugin;
    private final NamespacedKey playerUuidKey;
    private final NamespacedKey reviveTypeKey;

    public ReviveGUI(DungeonSystem plugin) {
        this.plugin = plugin;
        this.playerUuidKey = new NamespacedKey(plugin, "player_uuid");
        this.reviveTypeKey = new NamespacedKey(plugin, "revive_type");
    }

    /**
     * 創建並顯示復活GUI
     */
    public void openReviveGUI(Player user, String dungeonId, String reviveType) {
        // 獲取副本中的死亡玩家
        Set<UUID> deadPlayers = plugin.getDungeonManager().getDeadPlayers(dungeonId);
        if (deadPlayers == null || deadPlayers.isEmpty()) {
            user.sendMessage("§c目前沒有需要復活的玩家");
            return;
        }

        int size = (deadPlayers.size() / 9 + 1) * 9;
        size = Math.min(size, 54); // 最大容量為54（6行）

        Inventory gui = Bukkit.createInventory(null, size, "§6選擇要復活的玩家");

        int slot = 0;
        for (UUID deadPlayerUuid : deadPlayers) {
            Player deadPlayer = Bukkit.getPlayer(deadPlayerUuid);
            if (deadPlayer != null && deadPlayer.isOnline()) {
                ItemStack skull = createPlayerHead(deadPlayer, reviveType);
                if (slot < size) {
                    gui.setItem(slot++, skull);
                }
            }
        }

        user.openInventory(gui);
    }

    /**
     * 創建玩家頭顱物品
     */
    private ItemStack createPlayerHead(Player player, String reviveType) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e復活 " + player.getName());
            meta.setLore(Arrays.asList(
                    "§7點擊復活此玩家",
                    reviveType.equals(ReviveItemManager.ADVANCED_REVIVE) ?
                            "§a立即復活" : "§e需要等待10秒"
            ));

            meta.setOwningPlayer(player);

            // 存儲玩家UUID和復活類型
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(playerUuidKey, PersistentDataType.STRING, player.getUniqueId().toString());
            pdc.set(reviveTypeKey, PersistentDataType.STRING, reviveType);

            skull.setItemMeta(meta);
        }

        return skull;
    }

    /**
     * 獲取頭顱中存儲的玩家UUID
     */
    public UUID getPlayerUuidFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(playerUuidKey, PersistentDataType.STRING)) {
            String uuidStr = pdc.get(playerUuidKey, PersistentDataType.STRING);
            try {
                return UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * 獲取頭顱中存儲的復活類型
     */
    public String getReviveTypeFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(reviveTypeKey, PersistentDataType.STRING)) {
            return pdc.get(reviveTypeKey, PersistentDataType.STRING);
        }

        return null;
    }
}
