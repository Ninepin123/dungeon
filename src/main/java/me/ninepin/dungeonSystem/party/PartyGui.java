package me.ninepin.dungeonSystem.party;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PartyGui {
    private final DungeonSystem plugin;
    private final PartyManager partyManager;

    public PartyGui(DungeonSystem plugin) {
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
    }

    /**
     * 打開邀請GUI
     * @param player 打開GUI的玩家
     */
    public void openInviteGui(Player player) {
        // 檢查玩家是否在隊伍中
        Party party = partyManager.getPlayerParty(player.getUniqueId());

        // 如果玩家不在隊伍中，自動創建一個
        if (party == null) {
            party = partyManager.createParty(player);
            if (party == null) {
                player.sendMessage("§c創建隊伍失敗");
                return;
            }
            player.sendMessage("§a你創建了一個新的隊伍！");
        } else {
            // 檢查玩家是否是隊長
            if (!party.isOwner(player.getUniqueId())) {
                player.sendMessage("§c只有隊長才能邀請其他玩家");
                return;
            }
        }

        // 檢查隊伍是否已滿
        if (party.isFull()) {
            player.sendMessage("§c隊伍已滿，無法邀請更多玩家");
            return;
        }

        // 創建GUI
        Inventory gui = Bukkit.createInventory(null, 54, "§6邀請玩家加入隊伍");

        // 獲取可邀請的玩家列表
        List<Player> inviteablePlayers = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // 排除自己
            if (onlinePlayer.equals(player)) continue;

            // 排除已經在隊伍中的玩家
            if (partyManager.getPlayerParty(onlinePlayer.getUniqueId()) != null) continue;

            // 排除已有待處理邀請的玩家
            if (partyManager.getPendingInvite(onlinePlayer.getUniqueId()) != null) continue;

            inviteablePlayers.add(onlinePlayer);
        }

        // 添加玩家頭顱到GUI
        int slot = 0;
        for (Player inviteablePlayer : inviteablePlayers) {
            if (slot >= 45) break; // 最多顯示45個玩家

            ItemStack skull = createPlayerSkull(inviteablePlayer);
            gui.setItem(slot, skull);
            slot++;
        }

        // 添加關閉按鈕
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c關閉");
            List<String> closeLore = new ArrayList<>();
            closeLore.add("§7點擊關閉界面");
            closeMeta.setLore(closeLore);
            closeButton.setItemMeta(closeMeta);
        }
        gui.setItem(53, closeButton);

        // 添加資訊展示
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§a隊伍資訊");
            List<String> infoLore = new ArrayList<>();
            infoLore.add("§7當前隊伍人數: §e" + party.getSize() + "/" + party.getMaxSize());
            infoLore.add("§7可邀請玩家數: §e" + inviteablePlayers.size());
            infoLore.add("§7點擊玩家頭顱發送邀請");
            infoMeta.setLore(infoLore);
            infoItem.setItemMeta(infoMeta);
        }
        gui.setItem(49, infoItem);

        player.openInventory(gui);
    }

    /**
     * 打開踢人GUI
     * @param player 打開GUI的玩家
     */
    public void openKickGui(Player player) {
        // 檢查玩家是否在隊伍中
        Party party = partyManager.getPlayerParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§c你不在任何隊伍中");
            return;
        }

        // 檢查玩家是否是隊長
        if (!party.isOwner(player.getUniqueId())) {
            player.sendMessage("§c只有隊長才能踢出隊員");
            return;
        }

        // 創建GUI
        Inventory gui = Bukkit.createInventory(null, 54, "§c踢出隊員");

        // 獲取隊伍成員列表（排除隊長自己）
        List<UUID> kickableMembers = new ArrayList<>();
        for (UUID memberId : party.getMemberUUIDs()) {
            if (!memberId.equals(player.getUniqueId())) {
                kickableMembers.add(memberId);
            }
        }

        // 添加隊員頭顱到GUI
        int slot = 0;
        for (UUID memberId : kickableMembers) {
            if (slot >= 45) break; // 最多顯示45個隊員

            Player member = Bukkit.getPlayer(memberId);
            String memberName = party.getMembers().get(memberId);

            if (memberName == null && member != null) {
                memberName = member.getName();
            }

            if (memberName != null) {
                ItemStack skull = createMemberSkull(memberId, memberName, member != null && member.isOnline());
                gui.setItem(slot, skull);
                slot++;
            }
        }

        // 添加關閉按鈕
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c關閉");
            List<String> closeLore = new ArrayList<>();
            closeLore.add("§7點擊關閉界面");
            closeMeta.setLore(closeLore);
            closeButton.setItemMeta(closeMeta);
        }
        gui.setItem(53, closeButton);

        // 添加隊伍資訊
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§c隊伍管理");
            List<String> infoLore = new ArrayList<>();
            infoLore.add("§7隊伍人數: §e" + party.getSize() + "/" + party.getMaxSize());
            infoLore.add("§7可踢出成員數: §e" + kickableMembers.size());
            infoLore.add("§7點擊玩家頭顱踢出隊員");
            infoLore.add("§c§l警告: 此操作不可撤銷!");
            infoMeta.setLore(infoLore);
            infoItem.setItemMeta(infoMeta);
        }
        gui.setItem(49, infoItem);

        player.openInventory(gui);
    }

    /**
     * 創建玩家頭顱物品（用於邀請GUI）
     * @param player 目標玩家
     * @return 頭顱物品
     */
    private ItemStack createPlayerSkull(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();

        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName("§a" + player.getName());

            List<String> lore = new ArrayList<>();
            lore.add("§7玩家: §e" + player.getName());
            lore.add("§7狀態: §a在線");
            lore.add("");
            lore.add("§e點擊邀請此玩家加入隊伍");
            skullMeta.setLore(lore);

            skull.setItemMeta(skullMeta);
        }

        return skull;
    }

    /**
     * 創建隊員頭顱物品（用於踢人GUI）
     * @param memberId 隊員UUID
     * @param memberName 隊員名稱
     * @param isOnline 是否在線
     * @return 頭顱物品
     */
    private ItemStack createMemberSkull(UUID memberId, String memberName, boolean isOnline) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();

        if (skullMeta != null) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                skullMeta.setOwningPlayer(member);
            }

            skullMeta.setDisplayName((isOnline ? "§a" : "§7") + memberName);

            List<String> lore = new ArrayList<>();
            lore.add("§7玩家: §e" + memberName);
            lore.add("§7狀態: " + (isOnline ? "§a在線" : "§7離線"));
            lore.add("");
            lore.add("§c點擊踢出此玩家");
            lore.add("§c§l警告: 此操作不可撤銷!");
            skullMeta.setLore(lore);

            skull.setItemMeta(skullMeta);
        }

        return skull;
    }

    /**
     * 檢查物品是否是玩家頭顱
     * @param item 物品
     * @return 是否是玩家頭顱
     */
    public boolean isPlayerSkull(ItemStack item) {
        return item != null && item.getType() == Material.PLAYER_HEAD && item.hasItemMeta();
    }

    /**
     * 從頭顱物品獲取玩家名稱
     * @param item 頭顱物品
     * @return 玩家名稱
     */
    public String getPlayerNameFromSkull(ItemStack item) {
        if (!isPlayerSkull(item)) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            // 移除顏色代碼
            return displayName.replaceAll("§[0-9a-fA-F]", "");
        }

        return null;
    }
}