package me.ninepin.dungeonSystem.Dungeon;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class WaveDungeonManager {

    private final DungeonSystem plugin;
    private final DungeonManager dungeonManager;
    private final Map<String, BukkitTask> waveTimers; // 储存每个副本的波次计时器
    private final Map<String, Set<UUID>> waveEntities; // 储存每个副本当前波次的实体UUID
    private final Map<String, Boolean> waveCleared; // 记录当前波次是否已清理
    private final Map<String, Integer> nextWaveCountdowns; // 储存下一波的倒计时（秒）
    private final Map<String, BukkitTask> countdownTasks; // 储存倒计时任务

    public WaveDungeonManager(DungeonSystem plugin, DungeonManager dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        this.waveTimers = new HashMap<>();
        this.waveEntities = new HashMap<>();
        this.waveCleared = new HashMap<>();
        this.nextWaveCountdowns = new HashMap<>();
        this.countdownTasks = new HashMap<>();
    }

    /**
     * 开始副本的波次模式
     *
     * @param dungeonId 副本ID
     */
    public void startWaveDungeon(String dungeonId) {
        Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
        if (dungeon == null) {
            plugin.getLogger().severe("啟動波次副本失敗：找不到副本 " + dungeonId);
            return;
        }

        if (!(dungeon instanceof WaveDungeon)) {
            plugin.getLogger().severe("嘗試用波次副本啟動非波次：" + dungeonId);
            return;
        }

        WaveDungeon waveDungeon = (WaveDungeon) dungeon;

        // 检查是否已有波次进行中
        if (waveTimers.containsKey(dungeonId)) {
            plugin.getLogger().warning("副本 " + dungeonId + " 有波次計數器，取消舊波次計數器");
            cancelWaveTimer(dungeonId);
        }

        // 清理可能存在的残留数据
        clearWaveData(dungeonId);

        // 初始化波次副本
        waveDungeon.setCurrentWave(0);
        waveDungeon.setInProgress(true);
        waveCleared.put(dungeonId, true); // 设置为true以触发第一波

        plugin.getLogger().info("波次副本 " + dungeonId + " 啟動");

        // 通知所有在副本中的玩家
        broadcastToDungeon(dungeonId, "§6副本【" + dungeonId + "】開始！");

        // 开始第一波的倒计时
        startNextWaveCountdown(dungeonId, 5);
    }

    /**
     * 开始下一波的倒计时
     *
     * @param dungeonId 副本ID
     * @param seconds   倒计时秒数
     */
    private void startNextWaveCountdown(String dungeonId, int seconds) {
        // 记录日志
        plugin.getLogger().info("开始副本 " + dungeonId + " 下一次倒數: " + seconds + "秒");

        // 清理旧的倒计时资源
        cleanupCountdown(dungeonId);

        // 储存倒计时秒数
        nextWaveCountdowns.put(dungeonId, seconds);

        // 通知玩家
        broadcastToDungeon(dungeonId, "§e小心下一波怪物，" + seconds + "秒後開始...");

        // 创建新的倒计时任务
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // 检查副本是否还活跃
                if (!dungeonManager.isDungeonActive(dungeonId)) {
                    cleanupCountdown(dungeonId);
                    cancel();
                    return;
                }

                // 确保我们有倒计时数据
                Integer remainingSeconds = nextWaveCountdowns.get(dungeonId);
                if (remainingSeconds == null) {
                    cleanupCountdown(dungeonId);
                    cancel();
                    return;
                }

                remainingSeconds--;

                if (remainingSeconds <= 0) {
                    // 倒计时结束，清理资源
                    cleanupCountdown(dungeonId);
                    cancel();

                    // 使用延迟任务开始下一波（确保这个任务彻底结束）
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            startNextWave(dungeonId);
                        }
                    }.runTask(plugin);
                } else {
                    // 更新倒计时
                    nextWaveCountdowns.put(dungeonId, remainingSeconds);

                    // 仅在特定时间点通知玩家
                    if (remainingSeconds <= 5 || remainingSeconds % 5 == 0) {
                        broadcastToDungeon(dungeonId, "§e下一波怪物將在 " + remainingSeconds + " 秒後出現...");
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒执行一次

        // 储存任务
        countdownTasks.put(dungeonId, task);
    }

    /**
     * 清理副本的倒计时资源
     *
     * @param dungeonId 副本ID
     */
    private void cleanupCountdown(String dungeonId) {
        // 取消任务
        BukkitTask oldTask = countdownTasks.remove(dungeonId);
        if (oldTask != null) {
            try {
                oldTask.cancel();
                plugin.getLogger().info("已取消副本 " + dungeonId + " 的倒计时任务");
            } catch (Exception e) {
                plugin.getLogger().warning("取消副本 " + dungeonId + " 的倒计时任务时发生错误: " + e.getMessage());
            }
        }

        // 清除倒计时数据
        nextWaveCountdowns.remove(dungeonId);
    }

    /**
     * 开始下一波
     *
     * @param dungeonId 副本ID
     */
    /**
     * 开始下一波
     *
     * @param dungeonId 副本ID
     */
    private void startNextWave(String dungeonId) {
        // 确保没有正在运行的倒计时
        cleanupCountdown(dungeonId);

        Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
        if (!(dungeon instanceof WaveDungeon)) {
            return;
        }

        WaveDungeon waveDungeon = (WaveDungeon) dungeon;

        // 进入下一波
        int nextWave = waveDungeon.nextWave();

        // 如果已是最后一波且已清理
        if (nextWave == -1) {
            // 副本完成
            completeDungeon(dungeonId);
            return;
        }

        // 生成新一波的怪物
        broadcastToDungeon(dungeonId, "§e第 " + nextWave + " 波怪物來襲！");
        spawnWaveMobs(dungeonId, waveDungeon);
        waveCleared.put(dungeonId, false);

        // 启动波次检查任务（如果尚未启动）
        if (!waveTimers.containsKey(dungeonId)) {
            startWaveCheckTask(dungeonId);
        }
    }

    /**
     * 启动波次检查任务
     *
     * @param dungeonId 副本ID
     */
    private void startWaveCheckTask(String dungeonId) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // 检查副本是否还活跃
                if (!dungeonManager.isDungeonActive(dungeonId)) {
                    cancel();
                    waveTimers.remove(dungeonId);
                    return;
                }

                Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
                if (!(dungeon instanceof WaveDungeon)) {
                    cancel();
                    waveTimers.remove(dungeonId);
                    return;
                }

                WaveDungeon waveDungeon = (WaveDungeon) dungeon;

                // 检查当前波次是否已清理
                if (!waveCleared.getOrDefault(dungeonId, true)) {
                    // 检查当前波次是否已清理
                    checkWaveProgress(dungeonId, waveDungeon);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒检查一次

        waveTimers.put(dungeonId, task);
    }

    /**
     * 生成当前波次的怪物
     *
     * @param dungeonId   副本ID
     * @param waveDungeon 波次副本对象
     */
    private void spawnWaveMobs(String dungeonId, WaveDungeon waveDungeon) {
        int currentWave = waveDungeon.getCurrentWave();
        List<DungeonMob> mobs = waveDungeon.getWaveMobs(currentWave);

        if (mobs == null || mobs.isEmpty()) {
            plugin.getLogger().warning("波次 " + currentWave + " (副本 " + dungeonId + ") 沒有設定怪物，自動進入下一波");
            waveCleared.put(dungeonId, true); // 标记为已清理，以进入下一波
            startNextWaveCountdown(dungeonId, 5);
            return;
        }

        // 创建或获取当前副本的实体集合
        Set<UUID> entities = waveEntities.computeIfAbsent(dungeonId, k -> new HashSet<>());
        entities.clear(); // 清空上一波的实体记录

        int successfulSpawns = 0;
        int totalMobsToSpawn = 0;

        // 先計算總共要生成多少隻怪物
        for (DungeonMob mob : mobs) {
            totalMobsToSpawn += mob.getAmount();
        }

        // 生成怪物
        for (DungeonMob mob : mobs) {
            try {
                if (mob.getId() == null || mob.getLocation() == null) {
                    plugin.getLogger().warning("波次 " + currentWave + " 的怪物配置无效: ID或位置为空");
                    continue;
                }

                // 根據 amount 生成多隻怪物
                int amount = mob.getAmount();
                double radius = mob.getRadius();
                Location baseLocation = mob.getLocation();

                plugin.getLogger().info("在副本 " + dungeonId + " 的波次 " + currentWave + " 中準備生成 " + amount + " 隻 " + mob.getId());

                for (int i = 0; i < amount; i++) {
                    Location spawnLocation;

                    if (radius > 0) {
                        // 在指定半徑內隨機生成位置
                        spawnLocation = getRandomLocationInRadius(baseLocation, radius);
                    } else {
                        // 使用原始位置
                        spawnLocation = baseLocation.clone();
                    }

                    ActiveMob entity = MythicBukkit.inst().getMobManager().spawnMob(mob.getId(), spawnLocation);
                    if (entity != null) {
                        entities.add(entity.getUniqueId());
                        successfulSpawns++;

                        // 更详细的日志信息
                        plugin.getLogger().info("在副本 " + dungeonId + " 的波次 " + currentWave + " 中生成怪物 " +
                                mob.getId() + " 在 " + locationToString(spawnLocation));
                    } else {
                        plugin.getLogger().warning("无法生成怪物: " + mob.getId() + "，MythicMobs返回空实体");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("生成怪物 " + mob.getId() + " 时发生错误: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 检查是否成功生成怪物
        if (successfulSpawns == 0 && !mobs.isEmpty()) {
            plugin.getLogger().severe("波次 " + currentWave + " 没有成功生成任何怪物！自动进入下一波");
            waveCleared.put(dungeonId, true);
            startNextWaveCountdown(dungeonId, 5);
            return;
        }

        // 通知玩家
        broadcastToDungeon(dungeonId, "§c第 " + currentWave + " 波怪物已生成，共 " + successfulSpawns + " 隻！");
        if (totalMobsToSpawn != successfulSpawns) {
            plugin.getLogger().warning("波次 " + currentWave + " 預計生成 " + totalMobsToSpawn + " 隻怪物，實際成功生成 " + successfulSpawns + " 隻");
        }

        if (currentWave == waveDungeon.getTotalWaves()) {
            broadcastToDungeon(dungeonId, "§6这是最后一波怪物了，加油！");
        }
    }

    /**
     * 在指定半徑內生成隨機位置（平面上）
     *
     * @param center 中心位置
     * @param radius 半徑
     * @return 隨機位置
     */
    private Location getRandomLocationInRadius(Location center, double radius) {
        Random random = new Random();

        // 生成隨機角度（0 到 2π）
        double angle = random.nextDouble() * 2 * Math.PI;

        // 生成隨機距離（0 到 radius）
        double distance = random.nextDouble() * radius;

        // 計算隨機位置
        double x = center.getX() + distance * Math.cos(angle);
        double z = center.getZ() + distance * Math.sin(angle);

        // 保持原始的 Y 坐標（平面上）
        Location randomLocation = new Location(center.getWorld(), x, center.getY(), z,
                center.getYaw(), center.getPitch());

        return randomLocation;
    }

    /**
     * 将位置转换为字符串，用于日志
     */
    private String locationToString(Location loc) {
        if (loc == null) return "null";
        return loc.getWorld().getName() + "," +
                loc.getX() + "," +
                loc.getY() + "," +
                loc.getZ();
    }

    /**
     * 检查当前波次的进度
     *
     * @param dungeonId   副本ID
     * @param waveDungeon 波次副本对象
     */
    private void checkWaveProgress(String dungeonId, WaveDungeon waveDungeon) {
        Set<UUID> entities = waveEntities.getOrDefault(dungeonId, new HashSet<>());

        // 检查所有实体是否还存在
        boolean allCleared = true;
        int remainingMobs = 0;

        for (UUID entityId : entities) {
            boolean exists = false;
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(entityId)) {
                        exists = true;
                        remainingMobs++;
                        break;
                    }
                }
                if (exists) break;
            }
            if (exists) {
                allCleared = false;
            }
        }

        // 如果所有怪物都被清理
        if (allCleared && !entities.isEmpty()) {
            int currentWave = waveDungeon.getCurrentWave();
            broadcastToDungeon(dungeonId, "§a第 " + currentWave + " 波怪物已全部擊殺！");

            waveCleared.put(dungeonId, true);

            if (currentWave < waveDungeon.getTotalWaves()) {
                // 开始下一波的倒计时
                startNextWaveCountdown(dungeonId, 5);
            } else {
                // 最后一波已清理，标记完成
                completeDungeon(dungeonId);
            }
        } else if (remainingMobs > 0 && remainingMobs <= 5) {
            // 当剩余怪物较少时，通知玩家
            broadcastToDungeon(dungeonId, "§e還剩 " + remainingMobs + " 隻怪物！");
        }
    }

    /**
     * 完成副本
     *
     * @param dungeonId 副本ID
     */
    private void completeDungeon(String dungeonId) {
        plugin.getLogger().info("副本 " + dungeonId + " 完成所有波次，準備结束");

        // 取消所有相关任务
        cancelWaveTimer(dungeonId);
        BukkitTask countdownTask = countdownTasks.get(dungeonId);
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTasks.remove(dungeonId);
        }

        broadcastToDungeon(dungeonId, "§a§l恭喜你们！成功通過副本！5秒後自動返回主大廳");

        // 这里可以添加奖励发放逻辑
        // 例如：为每个玩家发放奖励道具、经验或金币
        for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
            if (dungeonId.equals(entry.getValue())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    // 发放奖励示例：这里仅作为示例，您可以替换为实际的奖励逻辑
                    // player.giveExp(1000);
                    // 向玩家发送特殊效果
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            }
        }

        // 5秒后结束副本
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupAndExitDungeon(dungeonId);
            }
        }.runTaskLater(plugin, 100L); // 5秒 = 100 ticks
    }

    /**
     * 清理副本并让所有玩家离开
     *
     * @param dungeonId 副本ID
     */
    private void cleanupAndExitDungeon(String dungeonId) {
        // 获取所有在此副本的玩家
        List<UUID> playersInDungeon = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
            if (dungeonId.equals(entry.getValue())) {
                playersInDungeon.add(entry.getKey());
            }
        }

        // 清理波次相关资源
        waveEntities.remove(dungeonId);
        waveCleared.remove(dungeonId);
        nextWaveCountdowns.remove(dungeonId);

        // 让玩家都离开副本
        for (UUID playerId : playersInDungeon) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                dungeonManager.leaveDungeon(player);
            }
        }
    }

    /**
     * 获取指定副本当前波次的实体UUID集合
     *
     * @param dungeonId 副本ID
     * @return 实体UUID集合，如果没有则返回null
     */
    public Set<UUID> getWaveEntities(String dungeonId) {
        return waveEntities.get(dungeonId);
    }

    /**
     * 清理指定副本的所有波次数据
     *
     * @param dungeonId 副本ID
     */
    public void clearWaveData(String dungeonId) {
        // 清理波次实体记录
        waveEntities.remove(dungeonId);

        // 清理波次状态记录
        waveCleared.remove(dungeonId);

        // 清理倒计时数据
        nextWaveCountdowns.remove(dungeonId);

        // 取消倒计时任务
        BukkitTask countdownTask = countdownTasks.get(dungeonId);
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTasks.remove(dungeonId);
        }

        // 取消定时器
        cancelWaveTimer(dungeonId);

        plugin.getLogger().info("已清理副本 " + dungeonId + " 的波次数据");
    }


    /**
     * 向副本中的所有玩家广播消息
     *
     * @param dungeonId 副本ID
     * @param message   消息内容
     */
    private void broadcastToDungeon(String dungeonId, String message) {
        for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
            if (dungeonId.equals(entry.getValue())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    player.sendMessage(message);
                }
            }
        }
    }

    /**
     * 取消副本的波次定时器
     *
     * @param dungeonId 副本ID
     */
    public void cancelWaveTimer(String dungeonId) {
        BukkitTask task = waveTimers.get(dungeonId);
        if (task != null) {
            try {
                task.cancel();
                plugin.getLogger().info("已取消副本 " + dungeonId + " 的波次定时器");
            } catch (Exception e) {
                plugin.getLogger().warning("取消副本 " + dungeonId + " 的波次定时器时发生错误: " + e.getMessage());
            }
            waveTimers.remove(dungeonId);
        }
    }
}