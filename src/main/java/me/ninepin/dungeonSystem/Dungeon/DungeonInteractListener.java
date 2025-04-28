package me.ninepin.dungeonSystem.Dungeon;

import me.ninepin.dungeonSystem.DungeonSystem;
import me.ninepin.dungeonSystem.key.KeyManager;
import me.ninepin.dungeonSystem.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
//多加了一個註解
public class DungeonInteractListener implements Listener {

    private final DungeonSystem plugin;
    private final DungeonManager dungeonManager;
    private final KeyManager keyManager;

    public DungeonInteractListener(DungeonSystem plugin) {
        this.plugin = plugin;
        this.dungeonManager = plugin.getDungeonManager();
        this.keyManager = plugin.getKeyManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 檢查是否是右鍵點擊
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 檢查物品是否是副本入場卷
        if (item == null || !keyManager.isDungeonKey(item)) {
            return;
        }

        // 獲取副本基礎ID
        String baseId = keyManager.getDungeonIdFromKey(item);
        if (baseId == null) {
            return;
        }

        // 阻止事件 (防止物品被消耗或與方塊交互)
        event.setCancelled(true);

        // 檢查玩家是否有權限使用此鑰匙
        if (!keyManager.hasKeyPermission(player, baseId)) {
            player.sendMessage("§c你沒有使用此副本入場卷的權限");
            return;
        }

        // 检查玩家是否已在副本中
        if (dungeonManager.getPlayerDungeon(player.getUniqueId()) != null) {
            player.sendMessage("§c你已經在副本中，無法再次進入其他副本");
            return;
        }

        // 檢查玩家是否是隊長
        Party party = plugin.getPartyManager().getPlayerParty(player.getUniqueId());

        if (party == null) {
            player.sendMessage("§c你必須加入一個隊伍才能使用副本入場卷");
            return;
        }

        if (!party.isOwner(player.getUniqueId())) {
            player.sendMessage("§c只有隊長才能使用副本入場卷");
            return;
        }

        // 判断是否是波次副本入场券
        boolean isWaveKey = keyManager.isWaveDungeonKey(item);

        // 获取实例ID
        String instanceId = keyManager.getInstanceIdFromBaseId(baseId);

        // 如果找不到可用的副本实例
        if (instanceId == null) {
            player.sendMessage("§c找不到可用的 " + baseId + " 副本，請稍後再試");
            return;
        }

        // 获取副本对象
        Dungeon dungeon = dungeonManager.getDungeon(instanceId);
        if (dungeon == null) {
            player.sendMessage("§c找不到可用的副本");
            return;
        }

        // 檢查副本類型是否與鑰匙類型匹配
        boolean isDungeonWave = (dungeon instanceof WaveDungeon);
        if (isWaveKey != isDungeonWave) {
            if (isWaveKey) {
                player.sendMessage("§c错误: 你正在使用波次副本入场券，但指定的副本不是波次模式");
            } else {
                player.sendMessage("§c错误: 你正在使用普通副本入场券，但指定的副本是波次模式");
            }
            return;
        }

        // 尝试进入副本
        boolean success = dungeonManager.joinDungeon(player, instanceId);

        // 如果成功进入，消耗一个入场券
        if (success) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().removeItem(item);
            }
            player.updateInventory();

            // 记录日志
            plugin.getLogger().info("玩家 " + player.getName() + " 使用 " + baseId +
                    (isWaveKey ? " 波次" : "") + " 副本入场券进入了实例 " + instanceId);

            // 根据副本类型显示不同的消息
            if (dungeon instanceof WaveDungeon) {
                WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                player.sendMessage("§b你已使用波次副本入场券进入 §e" + baseId + " §b(共 " + waveDungeon.getTotalWaves() + " 波)");
            } else {
                player.sendMessage("§b你已使用副本入场券进入 §e" + baseId);
            }
        }
    }
}