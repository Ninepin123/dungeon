package me.ninepin.dungeonSystem.key;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KeyNotificationCommand implements CommandExecutor {
    private final DungeonSystem plugin;
    private final KeyManager keyManager;

    public KeyNotificationCommand(DungeonSystem plugin) {
        this.plugin = plugin;
        this.keyManager = plugin.getKeyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此指令只能由玩家執行");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // 顯示當前狀態
            boolean enabled = keyManager.isPlayerNotificationEnabled(player.getUniqueId());
            player.sendMessage("§e副本入場卷冷卻通知狀態: " + (enabled ? "§a開啟" : "§c關閉"));
            player.sendMessage("§7使用 /keynotify on 開啟通知");
            player.sendMessage("§7使用 /keynotify off 關閉通知");
            player.sendMessage("§7使用 /keynotify test 測試通知");
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "on":
            case "enable":
                keyManager.setPlayerNotification(player.getUniqueId(), true);
                break;

            case "off":
            case "disable":
                keyManager.setPlayerNotification(player.getUniqueId(), false);
                break;

            case "test":
                if (player.hasPermission("dungeonsystem.admin") || player.isOp()) {
                    keyManager.testCooldownNotification(player);
                } else {
                    player.sendMessage("§c你沒有權限執行測試");
                }
                break;

            case "status":
                boolean enabled = keyManager.isPlayerNotificationEnabled(player.getUniqueId());
                player.sendMessage("§e副本入場卷冷卻通知: " + (enabled ? "§a開啟" : "§c關閉"));

                // 如果玩家有冷卻，顯示剩餘時間
                if (keyManager.isPlayerOnKeyCooldown(player.getUniqueId())) {
                    long remaining = keyManager.getPlayerKeyCooldownRemaining(player.getUniqueId());
                    player.sendMessage("§e剩餘冷卻時間: §c" + formatTime((int) remaining));
                }
                break;

            default:
                player.sendMessage("§c未知的參數: " + action);
                player.sendMessage("§7可用參數: on, off, test, status");
                break;
        }

        return true;
    }

    private String formatTime(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            if (remainingSeconds == 0) {
                return minutes + "分鐘";
            } else {
                return minutes + "分" + remainingSeconds + "秒";
            }
        } else {
            return seconds + "秒";
        }
    }
}

