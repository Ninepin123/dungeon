package me.ninepin.dungeonSystem.Dungeon;

import com.Zrips.CMI.Modules.Teleportations.CMITeleportType;
import com.Zrips.CMI.events.CMIAsyncPlayerTeleportEvent;
import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Set;
import java.util.UUID;

public class DungeonWorldListener implements Listener {

    private final DungeonSystem plugin;
    private final DungeonManager dungeonManager;

    public DungeonWorldListener(DungeonSystem plugin) {
        this.plugin = plugin;
        this.dungeonManager = plugin.getDungeonManager();
    }

    /**
     * 監聽 CMI 傳送事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCMIPlayerTeleport(CMIAsyncPlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location toLocation = event.getTo();

        // 確保目標位置不為空
        if (toLocation == null || toLocation.getWorld() == null) {
            return;
        }
        if(!event.getType().equals(CMITeleportType.Tp)){
            return;
        }
        World targetWorld = toLocation.getWorld();
        String targetWorldName = targetWorld.getName();

        plugin.getLogger().info("偵測到玩家 " + player.getName() + " 嘗試傳送到世界: " + targetWorldName);

        // 獲取所有副本世界名稱
        Set<String> dungeonWorlds = dungeonManager.getAllDungeonWorlds();

        // 如果沒有任何副本世界，直接返回
        if (dungeonWorlds.isEmpty()) {
            return;
        }

        // 如果玩家有管理員權限，允許執行
        if (player.hasPermission("dungeon.admin") || player.isOp()) {
            plugin.getLogger().info("玩家 " + player.getName() + " 有管理員權限，允許傳送");
            return;
        }

        // 檢查目標世界是否為副本世界
        if (dungeonWorlds.contains(targetWorldName)) {
            // 檢查玩家是否在任何副本中
            String playerDungeonId = dungeonManager.getPlayerDungeon(playerId);

            if (playerDungeonId == null) {
                // 玩家沒有在任何副本中，阻止傳送
                event.setCancelled(true);

                // 發送提示訊息
                player.sendMessage("§c你沒有進入任何副本，無法傳送到副本世界！");

                plugin.getLogger().info("阻止玩家 " + player.getName() + " 未經授權傳送到副本世界: " + targetWorldName);
            } else {
                // 玩家有合法的副本進入權限，記錄日誌
                plugin.getLogger().info("玩家 " + player.getName() + " 合法傳送到副本世界: " + targetWorldName + " (副本ID: " + playerDungeonId + ")");
            }
        }
    }


}