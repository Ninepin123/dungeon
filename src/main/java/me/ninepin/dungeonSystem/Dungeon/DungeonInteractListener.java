package me.ninepin.dungeonSystem.Dungeon;

import me.ninepin.dungeonSystem.DungeonSystem;
import me.ninepin.dungeonSystem.key.KeyManager;
import me.ninepin.dungeonSystem.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        // ===== 新增：檢查隊伍中是否有成員在鑰匙冷卻中 =====
        List<String> membersOnCooldown = keyManager.getPartyMembersOnCooldown(party);
        if (!membersOnCooldown.isEmpty()) {
            player.sendMessage("§c以下隊員正在鑰匙使用冷卻中，無法進入副本：");
            for (String memberInfo : membersOnCooldown) {
                player.sendMessage("§c- " + memberInfo);
            }
            player.sendMessage("§c請等待所有隊員冷卻結束後再試");
            return;
        }

        // 檢查隊伍中每個成員的鑰匙權限
        List<String> membersWithoutPermission = new ArrayList<>();

        for (UUID memberUUID : party.getMemberUUIDs()) {
            Player member = Bukkit.getPlayer(memberUUID);

            // 如果成員不在線上，記錄下來
            if (member == null) {
                String memberName = party.getMembers().get(memberUUID);
                membersWithoutPermission.add(memberName + " (離線)");
                continue;
            }

            // 檢查成員是否有權限
            if (!keyManager.hasKeyPermission(member, baseId)) {
                membersWithoutPermission.add(member.getName());
            }
        }

        // 如果有成員沒有權限，阻止進入
        if (!membersWithoutPermission.isEmpty()) {
            player.sendMessage("§c以下隊員沒有使用此副本入場卷的權限：");
            for (String memberName : membersWithoutPermission) {
                player.sendMessage("§c- " + memberName);
            }
            player.sendMessage("§c請確保所有隊員都有相應權限後再試");
            return;
        }

        // 判断是否是波次副本入场券
        boolean isWaveKey = keyManager.isWaveDungeonKey(item);

        // 智能副本實例選擇
        String instanceId = findBestAvailableInstance(baseId, isWaveKey);

        // 如果找不到可用的副本实例
        if (instanceId == null) {
            String dungeonTypeName = isWaveKey ? "波次" : "普通";
            player.sendMessage("§c找不到可用的 " + baseId + " " + dungeonTypeName + " 副本場地，請稍後再試");
            return;
        }

        // 获取副本对象
        Dungeon dungeon = dungeonManager.getDungeon(instanceId);
        if (dungeon == null) {
            player.sendMessage("§c找不到可用的副本");
            return;
        }

        // 檢查副本類型是否與鑰匙類型匹配（雙重確認）
        boolean isDungeonWave = (dungeon instanceof WaveDungeon);
        if (isWaveKey != isDungeonWave) {
            if (isWaveKey) {
                player.sendMessage("§c錯誤: 你正在使用波次副本入場券，但找到的副本不是波次模式");
            } else {
                player.sendMessage("§c錯誤: 你正在使用普通副本入場券，但找到的副本是波次模式");
            }
            return;
        }

        // 尝试进入副本
        boolean success = dungeonManager.joinDungeon(player, instanceId);

        // 如果成功进入，消耗一个入场券並設置冷卻
        if (success) {
            // ===== 新增：為所有隊員設置鑰匙冷卻 =====
            for (UUID memberUUID : party.getMemberUUIDs()) {
                keyManager.setPlayerKeyCooldown(memberUUID);
            }

            // 播放鑰匙使用音效
            keyManager.playKeyUseSound(player, baseId);

            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().removeItem(item);
            }
            player.updateInventory();

            // 記錄日誌
            plugin.getLogger().info("玩家 " + player.getName() + " 使用 " + baseId +
                    (isWaveKey ? " 波次" : "") + " 副本入場券進入了實例 " + instanceId);

            // 根據副本類型顯示不同的訊息
            if (dungeon instanceof WaveDungeon) {
                WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                player.sendMessage("§b你已使用波次副本入場券進入 §e" + baseId + " §b(共 " + waveDungeon.getTotalWaves() + " 波)");
            } else {
                player.sendMessage("§b你已使用副本入場券進入 §e" + baseId);
            }
        }
    }

    /**
     * 尋找最佳可用的副本實例
     *
     * @param baseId       副本基礎ID
     * @param needWaveType 是否需要波次類型副本
     * @return 可用的實例ID，如果沒有則返回null
     */
    private String findBestAvailableInstance(String baseId, boolean needWaveType) {
        Map<String, Dungeon> allDungeons = dungeonManager.getAllDungeons();
        Map<String, UUID> activeDungeons = dungeonManager.getActiveDungeons();

        List<String> matchingInstances = new ArrayList<>();

        // 第一步：找出所有匹配基礎ID的實例
        for (Map.Entry<String, Dungeon> entry : allDungeons.entrySet()) {
            String instanceId = entry.getKey();
            Dungeon dungeon = entry.getValue();

            // 從實例ID提取基礎ID
            String extractedBaseId = extractBaseIdFromInstance(instanceId);

            // 檢查基礎ID是否匹配
            if (!baseId.equals(extractedBaseId)) {
                continue;
            }

            // 檢查副本類型是否匹配
            boolean isDungeonWave = (dungeon instanceof WaveDungeon);
            if (isDungeonWave == needWaveType) {
                matchingInstances.add(instanceId);
            }
        }

        if (matchingInstances.isEmpty()) {
            plugin.getLogger().warning("找不到基礎ID為 " + baseId + " 且類型為 " +
                    (needWaveType ? "波次" : "普通") + " 的副本實例");
            return null;
        }

        // 第二步：在匹配的實例中找空閒的
        for (String instanceId : matchingInstances) {
            if (!activeDungeons.containsKey(instanceId)) {
                plugin.getLogger().info("找到可用的空閒實例: " + instanceId +
                        " (基礎ID: " + baseId + ", 類型: " + (needWaveType ? "波次" : "普通") + ")");
                return instanceId;
            }
        }

        // 第三步：如果都被占用，記錄日誌並返回null
        plugin.getLogger().info("所有匹配的 " + baseId + " (" +
                (needWaveType ? "波次" : "普通") + ") 副本實例都被占用。" +
                "總匹配實例數: " + matchingInstances.size());

        return null;
    }

    /**
     * 從實例ID中提取基礎ID
     * 假設格式: baseId_數字 (例如: dungeon1_1, dungeon1_2)
     * 如果沒有這種格式，整個ID就是基礎ID
     */
    private String extractBaseIdFromInstance(String instanceId) {
        if (instanceId.contains("_")) {
            String[] parts = instanceId.split("_");
            if (parts.length > 1) {
                try {
                    // 嘗試解析最後一部分是否為數字
                    Integer.parseInt(parts[parts.length - 1]);

                    // 如果是數字，重建沒有最後數字部分的基礎ID
                    StringBuilder baseIdBuilder = new StringBuilder(parts[0]);
                    for (int i = 1; i < parts.length - 1; i++) {
                        baseIdBuilder.append("_").append(parts[i]);
                    }
                    return baseIdBuilder.toString();
                } catch (NumberFormatException e) {
                    // 最後一部分不是數字，整個ID就是基礎ID
                    return instanceId;
                }
            }
        }

        // 沒有下劃線或其他情況，整個ID就是基礎ID
        return instanceId;
    }
}