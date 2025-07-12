package me.ninepin.dungeonSystem.revive;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ReviveManager {
    private final DungeonSystem plugin;
    private final Set<UUID> revivingPlayers = new HashSet<>(); // 正在執行復活的玩家
    private final Map<UUID, BukkitRunnable> reviveTasks = new HashMap<>(); // 復活倒計時任務

    public ReviveManager(DungeonSystem plugin) {
        this.plugin = plugin;
    }

    /**
     * 開始復活流程
     */
    public boolean startRevive(Player reviver, Player target, String reviveType, ItemStack reviveItem) {
        if (!plugin.isRevivalSystemEnabled()) {
            reviver.sendMessage("§c復活系統目前已被禁用");
            return false;
        }
        String reviverDungeonId = plugin.getDungeonManager().getPlayerDungeon(reviver.getUniqueId());
        String targetDungeonId = plugin.getDungeonManager().getPlayerDungeon(target.getUniqueId());

        if (reviverDungeonId == null || !reviverDungeonId.equals(targetDungeonId)) {
            reviver.sendMessage("§c你和目標玩家不在同一個副本中");
            return false;
        }

        // 檢查玩家是否死亡
        Set<UUID> deadPlayers = plugin.getDungeonManager().getDeadPlayers(reviverDungeonId);
        if (deadPlayers == null || !deadPlayers.contains(target.getUniqueId())) {
            reviver.sendMessage("§c目標玩家並不需要復活");
            return false;
        }

        // 檢查是否已經在復活中
        if (revivingPlayers.contains(target.getUniqueId())) {
            reviver.sendMessage("§c該玩家正在被其他人復活中");
            return false;
        }

        // 標記玩家為正在復活中
        revivingPlayers.add(target.getUniqueId());

        // 所有復活石都需要等待10秒
        startReviveTimer(reviver, target, reviverDungeonId, reviveItem, reviveType);
        return true;
    }

    /**
     * 開始復活倒計時（普通和進階復活石都需要等待）
     */
    private void startReviveTimer(Player reviver, Player target, String dungeonId, ItemStack reviveItem, String reviveType) {
        // 儲存原始位置
        Location reviverLocation = reviver.getLocation().clone();

        // 顯示開始復活的訊息
        String reviveTypeName = reviveType.equals(ReviveItemManager.ADVANCED_REVIVE) ? "進階復活石" : "普通復活石";
        reviver.sendMessage("§a使用 §e" + reviveTypeName + " §a開始復活 §e" + target.getName() + " §a倒計時10秒");
        target.sendMessage("§a玩家 §e" + reviver.getName() + " §a正在使用 §e" + reviveTypeName + " §a復活你，倒計時10秒");

        // 創建並啟動復活倒計時任務
        BukkitRunnable reviveTask = new BukkitRunnable() {
            private int secondsLeft = 10;

            @Override
            public void run() {
                // 檢查是否有人掉線或死亡
                if (!reviver.isOnline() || !target.isOnline() ||
                        plugin.getDungeonManager().getPlayerDungeon(reviver.getUniqueId()) == null ||
                        plugin.getDungeonManager().getPlayerDungeon(target.getUniqueId()) == null) {
                    cancelRevive(reviver, target);
                    return;
                }

                if (secondsLeft > 0) {
                    if (secondsLeft <= 5 || secondsLeft == 10) {
                        reviver.sendMessage("§a復活倒計時: §e" + secondsLeft + " §a秒");
                        target.sendMessage("§a復活倒計時: §e" + secondsLeft + " §a秒");
                    }
                    secondsLeft--;
                } else {
                    completeRevive(reviver, target, dungeonId, reviveItem, reviveType);
                    cancel();
                }
            }
        };

        // 存儲任務以便可以取消
        reviveTasks.put(target.getUniqueId(), reviveTask);

        // 每秒執行一次，從現在開始
        reviveTask.runTaskTimer(plugin, 0, 20);
    }

    /**
     * 取消復活流程
     */
    public void cancelRevive(Player reviver, Player target) {
        UUID targetId = target.getUniqueId();

        // 取消任務
        if (reviveTasks.containsKey(targetId)) {
            reviveTasks.get(targetId).cancel();
            reviveTasks.remove(targetId);
        }

        // 移除復活中標記
        revivingPlayers.remove(targetId);

        // 移除施術者的效果
        reviver.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    /**
     * 完成復活流程
     */
    private void completeRevive(Player reviver, Player target, String dungeonId, ItemStack reviveItem, String reviveType) {
        UUID targetId = target.getUniqueId();

        // 移除復活中標記
        revivingPlayers.remove(targetId);
        reviveTasks.remove(targetId);

        // 先從死亡名單中移除
        Set<UUID> deadPlayers = plugin.getDungeonManager().getDeadPlayers(dungeonId);
        if (deadPlayers != null) {
            deadPlayers.remove(targetId);
        }

        // 傳送玩家到復活者的位置 (而不是副本出生點)
        target.teleport(reviver.getLocation());

        // 移除施術者的效果
        reviver.removePotionEffect(PotionEffectType.SLOWNESS);

        // 根據復活石類型調整玩家屬性
        applyReviveEffects(reviver, target, reviveType);

        // 消耗物品
        if (reviveItem.getAmount() > 1) {
            reviveItem.setAmount(reviveItem.getAmount() - 1);
        } else {
            reviver.getInventory().removeItem(reviveItem);
        }
        reviver.updateInventory();

        // 發送成功訊息
        String reviveTypeName = reviveType.equals(ReviveItemManager.ADVANCED_REVIVE) ? "進階復活石" : "普通復活石";
        reviver.sendMessage("§a成功使用 §e" + reviveTypeName + " §a復活了 §e" + target.getName());
        target.sendMessage("§a你被 §e" + reviver.getName() + " §a使用 §e" + reviveTypeName + " §a復活了");

        // 向所有隊友通知
        String reviverPartyId = getPartyId(reviver);
        if (reviverPartyId != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p != reviver && p != target && reviverPartyId.equals(getPartyId(p))) {
                    p.sendMessage("§a隊友 §e" + target.getName() + " §a被 §e" + reviver.getName() + " §a使用 §e" + reviveTypeName + " §a復活了");
                }
            }
        }
    }

    /**
     * 根據復活石類型應用復活效果
     */
    private void applyReviveEffects(Player reviver, Player target, String reviveType) {
        // 獲取目標玩家最大生命值
        double targetMaxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

        // 獲取使用者當前血量
        double reviverCurrentHealth = reviver.getHealth();

        if (reviveType.equals(ReviveItemManager.ADVANCED_REVIVE)) {
            // 進階復活石：目標復活時擁有50%血量，使用者血量變為當前的80%
            double targetHealthToSet = targetMaxHealth * 0.5;
            target.setHealth(targetHealthToSet);

            double reviverHealthToSet = reviverCurrentHealth * 0.8;
            reviver.setHealth(reviverHealthToSet);

            target.sendMessage("§e進階復活石效果：§a復活時擁有 §650%血量");
            reviver.sendMessage("§e進階復活石消耗：§6你的血量變為 §680%");
        } else {
            // 普通復活石：目標復活時只有20%血量，使用者血量變為當前的50%
            double targetHealthToSet = targetMaxHealth * 0.2;
            target.setHealth(targetHealthToSet);

            double reviverHealthToSet = reviverCurrentHealth * 0.5;
            reviver.setHealth(reviverHealthToSet);

            target.sendMessage("§e普通復活石效果：§c復活時只有 §420%血量");
            reviver.sendMessage("§e普通復活石消耗：§c你的血量變為 §450%");
        }
    }

    /**
     * 獲取玩家的隊伍ID
     */
    private String getPartyId(Player player) {
        me.ninepin.dungeonSystem.party.Party party = plugin.getPartyManager().getPlayerParty(player.getUniqueId());
        return party != null ? party.getId().toString() : null;
    }
}