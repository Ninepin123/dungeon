package me.ninepin.dungeonSystem.Dungeon;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import me.ninepin.dungeonSystem.DungeonSystem;
import me.ninepin.dungeonSystem.damage.PlayerRanking;
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
    private final Map<String, BukkitTask> forceNextWaveTasks; // 储存强制开启下一波的任务
    private final Map<String, Integer> forceNextWaveCountdowns; // 储存强制开启下一波的剩余时间
    private final Map<String, Long> lastMobCountDisplay;

    public WaveDungeonManager(DungeonSystem plugin, DungeonManager dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        this.waveTimers = new HashMap<>();
        this.waveEntities = new HashMap<>();
        this.waveCleared = new HashMap<>();
        this.nextWaveCountdowns = new HashMap<>();
        this.countdownTasks = new HashMap<>();
        this.forceNextWaveTasks = new HashMap<>(); // 新增
        this.forceNextWaveCountdowns = new HashMap<>(); // 新增
        this.lastMobCountDisplay = new HashMap<>();
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

        // 清理旧倒计时资源
        cleanupCountdown(dungeonId);

        // 储存倒计时秒数
        nextWaveCountdowns.put(dungeonId, seconds);

        // 通知玩家
        broadcastToDungeon(dungeonId, "§e下一波怪物將在 " + seconds + " 秒後開始...");

        // 创建新倒计时任务
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
                        if (remainingSeconds <= 5) {
                            playCountdownSound(dungeonId);
                        }
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

        // 計算當前副本中的玩家數量
        int playerCount = 0;
        for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
            if (dungeonId.equals(entry.getValue())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    playerCount++;
                }
            }
        }

        // 獲取或創建當前副本的實體集合（不清空，保留上一波存活的怪物）
        Set<UUID> entities = waveEntities.computeIfAbsent(dungeonId, k -> new HashSet<>());
        // 注意：這裡不調用 entities.clear()，保留上一波的怪物記錄

        int successfulSpawns = 0;
        int totalMobsToSpawn = 0;

        plugin.getLogger().info("正在為副本 " + dungeonId + " 第 " + currentWave + " 波生成怪物，玩家數量: " + playerCount);

        // 先計算總共要生成多少隻怪物（應用縮放後）
        for (DungeonMob mob : mobs) {
            if (mob.isNormal()) {
                // 普通怪物：根據人數調整數量
                double multiplier = dungeonManager.getNormalMobMultiplier(playerCount);
                int scaledAmount = (int) Math.ceil(mob.getAmount() * multiplier);
                totalMobsToSpawn += scaledAmount;
            } else {
                // BOSS怪物：數量不變
                totalMobsToSpawn += mob.getAmount();
            }
        }

        // 生成怪物（應用縮放邏輯）
        for (DungeonMob mob : mobs) {
            try {
                if (mob.getId() == null || mob.getLocation() == null) {
                    plugin.getLogger().warning("波次 " + currentWave + " 的怪物配置无效: ID或位置为空");
                    continue;
                }

                int finalAmount;
                int finalLevel;

                if (waveDungeon.isModify()) {  // 檢查是否啟用調整
                    if (mob.isNormal()) {
                        // 普通怪物：根據人數調整數量
                        double multiplier = dungeonManager.getNormalMobMultiplier(playerCount);
                        finalAmount = (int) Math.ceil(mob.getAmount() * multiplier);
                        finalLevel = mob.getLevel();
                    } else if (mob.isBoss()) {
                        // BOSS怪物：數量不變，根據人數調整等級
                        finalAmount = mob.getAmount();
                        int levelBonus = dungeonManager.getBossLevelBonus(playerCount);
                        finalLevel = mob.getLevel() + levelBonus;
                    } else {
                        finalAmount = mob.getAmount();
                        finalLevel = mob.getLevel();
                    }
                } else {
                    // 不調整，保持原始數量和等級
                    finalAmount = mob.getAmount();
                    finalLevel = mob.getLevel();
                }

                double radius = mob.getRadius();
                Location baseLocation = mob.getLocation();

                plugin.getLogger().info("在副本 " + dungeonId + " 的波次 " + currentWave + " 中準備生成 " + finalAmount + " 隻 " + mob.getType() + " 類型的 " + mob.getId());

                for (int i = 0; i < finalAmount; i++) {
                    Location spawnLocation;

                    if (radius > 0) {
                        // 在指定半徑內隨機生成位置
                        spawnLocation = getRandomLocationInRadius(baseLocation, radius);
                    } else {
                        // 使用原始位置
                        spawnLocation = baseLocation.clone();
                    }

                    ActiveMob entity = null;
                    if (finalLevel > 1) {
                        // 使用調整後的等級生成怪物
                        entity = MythicBukkit.inst().getMobManager().spawnMob(mob.getId(), spawnLocation, finalLevel);
                    } else {
                        // 使用預設等級生成怪物
                        entity = MythicBukkit.inst().getMobManager().spawnMob(mob.getId(), spawnLocation);
                    }

                    if (entity != null) {
                        entities.add(entity.getUniqueId());
                        successfulSpawns++;

                        // 更详细的日志信息
                        plugin.getLogger().info("在副本 " + dungeonId + " 的波次 " + currentWave + " 中生成 " + mob.getType() + " 怪物 " +
                                mob.getId() + " (等級 " + finalLevel + ") 在 " + locationToString(spawnLocation));
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

        // 計算總怪物數量（包括上一波存活的）
        int totalMobs = entities.size(); // 這包括了上一波存活的怪物
        int previousWaveMobs = totalMobs - successfulSpawns;

        // 通知玩家（包含縮放信息）
        if (previousWaveMobs > 0) {
            if (playerCount > 1) {
                broadcastToDungeon(dungeonId, "§c第 " + currentWave + " 波怪物已生成！（已根據 " + playerCount + " 人調整難度）");
                broadcastToDungeon(dungeonId, "§e新增 " + successfulSpawns + " 隻怪物 + " + previousWaveMobs + " 隻上波存活 = 總計 " + totalMobs + " 隻");
            } else {
                broadcastToDungeon(dungeonId, "§c第 " + currentWave + " 波怪物已生成，共 " + successfulSpawns + " 隻新怪物（加上 " + previousWaveMobs + " 隻上一波存活怪物，總計 " + totalMobs + " 隻）！");
            }
        } else {
            if (playerCount > 1) {
                broadcastToDungeon(dungeonId, "§c第 " + currentWave + " 波怪物已生成！（已根據 " + playerCount + " 人調整難度）");
                broadcastToDungeon(dungeonId, "§e共生成 " + successfulSpawns + " 隻怪物！");
            } else {
                broadcastToDungeon(dungeonId, "§c第 " + currentWave + " 波怪物已生成，共 " + successfulSpawns + " 隻！");
            }
        }

        playWaveStartSound(dungeonId);
        if (totalMobsToSpawn != successfulSpawns) {
            plugin.getLogger().warning("波次 " + currentWave + " 預計生成 " + totalMobsToSpawn + " 隻怪物，實際成功生成 " + successfulSpawns + " 隻");
        }

        if (currentWave == waveDungeon.getTotalWaves()) {
            broadcastToDungeon(dungeonId, "§6这是最后一波怪物了，加油！");
            // 最後一波不啟動強制下一波倒數
            plugin.getLogger().info("副本 " + dungeonId + " 已到達最後一波（第 " + currentWave + " 波），不啟動強制下一波倒數");
        } else {
            // 不是最後一波才開始強制下一波倒數
            int forceNextWaveTime = plugin.getConfig().getInt("wave-dungeon.force-next-wave-countdown", 300); // 預設 5 分鐘
            startForceNextWaveCountdown(dungeonId, forceNextWaveTime);
        }
    }

    // 4. 新增方法：開始強制下一波的倒數
    private void startForceNextWaveCountdown(String dungeonId, int seconds) {
        // 记录日志
        plugin.getLogger().info("開始副本 " + dungeonId + " 強制下一波倒數: " + seconds + "秒");

        // 清理旧的强制倒计时任务
        cleanupForceNextWaveCountdown(dungeonId);

        // 储存强制倒计时秒数
        forceNextWaveCountdowns.put(dungeonId, seconds);

        // 创建强制倒计时任务
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // 检查副本是否还活跃
                if (!dungeonManager.isDungeonActive(dungeonId)) {
                    cleanupForceNextWaveCountdown(dungeonId);
                    cancel();
                    return;
                }

                // 检查当前波次是否已经被清理（正常完成）
                if (waveCleared.getOrDefault(dungeonId, false)) {
                    cleanupForceNextWaveCountdown(dungeonId);
                    cancel();
                    return;
                }

                // 确保我们有强制倒计时数据
                Integer remainingSeconds = forceNextWaveCountdowns.get(dungeonId);
                if (remainingSeconds == null) {
                    cleanupForceNextWaveCountdown(dungeonId);
                    cancel();
                    return;
                }

                remainingSeconds--;

                if (remainingSeconds <= 0) {
                    // 强制倒计时结束，强制开始下一波
                    cleanupForceNextWaveCountdown(dungeonId);
                    cancel();

                    // 获取当前活着的怪物数量
                    Set<UUID> currentEntities = waveEntities.getOrDefault(dungeonId, new HashSet<>());
                    int survivingMobs = 0;
                    Set<UUID> survivingEntities = new HashSet<>();

                    // 检查哪些怪物还活着
                    for (UUID entityId : currentEntities) {
                        for (org.bukkit.World world : Bukkit.getWorlds()) {
                            for (Entity entity : world.getEntities()) {
                                if (entity.getUniqueId().equals(entityId)) {
                                    survivingEntities.add(entityId);
                                    survivingMobs++;
                                    break;
                                }
                            }
                        }
                    }

                    // 計算玩家數量，用於縮放提示
                    int playerCount = 0;
                    for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
                        if (dungeonId.equals(entry.getValue())) {
                            Player player = Bukkit.getPlayer(entry.getKey());
                            if (player != null && player.isOnline()) {
                                playerCount++;
                            }
                        }
                    }

                    // 强制进入下一波（包含縮放信息）
                    if (survivingMobs > 0) {
                        if (playerCount > 1) {
                            broadcastToDungeon(dungeonId, "§c§l時間到！强制開始下一波！（根據 " + playerCount + " 人調整難度）");
                            broadcastToDungeon(dungeonId, "§e" + survivingMobs + " 隻上波存活怪物將併入下一波");
                        } else {
                            broadcastToDungeon(dungeonId, "§c§l時間到！强制開始下一波！（" + survivingMobs + " 隻上一波怪物將併入下一波）");
                        }
                    } else {
                        if (playerCount > 1) {
                            broadcastToDungeon(dungeonId, "§c§l時間到！强制開始下一波！（根據 " + playerCount + " 人調整難度）");
                        } else {
                            broadcastToDungeon(dungeonId, "§c§l時間到！强制開始下一波！");
                        }
                    }

                    playForceNextWaveSound(dungeonId);

                    // 保留上一波存活的怪物，並將它們合併到下一波
                    // 這裡不清空 waveEntities，讓上一波存活的怪物成為下一波的一部分

                    // 標記當前波次為已清理，讓系統進入下一波
                    waveCleared.put(dungeonId, true);

                    // 使用延迟任务开始下一波
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // 在 startNextWave 中，上一波的存活怪物會自動合併到下一波
                            startNextWaveCountdown(dungeonId, 5);
                        }
                    }.runTask(plugin);
                } else {
                    // 更新强制倒计时
                    forceNextWaveCountdowns.put(dungeonId, remainingSeconds);

                    // 在关键时间点通知玩家
                    if (remainingSeconds <= 30 && remainingSeconds % 10 == 0) {
                        broadcastToDungeon(dungeonId, "§c§l警告！強制進入下一波倒數: " + remainingSeconds + " 秒");
                    } else if (remainingSeconds <= 60 && remainingSeconds % 30 == 0) {
                        broadcastToDungeon(dungeonId, "§e強制進入下一波倒數: " + remainingSeconds + " 秒");
                    } else if (remainingSeconds <= 300 && remainingSeconds % 60 == 0) {
                        // 新增：在前5分鐘，每分鐘提醒一次
                        int minutes = remainingSeconds / 60;
                        broadcastToDungeon(dungeonId, "§7提醒：還有 " + minutes + " 分鐘將強制進入下一波");
                    }

                    if (remainingSeconds <= 5) {
                        playCountdownSound(dungeonId);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒执行一次

        // 储存任务
        forceNextWaveTasks.put(dungeonId, task);
    }

    /**
     * 播放強制下一波音效给副本中的所有玩家
     */
    private void playForceNextWaveSound(String dungeonId) {
        DungeonSystem.SoundConfig config = plugin.getSoundConfig();
        if (config == null) {
            plugin.getLogger().warning("音效配置未載入，跳過強制下一波音效播放");
            return;
        }

        try {
            // 使用倒數音效，但音調調低一點表示警告
            Sound sound = Sound.valueOf(config.getCountdownSound());
            for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
                if (dungeonId.equals(entry.getValue())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.playSound(player.getLocation(), sound,
                                (float) config.getVolume(),
                                (float) (config.getCountdownPitch() * 0.8)); // 音調調低表示警告
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("無效的音效: " + config.getCountdownSound());
        }
    }

    private void cleanupForceNextWaveCountdown(String dungeonId) {
        // 取消任务
        BukkitTask oldTask = forceNextWaveTasks.remove(dungeonId);
        if (oldTask != null) {
            try {
                oldTask.cancel();
                plugin.getLogger().info("已取消副本 " + dungeonId + " 的強制下一波倒计时任务");
            } catch (Exception e) {
                plugin.getLogger().warning("取消副本 " + dungeonId + " 的強制下一波倒计时任务时发生错误: " + e.getMessage());
            }
        }

        // 清除强制倒计时数据
        forceNextWaveCountdowns.remove(dungeonId);
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
     * 播放倒數音效给副本中的所有玩家
     */
    private void playCountdownSound(String dungeonId) {
        DungeonSystem.SoundConfig config = plugin.getSoundConfig();
        if (config == null) {
            plugin.getLogger().warning("音效配置未載入，跳過倒數音效播放");
            return;
        }

        try {
            Sound sound = Sound.valueOf(config.getCountdownSound());
            for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
                if (dungeonId.equals(entry.getValue())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.playSound(player.getLocation(), sound,
                                (float) config.getVolume(),
                                (float) config.getCountdownPitch());
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("無效的倒數音效: " + config.getCountdownSound());
        }
    }

    /**
     * 播放波次完成音效给副本中的所有玩家
     */
    private void playWaveClearSound(String dungeonId) {
        DungeonSystem.SoundConfig config = plugin.getSoundConfig();
        if (config == null) {
            plugin.getLogger().warning("音效配置未載入，跳過波次完成音效播放");
            return;
        }

        try {
            Sound sound = Sound.valueOf(config.getWaveClearSound());
            for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
                if (dungeonId.equals(entry.getValue())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.playSound(player.getLocation(), sound,
                                (float) config.getVolume(),
                                (float) config.getPitch());
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("無效的波次完成音效: " + config.getWaveClearSound());
        }
    }

    /**
     * 播放副本完成音效给副本中的所有玩家
     */
    private void playDungeonCompleteSound(String dungeonId) {
        DungeonSystem.SoundConfig config = plugin.getSoundConfig();
        if (config == null) {
            plugin.getLogger().warning("音效配置未載入，跳過副本完成音效播放");
            return;
        }

        try {
            Sound sound = Sound.valueOf(config.getDungeonCompleteSound());
            for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
                if (dungeonId.equals(entry.getValue())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.playSound(player.getLocation(), sound,
                                (float) config.getVolume(),
                                (float) config.getPitch());
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("無效的副本完成音效: " + config.getDungeonCompleteSound());
        }
    }

    /**
     * 播放波次開始音效给副本中的所有玩家
     */
    private void playWaveStartSound(String dungeonId) {
        DungeonSystem.SoundConfig config = plugin.getSoundConfig();
        if (config == null) {
            plugin.getLogger().warning("音效配置未載入，跳過波次開始音效播放");
            return;
        }

        try {
            Sound sound = Sound.valueOf(config.getWaveStartSound());
            for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
                if (dungeonId.equals(entry.getValue())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.playSound(player.getLocation(), sound,
                                (float) config.getVolume(),
                                (float) config.getPitch());
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("無效的波次開始音效: " + config.getWaveStartSound());
        }
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
        Set<UUID> aliveEntities = new HashSet<>(); // 用來儲存還活著的怪物UUID

        for (UUID entityId : entities) {
            boolean exists = false;
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(entityId)) {
                        exists = true;
                        aliveEntities.add(entityId); // 添加到還活著的集合中
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

        // 更新實體集合，移除已經死亡的怪物
        waveEntities.put(dungeonId, aliveEntities);

        // 如果所有怪物都被清理
        if (allCleared && !entities.isEmpty()) {
            int currentWave = waveDungeon.getCurrentWave();
            broadcastToDungeon(dungeonId, "§a第 " + currentWave + " 波怪物已全部擊殺！");

            // 播放波次完成音效
            playWaveClearSound(dungeonId);

            // 清理強制倒數任務（因為正常完成了這一波）
            cleanupForceNextWaveCountdown(dungeonId);

            waveCleared.put(dungeonId, true);

            if (currentWave < waveDungeon.getTotalWaves()) {
                // 开始下一波的倒计时
                startNextWaveCountdown(dungeonId, 5);
            } else {
                // 最后一波已清理，标记完成
                completeDungeon(dungeonId);
            }
        } else if (remainingMobs > 0) {
            // 檢查是否需要顯示剩餘怪物數量（10秒顯示一次）
            long currentTime = System.currentTimeMillis();
            Long lastDisplayTime = lastMobCountDisplay.get(dungeonId);

            if (lastDisplayTime == null || currentTime - lastDisplayTime >= 10000) { // 10秒 = 10000毫秒
                // 根據剩餘怪物數量調整通知頻率和內容
                if (remainingMobs <= 5) {
                    // 當剩余怪物較少時，每次都通知玩家
                    broadcastToDungeon(dungeonId, "§e還剩 " + remainingMobs + " 隻怪物！");
                } else if (remainingMobs <= 10 && remainingMobs % 2 == 0) {
                    // 10隻以內，每2隻通知一次
                    broadcastToDungeon(dungeonId, "§e還剩 " + remainingMobs + " 隻怪物！");
                } else if (remainingMobs <= 20 && remainingMobs % 5 == 0) {
                    // 20隻以內，每5隻通知一次
                    broadcastToDungeon(dungeonId, "§e還剩 " + remainingMobs + " 隻怪物！");
                } else if (remainingMobs % 10 == 0) {
                    // 20隻以上，每10隻通知一次
                    broadcastToDungeon(dungeonId, "§e還剩 " + remainingMobs + " 隻怪物！");
                }

                // 更新最後顯示時間
                lastMobCountDisplay.put(dungeonId, currentTime);
            }
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

        // 獲取傷害統計和排名
        List<PlayerRanking> rankings = new ArrayList<>();
        if (plugin.getDamageTracker().hasDungeonStats(dungeonId)) {
            rankings = plugin.getDamageTracker().generateRankings(dungeonId);
        }

        // 檢查玩家獎勵資格並記錄符合條件的玩家
        Set<UUID> qualifiedPlayers = checkRewardEligibilityAndRecord(dungeonId, rankings);

        broadcastToDungeon(dungeonId, "§a§l恭喜你们！成功通過副本！5秒後自動返回主大廳");

        // 播放副本完成音效
        playDungeonCompleteSound(dungeonId);

        // 顯示戰鬥統計
        if (!rankings.isEmpty()) {
            displayRankingsToPlayers(dungeonId, rankings);
        }

        // 5秒后结束副本
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupAndExitDungeon(dungeonId);
            }
        }.runTaskLater(plugin, 100L);
    }

    /**
     * 檢查玩家獎勵資格並記錄符合條件的玩家
     *
     * @param dungeonId 副本ID
     * @param rankings  玩家排名列表
     * @return 符合獎勵條件的玩家UUID集合
     */
    private Set<UUID> checkRewardEligibilityAndRecord(String dungeonId, List<PlayerRanking> rankings) {
        Set<UUID> qualifiedPlayers = new HashSet<>();

        // 檢查是否啟用獎勵標準檢查
        if (!plugin.getConfig().getBoolean("dungeon-rewards.enabled", true)) {
            // 如果停用檢查，所有玩家都被記錄為成功通關
            for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
                if (dungeonId.equals(entry.getValue())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        plugin.getRankingManager().recordCompletion(player, dungeonId);
                        qualifiedPlayers.add(player.getUniqueId());
                        plugin.getLogger().info("記錄玩家 " + player.getName() + " 完成副本 " + dungeonId + "（檢查已停用）");
                    }
                }
            }
            return qualifiedPlayers;
        }

        if (rankings.isEmpty()) {
            plugin.getLogger().warning("副本 " + dungeonId + " 沒有傷害統計資料，所有玩家視為符合條件");
            // 沒有統計資料時，所有玩家都被記錄
            for (Map.Entry<UUID, String> entry : dungeonManager.getPlayerDungeons().entrySet()) {
                if (dungeonId.equals(entry.getValue())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        plugin.getRankingManager().recordCompletion(player, dungeonId);
                        qualifiedPlayers.add(player.getUniqueId());
                    }
                }
            }
            return qualifiedPlayers;
        }

        // 獲取傷害門檻倍數
        double thresholdMultiplier;
        String dungeonConfigPath = "dungeon-rewards.dungeons." + dungeonId + ".damage-threshold-multiplier";
        if (plugin.getConfig().contains(dungeonConfigPath)) {
            thresholdMultiplier = plugin.getConfig().getDouble(dungeonConfigPath);
        } else {
            thresholdMultiplier = plugin.getConfig().getDouble("dungeon-rewards.global.damage-threshold-multiplier", 0.5);
        }

        boolean showEligibility = plugin.getConfig().getBoolean("dungeon-rewards.global.show-reward-eligibility", true);

        // 計算獎勵門檻
        double highestDamage = rankings.get(0).getTotalDamage();
        double damageThreshold = highestDamage * thresholdMultiplier;

        plugin.getLogger().info("副本 " + dungeonId + " 獎勵門檻：" + damageThreshold +
                "（最高傷害 " + highestDamage + " × " + thresholdMultiplier + "）");

        // 顯示獎勵門檻訊息
        if (showEligibility) {
            String thresholdInfo = plugin.getConfig().getString(
                    "dungeon-rewards.global.messages.damage-threshold-info",
                    "§e本次獎勵門檻：{threshold} 傷害（最高輸出的 {percentage}%）"
            );

            String formattedMessage = thresholdInfo
                    .replace("{threshold}", String.format("%.1f", damageThreshold))
                    .replace("{percentage}", String.format("%.0f", thresholdMultiplier * 100));

            broadcastToDungeon(dungeonId, "");
            broadcastToDungeon(dungeonId, formattedMessage);
            broadcastToDungeon(dungeonId, "");
        }

        // 檢查每個玩家是否達到標準
        for (PlayerRanking ranking : rankings) {
            Player player = Bukkit.getPlayer(ranking.getPlayerId());
            if (player != null && player.isOnline()) {
                boolean qualified = ranking.getTotalDamage() >= damageThreshold;

                if (qualified) {
                    // 達到標準：記錄完成 + 發送訊息
                    plugin.getRankingManager().recordCompletion(player, dungeonId);
                    qualifiedPlayers.add(player.getUniqueId());

                    String qualifiedMessage = plugin.getConfig().getString(
                            "dungeon-rewards.global.messages.reward-qualified",
                            "§a§l恭喜！你已達到獎勵標準！"
                    );
                    player.sendMessage(qualifiedMessage);

                    plugin.getLogger().info("玩家 " + player.getName() + " 達到獎勵標準並記錄完成（傷害: " +
                            ranking.getTotalDamage() + " >= " + damageThreshold + "）");
                } else {
                    // 未達標準：不記錄完成，僅發送訊息
                    if (showEligibility) {
                        String notQualifiedMessage = plugin.getConfig().getString(
                                "dungeon-rewards.global.messages.reward-not-qualified",
                                "§c很遺憾，你的傷害未達到獎勵標準（需要達到最高輸出的 {threshold}%）"
                        );

                        String formattedMessage = notQualifiedMessage
                                .replace("{threshold}", String.format("%.0f", thresholdMultiplier * 100));

                        player.sendMessage(formattedMessage);
                    }

                    plugin.getLogger().info("玩家 " + player.getName() + " 未達到獎勵標準，不記錄完成（傷害: " +
                            ranking.getTotalDamage() + " < " + damageThreshold + "）");
                }
            }
        }

        return qualifiedPlayers;
    }

    /**
     * 顯示排名給副本中的玩家
     */
    private void displayRankingsToPlayers(String dungeonId, List<PlayerRanking> rankings) {
        if (rankings.isEmpty()) {
            return;
        }

        broadcastToDungeon(dungeonId, "");
        broadcastToDungeon(dungeonId, "§6§l=== 副本戰鬥統計 ===");

        for (int i = 0; i < rankings.size(); i++) {
            PlayerRanking ranking = rankings.get(i);

            // 根據排名設置顏色
            String rankColor = i == 0 ? "§e" : i == 1 ? "§7" : i == 2 ? "§6" : "§f";
            String medal = i == 0 ? "🏆" : i == 1 ? "🥈" : i == 2 ? "🥉" : "";

            broadcastToDungeon(dungeonId, String.format(
                    "%s第%d名 %s: %s",
                    rankColor, i + 1, medal, ranking.getPlayerName()
            ));

            broadcastToDungeon(dungeonId, String.format(
                    "%s  傷害: §c%.1f §7| 擊殺: §a%d §7| 死亡: §c%d §7| DPS: §e%.1f",
                    rankColor,
                    ranking.getTotalDamage(),
                    ranking.getKills(),
                    ranking.getDeaths(),
                    ranking.getDPS()
            ));

            if (i < rankings.size() - 1) {
                broadcastToDungeon(dungeonId, "");
            }
        }

        broadcastToDungeon(dungeonId, "§6§l==================");
        broadcastToDungeon(dungeonId, "");
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
        lastMobCountDisplay.remove(dungeonId);

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