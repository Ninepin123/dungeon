package me.ninepin.dungeonSystem.Dungeon;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;

public class WaveDungeonListener implements Listener {

    private final DungeonSystem plugin;
    private final DungeonManager dungeonManager;
    private final WaveDungeonManager waveDungeonManager;

    public WaveDungeonListener(DungeonSystem plugin, WaveDungeonManager waveDungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = plugin.getDungeonManager();
        this.waveDungeonManager = waveDungeonManager;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        // 获取死亡怪物的ID
        String mobId = event.getMobType().getInternalName();

        // 检查是否需要记录这个怪物的死亡
        for (Map.Entry<String, UUID> entry : dungeonManager.getActiveDungeons().entrySet()) {
            String dungeonId = entry.getKey();
            Dungeon dungeon = dungeonManager.getDungeon(dungeonId);

            // 如果不是波次副本，跳过（这会由普通的DungeonMobListener处理）
            if (!(dungeon instanceof WaveDungeon)) {
                continue;
            }

            // 如果是最终目标怪物，它的死亡会由DungeonMobListener处理
            // 所以这里只处理波次中的普通怪物
            if (mobId.equals(dungeon.getTargetMobId())) {
                continue;
            }

            // 这里由WaveDungeonManager自动管理波次进度
            // 它会在checkWaveProgress方法中检查所有怪物是否被击杀

            // 获取杀死怪物的玩家，如果有
            if (event.getKiller() instanceof Player) {
                Player killer = (Player) event.getKiller();
                String playerDungeonId = dungeonManager.getPlayerDungeon(killer.getUniqueId());

                // 确认玩家在这个副本中
                if (dungeonId.equals(playerDungeonId)) {
                    // 可以在这里添加玩家击杀怪物的奖励或记录
                    // 例如经验值、金币等

                    // 如果需要，可以通知玩家
                    // killer.sendMessage("§a你击杀了 " + mobId);
                }
            }
        }
    }


}