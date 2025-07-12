package me.ninepin.dungeonSystem.party;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PartyGuiListener implements Listener {
    private final DungeonSystem plugin;
    private final PartyManager partyManager;
    private final PartyGui partyGui;

    public PartyGuiListener(DungeonSystem plugin, PartyGui partyGui) {
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
        this.partyGui = partyGui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // 检查是否是队伍GUI
        if (!title.equals("§6邀請玩家加入隊伍") && !title.equals("§c踢出隊員")) {
            return;
        }

        // 取消默认行为
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // 处理邀请GUI点击
        if (title.equals("§6邀請玩家加入隊伍")) {
            handleInviteGuiClick(player, clickedItem, event.getSlot());
        }
        // 处理踢人GUI点击
        else if (title.equals("§c踢出隊員")) {
            handleKickGuiClick(player, clickedItem, event.getSlot());
        }
    }

    /**
     * 处理邀请GUI的点击事件
     * @param player 点击的玩家
     * @param clickedItem 点击的物品
     * @param slot 点击的槽位
     */
    private void handleInviteGuiClick(Player player, ItemStack clickedItem, int slot) {
        // 关闭按钮
        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // 信息展示物品
        if (clickedItem.getType() == Material.PAPER) {
            return; // 不执行任何操作
        }

        // 玩家头颅
        if (partyGui.isPlayerSkull(clickedItem)) {
            String targetName = partyGui.getPlayerNameFromSkull(clickedItem);
            if (targetName != null) {
                // 执行邀请逻辑
                invitePlayerFromGui(player, targetName);

                // 延迟关闭GUI并重新打开以刷新列表
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.closeInventory();
                    // 可选择是否重新打开GUI来显示更新后的列表
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        partyGui.openInviteGui(player);
                    }, 5L);
                }, 1L);
            }
        }
    }

    /**
     * 处理踢人GUI的点击事件
     * @param player 点击的玩家
     * @param clickedItem 点击的物品
     * @param slot 点击的槽位
     */
    private void handleKickGuiClick(Player player, ItemStack clickedItem, int slot) {
        // 关闭按钮
        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // 信息展示物品
        if (clickedItem.getType() == Material.PAPER) {
            return; // 不执行任何操作
        }

        // 玩家头颅
        if (partyGui.isPlayerSkull(clickedItem)) {
            String targetName = partyGui.getPlayerNameFromSkull(clickedItem);
            if (targetName != null) {
                // 执行踢人逻辑
                kickPlayerFromGui(player, targetName);

                // 延迟关闭GUI并重新打开以刷新列表
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.closeInventory();
                    // 重新打开GUI来显示更新后的列表
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Party party = partyManager.getPlayerParty(player.getUniqueId());
                        if (party != null && party.getSize() > 1) {
                            partyGui.openKickGui(player);
                        }
                    }, 5L);
                }, 1L);
            }
        }
    }

    /**
     * 从GUI执行邀请玩家的逻辑 - 与原始handleInviteCommand方法保持一致
     * @param player 邀请者
     * @param targetName 目标玩家名称
     */
    private void invitePlayerFromGui(Player player, String targetName) {
        // 查找目标玩家
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§c找不到玩家 " + targetName + " 或該玩家不在線");
            return;
        }

        // 检查目标玩家是否已经在队伍中
        if (partyManager.getPlayerParty(target.getUniqueId()) != null) {
            player.sendMessage("§c該玩家已經在一個隊伍中");
            return;
        }

        // 检查玩家是否在队伍中
        Party party = partyManager.getPlayerParty(player.getUniqueId());

        // 如果玩家不在队伍中，自动创建一个
        if (party == null) {
            party = partyManager.createParty(player);
            player.sendMessage("§a你創建了一個新的隊伍！");
        } else {
            // 检查玩家是否是队长
            if (!party.isOwner(player.getUniqueId())) {
                player.sendMessage("§c只有隊長才能邀請其他玩家");
                return;
            }
        }

        // 检查是否已经有待处理的邀请 - 使用PartyCommand的pendingInvites
        // 由于无法直接访问PartyCommand的pendingInvites，我们需要通过PartyManager来检查
        if (partyManager.getPendingInvite(target.getUniqueId()) != null) {
            player.sendMessage("§c該玩家已經有一個待處理的邀請");
            return;
        }

        // 检查队伍是否已满
        if (party.isFull()) {
            player.sendMessage("§c隊伍已滿，無法邀請更多玩家");
            return;
        }

        // 发送邀请 - 使用PartyManager的sendInvite方法
        if (partyManager.sendInvite(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage("§a已發送隊伍邀請給 " + target.getName());

            // 向目标玩家发送邀请消息
            target.sendMessage("§a" + player.getName() + " 邀請你加入他的隊伍");

            // 创建可点击按钮 - 与原方法完全一致
            net.md_5.bungee.api.chat.TextComponent acceptMessage = new net.md_5.bungee.api.chat.TextComponent("§a[接受邀請]");
            acceptMessage.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/party accept"));
            acceptMessage.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("點擊接受邀請").color(net.md_5.bungee.api.ChatColor.GREEN).create()));

            net.md_5.bungee.api.chat.TextComponent spaceComponent = new net.md_5.bungee.api.chat.TextComponent(" ");

            net.md_5.bungee.api.chat.TextComponent declineMessage = new net.md_5.bungee.api.chat.TextComponent("§c[拒絕邀請]");
            declineMessage.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/party decline"));
            declineMessage.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("點擊拒絕邀請").color(net.md_5.bungee.api.ChatColor.RED).create()));

            target.spigot().sendMessage(acceptMessage, spaceComponent, declineMessage);

            // 设定邀请超时 - 与原方法完全一致
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (partyManager.getPendingInvite(target.getUniqueId()) != null &&
                        partyManager.getPendingInvite(target.getUniqueId()).equals(player.getUniqueId())) {

                    partyManager.declineInvite(target.getUniqueId());

                    if (target.isOnline()) {
                        target.sendMessage("§c來自 " + player.getName() + " 的隊伍邀請已過期");
                    }
                    if (player.isOnline()) {
                        player.sendMessage("§c發送給 " + target.getName() + " 的隊伍邀請已過期");
                    }
                }
            }, 1200L); // 60秒后过期
        } else {
            player.sendMessage("§c發送邀請失敗");
        }
    }

    /**
     * 从GUI执行踢人的逻辑 - 与原始handleKickCommand方法保持一致
     * @param player 队长
     * @param targetName 目标玩家名称
     */
    private void kickPlayerFromGui(Player player, String targetName) {
        // 检查玩家是否在队伍中
        Party party = partyManager.getPlayerParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§c你不在任何隊伍中");
            return;
        }

        // 检查玩家是否是队长
        if (!party.isOwner(player.getUniqueId())) {
            player.sendMessage("§c只有隊長才能踢出隊員");
            return;
        }

        // 寻找目标玩家
        Player target = null;
        UUID targetId = null;

        // 先在队伍成员中查找匹配的玩家名称
        for (UUID memberId : party.getMemberUUIDs()) {
            String memberName = party.getMembers().get(memberId);
            if (memberName != null && memberName.equalsIgnoreCase(targetName)) {
                targetId = memberId;
                target = Bukkit.getPlayer(memberId);
                break;
            }
        }

        // 如果在队伍成员中找不到，尝试在线玩家
        if (targetId == null) {
            target = Bukkit.getPlayer(targetName);
            if (target != null) {
                targetId = target.getUniqueId();
                // 确认该玩家确实在这个队伍中
                if (!party.getMemberUUIDs().contains(targetId)) {
                    target = null;
                    targetId = null;
                }
            }
        }

        if (targetId == null) {
            player.sendMessage("§c找不到玩家 " + targetName + " 或該玩家不在你的隊伍中");
            return;
        }

        // 不能踢自己
        if (targetId.equals(player.getUniqueId())) {
            player.sendMessage("§c你不能踢出自己，請使用 /party leave 離開隊伍");
            return;
        }

        // 使用 PartyManager 的 kickPlayer 方法
        boolean success = partyManager.kickPlayer(player.getUniqueId(), targetId);

        if (success) {
            // 通知被踢的玩家
            if (target != null && target.isOnline()) {
                target.sendMessage("§c你被踢出了隊伍");
            }

            // 通知所有队员
            String kickedPlayerName = target != null ? target.getName() : party.getMembers().get(targetId);
            for (UUID memberId : party.getMemberUUIDs()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage("§c" + kickedPlayerName + " 被踢出了隊伍");
                }
            }
        } else {
            player.sendMessage("§c踢出玩家失敗");
        }
    }
}