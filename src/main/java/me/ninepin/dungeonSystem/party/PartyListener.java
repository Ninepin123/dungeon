package me.ninepin.dungeonSystem.party;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * 隊伍系統事件監聽器，負責處理隊伍相關的事件
 */
public class PartyListener implements Listener {

    private final DungeonSystem plugin;
    private final PartyManager partyManager;

    public PartyListener(DungeonSystem plugin) {
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
    }

    /**
     * 處理玩家離線事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 移除玩家的任何待處理邀請
        partyManager.declineInvite(playerId);

        // 如果玩家在隊伍中，通知其他隊員並處理離開邏輯
        Party party = partyManager.getPlayerParty(playerId);
        if (party != null) {
            // 玩家離線時也算作離開隊伍
            partyManager.removePlayerFromParty(playerId);

            // 通知其他隊員
            for (UUID memberId : party.getMemberUUIDs()) {
                if (!memberId.equals(playerId)) {
                    Player member = plugin.getServer().getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        member.sendMessage("§c" + player.getName() + " 離線，已離開隊伍");
                    }
                }
            }
        }
    }

    /**
     * 處理實體傷害事件，防止隊伍成員之間互相傷害
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 檢查受害者是否是玩家
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Entity damager = event.getDamager();
        Player attacker = null;

        // 獲取攻擊者（可能是玩家或其他實體類型）
        if (damager instanceof Player) {
            attacker = (Player) damager;
        } else {
            // 在這裡可以處理來自箭矢、藥水等間接傷害的情況
            // 例如：if (damager instanceof Arrow) { attacker = (Player) ((Arrow) damager).getShooter(); }
            // 但為了簡化，我們只處理直接玩家攻擊的情況
            return;
        }

        // 如果攻擊者不是玩家，忽略
        if (attacker == null) {
            return;
        }

        // 檢查雙方是否在同一個隊伍中
        Party victimParty = partyManager.getPlayerParty(victim.getUniqueId());
        Party attackerParty = partyManager.getPlayerParty(attacker.getUniqueId());

        // 如果兩個玩家在同一個隊伍中，取消傷害
        if (victimParty != null && attackerParty != null && victimParty == attackerParty) {
            event.setCancelled(true);
            attacker.sendMessage("§c你不能攻擊自己的隊友！");
        }
    }
}
