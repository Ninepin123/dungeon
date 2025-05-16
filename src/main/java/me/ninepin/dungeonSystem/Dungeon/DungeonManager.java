package me.ninepin.dungeonSystem.Dungeon;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import me.ninepin.dungeonSystem.DungeonSystem;
import me.ninepin.dungeonSystem.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DungeonManager {

    private final DungeonSystem plugin;
    private final Map<String, Dungeon> dungeons;
    private final Map<UUID, String> playerDungeons; // 紀錄玩家在哪個副本中
    private final Map<String, UUID> activeDungeons;
    private final Map<String, List<UUID>> dungeonEntities;
    private final Map<String, Set<UUID>> deadPlayers;
    private final Map<String, List<String>> dungeonInstances;
    private final WaveDungeonManager waveDungeonManager;

    // 映射實例ID到其副本ID
    private final Map<String, String> instanceToDungeon;

    public DungeonManager(DungeonSystem plugin) {
        this.plugin = plugin;
        this.dungeons = new HashMap<>();
        this.playerDungeons = new HashMap<>();
        this.activeDungeons = new HashMap<>();
        this.dungeonEntities = new HashMap<>();
        this.deadPlayers = new HashMap<>();
        this.dungeonInstances = new HashMap<>();
        this.instanceToDungeon = new HashMap<>();
        this.waveDungeonManager = new WaveDungeonManager(plugin, this);
        // 從配置文件加載副本
        loadDungeons();
    }

    /**
     * 從配置文件加載所有副本信息
     */
    private void loadDungeons() {
        ConfigurationSection dungeonsSection = plugin.getConfig().getConfigurationSection("dungeons");

        if (dungeonsSection == null) {
            plugin.getLogger().warning("No dungeons found in config.yml!");
            return;
        }

        for (String instanceId : dungeonsSection.getKeys(false)) {
            ConfigurationSection dungeonSection = dungeonsSection.getConfigurationSection(instanceId);

            if (dungeonSection == null) continue;

            int levelRequired = dungeonSection.getInt("level-required", 0);
            int maxPlayers = dungeonSection.getInt("max-players", 4);
            String targetMobId = dungeonSection.getString("target-mob"); // 读取目标怪物ID

            String spawnPointStr = dungeonSection.getString("spawn-point");
            Location spawnPoint = parseLocation(spawnPointStr);

            if (spawnPoint == null) {
                plugin.getLogger().warning("Invalid spawn-point format for dungeon instance " + instanceId);
                continue;
            }

            String deathWaitingAreaStr = dungeonSection.getString("death-waiting-area");
            Location deathWaitingArea = parseLocation(deathWaitingAreaStr);

            if (deathWaitingArea == null) {
                plugin.getLogger().warning("Invalid death-waiting-area format for dungeon instance " + instanceId);
                continue;
            }

            // 读取副本模式
            String dungeonType = dungeonSection.getString("type", "normal");

            // 讀取普通副本的怪物配置（修改部分）
            List<DungeonMob> mobs = new ArrayList<>();
            if (dungeonSection.isList("mobs")) {
                List<Map<?, ?>> mobsList = dungeonSection.getMapList("mobs");
                for (Map<?, ?> mobMap : mobsList) {
                    String mobId = (String) mobMap.get("id");
                    String locationStr = (String) mobMap.get("location");
                    Location mobLocation = parseLocation(locationStr);

                    // 新增：讀取 amount 和 radius
                    int amount = 1; // 預設值
                    double radius = 0.0; // 預設值

                    // 讀取 amount（如果存在）
                    if (mobMap.containsKey("amount")) {
                        Object amountObj = mobMap.get("amount");
                        if (amountObj instanceof Number) {
                            amount = ((Number) amountObj).intValue();
                            // 確保數量至少為 1
                            if (amount < 1) {
                                plugin.getLogger().warning("Invalid amount (" + amount + ") for mob " + mobId + " in dungeon " + instanceId + ", setting to 1");
                                amount = 1;
                            }
                        }
                    }

                    // 讀取 radius（如果存在）
                    if (mobMap.containsKey("radius")) {
                        Object radiusObj = mobMap.get("radius");
                        if (radiusObj instanceof Number) {
                            radius = ((Number) radiusObj).doubleValue();
                            // 確保半徑不為負數
                            if (radius < 0.0) {
                                plugin.getLogger().warning("Invalid radius (" + radius + ") for mob " + mobId + " in dungeon " + instanceId + ", setting to 0.0");
                                radius = 0.0;
                            }
                        }
                    }

                    if (mobId != null && mobLocation != null) {
                        mobs.add(new DungeonMob(mobId, mobLocation, amount, radius));
                        plugin.getLogger().info("Loaded mob " + mobId + " with amount: " + amount + ", radius: " + radius + " in dungeon " + instanceId);
                    }
                }
            }

            Dungeon dungeon;

            // 根据副本类型创建不同的副本实例
            if ("wave".equalsIgnoreCase(dungeonType)) {
                // 加载波次副本的特殊配置
                int totalWaves = dungeonSection.getInt("waves.total", 1);
                Map<Integer, List<DungeonMob>> waveMobs = new HashMap<>();

                // 加载每一波的怪物配置（修改部分）
                ConfigurationSection wavesSection = dungeonSection.getConfigurationSection("waves");
                if (wavesSection != null) {
                    for (int wave = 1; wave <= totalWaves; wave++) {
                        String waveKey = "wave-" + wave;
                        if (wavesSection.isList(waveKey)) {
                            List<DungeonMob> waveMobList = new ArrayList<>();
                            List<Map<?, ?>> waveMobsList = wavesSection.getMapList(waveKey);

                            for (Map<?, ?> mobMap : waveMobsList) {
                                String mobId = (String) mobMap.get("id");
                                String locationStr = (String) mobMap.get("location");
                                Location mobLocation = parseLocation(locationStr);

                                // 新增：讀取 amount 和 radius
                                int amount = 1; // 預設值
                                double radius = 0.0; // 預設值

                                // 讀取 amount（如果存在）
                                if (mobMap.containsKey("amount")) {
                                    Object amountObj = mobMap.get("amount");
                                    if (amountObj instanceof Number) {
                                        amount = ((Number) amountObj).intValue();
                                        // 確保數量至少為 1
                                        if (amount < 1) {
                                            plugin.getLogger().warning("Invalid amount (" + amount + ") for mob " + mobId + " in wave " + wave + " of dungeon " + instanceId + ", setting to 1");
                                            amount = 1;
                                        }
                                    }
                                }

                                // 讀取 radius（如果存在）
                                if (mobMap.containsKey("radius")) {
                                    Object radiusObj = mobMap.get("radius");
                                    if (radiusObj instanceof Number) {
                                        radius = ((Number) radiusObj).doubleValue();
                                        // 確保半徑不為負數
                                        if (radius < 0.0) {
                                            plugin.getLogger().warning("Invalid radius (" + radius + ") for mob " + mobId + " in wave " + wave + " of dungeon " + instanceId + ", setting to 0.0");
                                            radius = 0.0;
                                        }
                                    }
                                }

                                if (mobId != null && mobLocation != null) {
                                    waveMobList.add(new DungeonMob(mobId, mobLocation, amount, radius));
                                    plugin.getLogger().info("Loaded wave mob " + mobId + " with amount: " + amount + ", radius: " + radius + " for wave " + wave + " in dungeon " + instanceId);
                                }
                            }

                            waveMobs.put(wave, waveMobList);
                        } else {
                            plugin.getLogger().warning("No mob configuration found for wave " + wave + " in dungeon " + instanceId);
                        }
                    }
                }

                // 创建波次副本实例
                dungeon = new WaveDungeon(instanceId, levelRequired, maxPlayers, spawnPoint, deathWaitingArea, mobs, targetMobId, totalWaves, waveMobs);
                plugin.getLogger().info("Loaded wave dungeon: " + instanceId + " with " + totalWaves + " waves");
            } else {
                // 创建普通副本实例
                dungeon = new Dungeon(instanceId, levelRequired, maxPlayers, spawnPoint, deathWaitingArea, mobs, targetMobId);
            }

            dungeons.put(instanceId, dungeon);

            // 更新副本ID映射
            String dungeonId = instanceId;
            if (instanceId.contains("_")) {
                String[] parts = instanceId.split("_");
                if (parts.length > 1) {
                    try {
                        Integer.parseInt(parts[parts.length - 1]);
                        StringBuilder dungeonIdBuilder = new StringBuilder(parts[0]);
                        for (int i = 1; i < parts.length - 1; i++) {
                            dungeonIdBuilder.append("_").append(parts[i]);
                        }
                        dungeonId = dungeonIdBuilder.toString();
                    } catch (NumberFormatException e) {
                        dungeonId = instanceId;
                    }
                }
            }

            instanceToDungeon.put(instanceId, dungeonId);
            List<String> instances = dungeonInstances.computeIfAbsent(dungeonId, k -> new ArrayList<>());
            instances.add(instanceId);
        }
    }

    // 尋找可用的副本實例
    public String findAvailableInstance(String dungeonId) {
        List<String> instances = dungeonInstances.get(dungeonId);
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        for (String instanceId : instances) {
            // 檢查此實例是否已被占用
            if (!activeDungeons.containsKey(instanceId)) {
                return instanceId;
            }
        }

        return null; // 沒有可用的實例
    }

    public Map<String, UUID> getActiveDungeons() {
        return activeDungeons;
    }

    public boolean isDungeonActive(String dungeonId) {
        return activeDungeons.containsKey(dungeonId);
    }

    /**
     * 將字符串解析為坐標對象
     * 格式: "world,x,y,z" 或 "world,x,y,z,yaw,pitch"
     */
    Location parseLocation(String locString) {
        if (locString == null) return null;

        String[] parts = locString.split(",");
        if (parts.length < 4) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());

            if (parts.length >= 6) {
                float yaw = Float.parseFloat(parts[4].trim());
                float pitch = Float.parseFloat(parts[5].trim());
                return new Location(world, x, y, z, yaw, pitch);
            }

            return new Location(world, x, y, z);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 添加怪物到波次副本的指定波次
     *
     * @param dungeonId 副本ID
     * @param mobId     怪物ID
     * @param location  位置
     * @param amount    數量
     * @param radius    半徑
     * @param wave      波次
     * @return 是否成功添加
     */
    public boolean addMobToWaveDungeon(String dungeonId, String mobId, Location location, int amount, double radius, int wave) {
        // 檢查副本是否存在
        Dungeon dungeon = dungeons.get(dungeonId);
        if (!(dungeon instanceof WaveDungeon)) {
            plugin.getLogger().warning("嘗試添加怪物到非波次副本或不存在的副本: " + dungeonId);
            return false;
        }

        WaveDungeon waveDungeon = (WaveDungeon) dungeon;

        // 檢查波次是否有效
        if (wave < 1 || wave > waveDungeon.getTotalWaves()) {
            plugin.getLogger().warning("無效的波次: " + wave + " (副本 " + dungeonId + " 共有 " + waveDungeon.getTotalWaves() + " 波)");
            return false;
        }

        // 創建新的 DungeonMob 對象
        DungeonMob newMob = new DungeonMob(mobId, location, amount, radius);

        // 使用 WaveDungeon 的方法添加怪物到指定波次
        waveDungeon.addMobToWave(wave, newMob);

        // 保存到配置文件
        boolean saved = saveMobToWaveConfig(dungeonId, mobId, location, amount, radius, wave);

        if (saved) {
            plugin.getLogger().info("已成功添加怪物 " + mobId + " 到副本 " + dungeonId + " 的第 " + wave + " 波");
        }

        return saved;
    }

    /**
     * 將怪物配置保存到波次副本的配置文件
     *
     * @param dungeonId 副本ID
     * @param mobId     怪物ID
     * @param location  位置
     * @param amount    數量
     * @param radius    半徑
     * @param wave      波次
     * @return 是否成功保存
     */
    private boolean saveMobToWaveConfig(String dungeonId, String mobId, Location location, int amount, double radius, int wave) {
        try {
            ConfigurationSection dungeonSection = plugin.getConfig().getConfigurationSection("dungeons." + dungeonId);
            if (dungeonSection == null) {
                plugin.getLogger().severe("找不到副本 " + dungeonId + " 的配置節點");
                return false;
            }

            ConfigurationSection wavesSection = dungeonSection.getConfigurationSection("waves");
            if (wavesSection == null) {
                wavesSection = dungeonSection.createSection("waves");
            }

            String waveKey = "wave-" + wave;

            // 獲取現有的該波次怪物列表
            List<Map<String, Object>> waveList = new ArrayList<>();
            if (wavesSection.isList(waveKey)) {
                List<Map<?, ?>> existingWave = wavesSection.getMapList(waveKey);
                for (Map<?, ?> mob : existingWave) {
                    Map<String, Object> mobMap = new HashMap<>();
                    for (Map.Entry<?, ?> entry : mob.entrySet()) {
                        mobMap.put(entry.getKey().toString(), entry.getValue());
                    }
                    waveList.add(mobMap);
                }
            }

            // 創建新的怪物配置
            Map<String, Object> mobMap = new HashMap<>();
            mobMap.put("id", mobId);
            mobMap.put("location", locationToString(location));
            mobMap.put("amount", amount);
            mobMap.put("radius", radius);

            // 添加到該波次
            waveList.add(mobMap);

            // 保存回配置
            wavesSection.set(waveKey, waveList);
            plugin.saveConfig();

            plugin.getLogger().info("已添加怪物 " + mobId + " 到副本 " + dungeonId + " 的第 " + wave + " 波配置文件");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("保存波次怪物配置到文件時發生錯誤: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 處理玩家在副本中死亡
     */
    public void handlePlayerDeath(Player player, String dungeonId) {
        UUID playerId = player.getUniqueId();
        Dungeon dungeon = dungeons.get(dungeonId);

        if (dungeon == null) return;

        if (plugin.isRevivalSystemEnabled()) {
            // 復活系統啟用，原有邏輯
            player.teleport(dungeon.getDeathWaitingArea());
            player.sendMessage("§c你在副本中死亡，已被傳送到等待區");

            // 記錄玩家死亡
            Set<UUID> dungeonDeadPlayers = deadPlayers.computeIfAbsent(dungeonId, k -> new HashSet<>());
            dungeonDeadPlayers.add(playerId);

            // 檢查隊伍狀態
            checkTeamStatus(dungeonId);
        } else {
            // 復活系統禁用，直接踢出副本
            // 從配置文件獲取退出點坐標
            String exitPointStr = plugin.getConfig().getString("settings.exit-point");
            Location exitPoint = parseLocation(exitPointStr);

            // 如果配置中沒有退出點或格式不正確，使用世界出生點作為備用
            if (exitPoint == null) {
                exitPoint = Bukkit.getWorlds().get(0).getSpawnLocation();
            }

            player.teleport(exitPoint);
            player.sendMessage("§c你在副本中死亡，已被傳送出副本");

            // 從記錄中移除此玩家
            playerDungeons.remove(playerId);

            // 檢查副本是否空了
            checkIfDungeonEmpty(dungeonId);
        }
    }

    // 新增方法：檢查副本是否空了
    private void checkIfDungeonEmpty(String dungeonId) {
        boolean dungeonEmpty = true;
        for (UUID id : playerDungeons.keySet()) {
            if (dungeonId.equals(playerDungeons.get(id))) {
                dungeonEmpty = false;
                break;
            }
        }

        // 如果副本空了，清理怪物並釋放副本
        if (dungeonEmpty) {
            Dungeon dungeon = dungeons.get(dungeonId);
            if (dungeon instanceof WaveDungeon) {
                waveDungeonManager.cancelWaveTimer(dungeonId);
            }

            cleanupDungeon(dungeonId);
            deadPlayers.remove(dungeonId);
            activeDungeons.remove(dungeonId);
        }
    }

    /**
     * 處理玩家在副本中斷線
     */
    public void handlePlayerDisconnect(Player player, String dungeonId) {
        UUID playerId = player.getUniqueId();

        // 如果玩家不在副本中，直接返回
        if (dungeonId == null) {
            return;
        }

        // 從記錄中移除此玩家
        playerDungeons.remove(playerId);

        // 從死亡玩家列表中移除
        Set<UUID> dungeonDeadPlayers = deadPlayers.getOrDefault(dungeonId, new HashSet<>());
        dungeonDeadPlayers.remove(playerId);

        plugin.getLogger().info("Player " + player.getName() + " disconnected from dungeon " + dungeonId);

        // 檢查隊伍狀態
        checkTeamStatus(dungeonId);
    }

    /**
     * 初始化副本状态检查任务
     */
    public void initDungeonCheckTask() {
        // 创建一个每30秒执行一次的任务，检查所有副本状态
        new BukkitRunnable() {
            @Override
            public void run() {
                // 复制一份活跃副本列表，避免并发修改异常
                Set<String> activeDungeonIds = new HashSet<>(activeDungeons.keySet());

                for (String dungeonId : activeDungeonIds) {
                    checkTeamStatus(dungeonId);
                }
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // 每30秒检查一次
    }

    /**
     * 检查所有活跃副本的状态
     */
    private void checkAllDungeons() {
        // 复制一份活跃副本列表，避免并发修改异常
        Set<String> activeDungeonIds = new HashSet<>(activeDungeons.keySet());

        for (String dungeonId : activeDungeonIds) {
            // 检查副本中是否还有玩家
            boolean hasPlayers = false;
            for (Map.Entry<UUID, String> entry : playerDungeons.entrySet()) {
                if (dungeonId.equals(entry.getValue())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        hasPlayers = true;
                        break;
                    } else {
                        // 如果玩家不在线，移除记录
                        playerDungeons.remove(entry.getKey());
                    }
                }
            }

            // 如果没有玩家，清理副本
            if (!hasPlayers) {
                plugin.getLogger().info("No players in dungeon " + dungeonId + ", cleaning up...");
                cleanupDungeon(dungeonId);
                deadPlayers.remove(dungeonId);
                activeDungeons.remove(dungeonId);
            }
        }
    }

    /**
     * 檢查隊伍狀態
     */
    private void checkTeamStatus(String dungeonId) {
        // 如果復活系統被禁用，則不需要檢查團滅
        if (!plugin.isRevivalSystemEnabled()) {
            return;
        }

        Set<UUID> dungeonDeadPlayers = deadPlayers.getOrDefault(dungeonId, new HashSet<>());
        Set<UUID> dungeonPlayers = new HashSet<>();

        // 獲取在副本中的所有在線玩家
        for (Map.Entry<UUID, String> entry : playerDungeons.entrySet()) {
            if (dungeonId.equals(entry.getValue())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    dungeonPlayers.add(entry.getKey());
                } else {
                    // 玩家不在線，視為已退出
                    playerDungeons.remove(entry.getKey());
                }
            }
        }

        // 如果沒有玩家在副本中，或所有玩家都已死亡
        if (dungeonPlayers.isEmpty() || dungeonPlayers.size() == dungeonDeadPlayers.size()) {
            // 副本挑戰失敗
            failDungeon(dungeonId);
        }
    }

    /**
     * 副本挑戰失敗，將所有隊員傳送出副本
     */
    private void failDungeon(String dungeonId) {
        plugin.getLogger().info("Dungeon " + dungeonId + " challenge failed");

        // 找出在此副本的所有玩家
        List<UUID> playersInDungeon = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerDungeons.entrySet()) {
            if (dungeonId.equals(entry.getValue())) {
                playersInDungeon.add(entry.getKey());
            }
        }

        // 通知所有玩家副本失敗，5秒後將被傳送出副本
        for (UUID playerId : playersInDungeon) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§c副本挑戰失敗，5秒後將傳送出副本...");
            }
        }

        // 延遲5秒後傳送玩家出副本
        new BukkitRunnable() {
            @Override
            public void run() {
                // 從配置文件獲取退出點坐標
                String exitPointStr = plugin.getConfig().getString("settings.exit-point");
                Location exitPoint = parseLocation(exitPointStr);

                // 如果配置中沒有退出點或格式不正確，使用世界出生點作為備用
                if (exitPoint == null) {
                    exitPoint = Bukkit.getWorlds().get(0).getSpawnLocation();
                }

                // 傳送所有玩家出副本
                for (UUID playerId : playersInDungeon) {
                    playerDungeons.remove(playerId);

                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.teleport(exitPoint);
                        player.sendMessage("§c你已被傳送出副本");
                    }
                }

                // 清理副本
                cleanupDungeon(dungeonId);
                deadPlayers.remove(dungeonId);
                activeDungeons.remove(dungeonId);

                plugin.getLogger().info("Cleaned up failed dungeon: " + dungeonId);
            }
        }.runTaskLater(plugin, 5 * 20L); // 5秒 = 5 * 20 ticks
    }

    /**
     * 获取玩家副本映射表
     *
     * @return 玩家UUID到副本ID的映射
     */
    public Map<UUID, String> getPlayerDungeons() {
        return playerDungeons;
    }

    /**
     * 獲取指定ID的副本
     */
    public Dungeon getDungeon(String dungeonId) {
        return dungeons.get(dungeonId);
    }

    /**
     * 檢查玩家是否可以進入指定副本
     *
     * @return 如果無法進入，返回錯誤訊息；如果可以進入，返回null
     */
    public String canJoinDungeon(Player player, Dungeon dungeon) {
        // 檢查副本是否已被佔用
        if (activeDungeons.containsKey(dungeon.getId())) {
            return "此副本已被其他隊伍佔用，請稍後再試";
        }

        // 檢查玩家是否在隊伍中
        Party party = plugin.getPartyManager().getPlayerParty(player.getUniqueId());
        if (party == null) {
            return "你必須加入一個隊伍才能進入副本";
        }

        // 檢查隊伍人數是否超過上限
        if (party.getSize() > dungeon.getMaxPlayers()) {
            return "你的隊伍人數超過了此副本的上限 (" + dungeon.getMaxPlayers() + " 人)";
        }

        return null; // 沒有返回錯誤訊息，表示可以進入
    }

    /**
     * 讓玩家隊伍進入副本
     */
    public boolean joinDungeon(Player player, String dungeonId) {
        // 使用你的队伍系统
        Party party = plugin.getPartyManager().getPlayerParty(player.getUniqueId());

        if (party == null) return false;

        // 检查所有队员是否已在副本中
        for (UUID memberId : party.getMemberUUIDs()) {
            Player memberPlayer = Bukkit.getPlayer(memberId);
            if (memberPlayer != null && memberPlayer.isOnline()) {
                String memberDungeonId = getPlayerDungeon(memberPlayer.getUniqueId());
                if (memberDungeonId != null) {
                    player.sendMessage("§c队伍成员 " + memberPlayer.getName() + " 已经在副本中，无法进入新副本");
                    return false;
                }
            }
        }

        // 寻找可用的副本实例
        String instanceId;
        if (dungeons.containsKey(dungeonId)) {
            // 直接使用指定的实例ID
            instanceId = dungeonId;
        } else {
            // 寻找该副本的可用实例
            instanceId = findAvailableInstance(dungeonId);
            if (instanceId == null) {
                player.sendMessage("§c目前没有可用的 " + dungeonId + " 副本场地，请稍后再试");
                return false;
            }
        }

        Dungeon dungeon = dungeons.get(instanceId);
        if (dungeon == null) {
            player.sendMessage("§c找不到可用的副本");
            return false;
        }

        // 检查是否可以进入
        String error = canJoinDungeon(player, dungeon);
        if (error != null) {
            player.sendMessage("§c" + error);
            return false;
        }

        // 标记此实例为已占用
        activeDungeons.put(instanceId, player.getUniqueId());

        // 传送所有队伍成员到副本起点
        for (UUID memberId : party.getMemberUUIDs()) {
            Player memberPlayer = Bukkit.getPlayer(memberId);
            if (memberPlayer != null && memberPlayer.isOnline()) {
                memberPlayer.teleport(dungeon.getSpawnPoint());
                playerDungeons.put(memberPlayer.getUniqueId(), instanceId);
                memberPlayer.sendMessage("§a你已进入副本: §e" + getDungeonDisplayName(instanceId));
            }
        }

        // 根据副本类型进行不同处理
        if (dungeon instanceof WaveDungeon) {
            // 波次副本处理
            waveDungeonManager.startWaveDungeon(instanceId);
        } else {
            // 普通副本处理 - 生成副本怪物
            spawnDungeonMobs(dungeon);
        }

        player.sendMessage("§a你的队伍已进入副本 §e" + getDungeonDisplayName(instanceId));
        return true;
    }

    // 獲取副本顯示名稱
    private String getDungeonDisplayName(String instanceId) {
        String dungeonId = instanceToDungeon.getOrDefault(instanceId, instanceId);
        return dungeonId;
    }

    /**
     * 添加怪物到副本配置並保存到配置文件
     *
     * @param dungeonId 副本ID
     * @param mobId     怪物ID
     * @param location  位置
     * @param amount    數量
     * @param radius    半徑
     * @return 是否成功添加
     */
    public boolean addMobToDungeon(String dungeonId, String mobId, Location location, int amount, double radius) {
        // 檢查副本是否存在
        Dungeon dungeon = dungeons.get(dungeonId);
        if (dungeon == null) {
            plugin.getLogger().warning("嘗試添加怪物到不存在的副本: " + dungeonId);
            return false;
        }

        // 創建新的 DungeonMob 對象
        DungeonMob newMob = new DungeonMob(mobId, location, amount, radius);

        // 添加到內存中的副本對象
        dungeon.getMobs().add(newMob);

        // 保存到配置文件
        return saveMobToConfig(dungeonId, mobId, location, amount, radius);
    }

    /**
     * 將怪物配置保存到配置文件
     *
     * @param dungeonId 副本ID
     * @param mobId     怪物ID
     * @param location  位置
     * @param amount    數量
     * @param radius    半徑
     * @return 是否成功保存
     */

    private boolean saveMobToConfig(String dungeonId, String mobId, Location location, int amount, double radius) {
        try {
            ConfigurationSection dungeonSection = plugin.getConfig().getConfigurationSection("dungeons." + dungeonId);
            if (dungeonSection == null) {
                plugin.getLogger().severe("找不到副本 " + dungeonId + " 的配置節點");
                return false;
            }

            // 獲取現有的怪物列表
            List<Map<String, Object>> mobsList = new ArrayList<>();
            if (dungeonSection.isList("mobs")) {
                // 創建一個新的列表，複製原有的內容
                List<Map<?, ?>> existingMobs = dungeonSection.getMapList("mobs");
                for (Map<?, ?> mob : existingMobs) {
                    Map<String, Object> mobMap = new HashMap<>();
                    for (Map.Entry<?, ?> entry : mob.entrySet()) {
                        mobMap.put(entry.getKey().toString(), entry.getValue());
                    }
                    mobsList.add(mobMap);
                }
            }

            // 創建新的怪物配置
            Map<String, Object> mobMap = new HashMap<>();
            mobMap.put("id", mobId);
            mobMap.put("location", locationToString(location));
            mobMap.put("amount", amount);
            mobMap.put("radius", radius);

            // 添加到列表
            mobsList.add(mobMap);

            // 保存回配置
            dungeonSection.set("mobs", mobsList);
            plugin.saveConfig();

            plugin.getLogger().info("已添加怪物 " + mobId + " 到副本 " + dungeonId + " 的配置文件");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("保存怪物配置到文件時發生錯誤: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 檢查是否有指定ID的副本可用
     */
    public boolean isDungeonAvailable(String dungeonId) {
        return dungeonInstances.containsKey(dungeonId);
    }

    /**
     * 獲取副本中的死亡玩家
     */
    public Set<UUID> getDeadPlayers(String dungeonId) {
        return deadPlayers.get(dungeonId);
    }

    /**
     * 獲取所有可用的副本ID（不包括實例ID）
     */
    public List<String> getAvailableDungeonIds() {
        return new ArrayList<>(dungeonInstances.keySet());
    }

    /**
     * 在副本中生成所有怪物
     */
    private void spawnDungeonMobs(Dungeon dungeon) {
        // 創建一個新的 UUID 列表來存儲這個副本的實體
        List<UUID> entities = new ArrayList<>();
        dungeonEntities.put(dungeon.getId(), entities);

        for (DungeonMob mob : dungeon.getMobs()) {
            try {
                // 根據 amount 生成多隻怪物
                int amount = mob.getAmount();
                double radius = mob.getRadius();
                Location baseLocation = mob.getLocation();

                for (int i = 0; i < amount; i++) {
                    Location spawnLocation;

                    if (radius > 0) {
                        // 在指定半徑內隨機生成位置
                        spawnLocation = getRandomLocationInRadius(baseLocation, radius);
                    } else {
                        // 使用原始位置
                        spawnLocation = baseLocation.clone();
                    }

                    // 生成怪物並獲取實體引用
                    ActiveMob entity = MythicBukkit.inst().getMobManager().spawnMob(mob.getId(), spawnLocation);
                    if (entity != null) {
                        // 保存怪物的 UUID
                        entities.add(entity.getUniqueId());
                        plugin.getLogger().info("Spawned MythicMob " + mob.getId() +
                                " at " + locationToString(spawnLocation) +
                                " in dungeon " + dungeon.getId());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to spawn MythicMob " + mob.getId() + ": " + e.getMessage());
            }
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

        // 確保位置在地面上（可選）
        // randomLocation.setY(center.getWorld().getHighestBlockYAt(randomLocation) + 1);

        return randomLocation;
    }

    /**
     * 將位置轉換為字符串，用於日誌
     */
    public String locationToString(Location loc) {
        if (loc == null) return "null";
        return String.format("%s,%.2f,%.2f,%.2f",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ());
    }

    /**
     * 讓玩家離開當前副本
     *
     * @param player 要離開副本的玩家
     * @return 是否成功離開
     */
    public boolean leaveDungeon(Player player) {
        UUID playerId = player.getUniqueId();
        String dungeonId = playerDungeons.get(playerId);

        if (dungeonId == null) {
            return false; // 玩家不在任何副本中
        }

        // 获取副本对象
        Dungeon dungeon = dungeons.get(dungeonId);

        // 从配置文件获取退出点坐标
        String exitPointStr = plugin.getConfig().getString("settings.exit-point");
        Location exitPoint = parseLocation(exitPointStr);

        // 如果配置中没有退出点或格式不正确，使用世界出生点作为备用
        if (exitPoint == null) {
            exitPoint = player.getWorld().getSpawnLocation();
        }

        // 将玩家传送至退出点
        player.teleport(exitPoint);

        // 如果玩家是队长，且有队伍，则同时让整个队伍离开
        Party party = plugin.getPartyManager().getPlayerParty(player.getUniqueId());

        if (party != null && party.isOwner(player.getUniqueId())) {
            for (UUID memberId : party.getMemberUUIDs()) {
                Player memberPlayer = Bukkit.getPlayer(memberId);
                if (memberPlayer != null && memberPlayer.isOnline() && !memberId.equals(playerId)) {
                    if (dungeonId.equals(playerDungeons.get(memberId))) {
                        memberPlayer.teleport(exitPoint);
                        playerDungeons.remove(memberId);
                        memberPlayer.sendMessage("§a队长已让整个队伍离开副本");
                    }
                }
            }
        }

        // 从记录中移除此玩家
        playerDungeons.remove(playerId);
        boolean dungeonEmpty = true;
        for (UUID id : playerDungeons.keySet()) {
            if (dungeonId.equals(playerDungeons.get(id))) {
                dungeonEmpty = false;
                break;
            }
        }

        // 如果副本空了，清理怪物并释放副本
        if (dungeonEmpty) {
            // 如果是波次副本，取消波次定时器
            if (dungeon instanceof WaveDungeon) {
                waveDungeonManager.cancelWaveTimer(dungeonId);
            }

            cleanupDungeon(dungeonId);
            deadPlayers.remove(dungeonId);
            activeDungeons.remove(dungeonId);
        }
        return true;
    }

    /**
     * 清理副本中的所有怪物
     */
    public void cleanupDungeon(String dungeonId) {
        plugin.getLogger().info("正在清理副本: " + dungeonId);

        // 清理普通副本的实体
        List<UUID> entities = dungeonEntities.get(dungeonId);
        if (entities != null) {
            for (UUID entityId : entities) {
                // 寻找并移除实体
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (entity.getUniqueId().equals(entityId)) {
                            entity.remove();
                            break;
                        }
                    }
                }
            }
            // 清空记录
            entities.clear();
            dungeonEntities.remove(dungeonId);
        }

        // 获取副本对象
        Dungeon dungeon = dungeons.get(dungeonId);

        // 如果是波次副本，执行额外的清理
        if (dungeon instanceof WaveDungeon) {
            // 取消波次定时器
            waveDungeonManager.cancelWaveTimer(dungeonId);

            // 清理波次实体
            Set<UUID> waveEntities = waveDungeonManager.getWaveEntities(dungeonId);
            if (waveEntities != null) {
                for (UUID entityId : waveEntities) {
                    for (World world : Bukkit.getWorlds()) {
                        for (Entity entity : world.getEntities()) {
                            if (entity.getUniqueId().equals(entityId)) {
                                entity.remove();
                                break;
                            }
                        }
                    }
                }
            }

            // 清理波次状态记录
            waveDungeonManager.clearWaveData(dungeonId);
        }

        // 检查是否有玩家仍在此副本中
        boolean playersExist = false;
        for (Map.Entry<UUID, String> entry : playerDungeons.entrySet()) {
            if (dungeonId.equals(entry.getValue())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    playersExist = true;

                    // 获取退出点并传送玩家
                    String exitPointStr = plugin.getConfig().getString("settings.exit-point");
                    Location exitPoint = parseLocation(exitPointStr);
                    if (exitPoint == null) {
                        exitPoint = player.getWorld().getSpawnLocation();
                    }
                    player.teleport(exitPoint);
                    player.sendMessage("§c副本已被强制清理，你已被传送出副本");

                    // 从记录中移除
                    playerDungeons.remove(entry.getKey());
                }
            }
        }

        if (playersExist) {
            plugin.getLogger().warning("副本 " + dungeonId + " 清理时仍有玩家在内，已强制传送");
        }

        // 清理死亡玩家记录
        deadPlayers.remove(dungeonId);

        // 从活跃副本列表中移除
        activeDungeons.remove(dungeonId);

        // 记录日志
        plugin.getLogger().info("副本 " + dungeonId + " 清理完成");
    }

    /**
     * 獲取玩家當前所在的副本ID
     */
    public String getPlayerDungeon(UUID playerId) {
        return playerDungeons.get(playerId);
    }

    /**
     * 獲取所有可用的副本
     *
     * @return 副本映射表，鍵為副本ID，值為副本對象
     */
    public Map<String, Dungeon> getAllDungeons() {
        return Collections.unmodifiableMap(dungeons);
    }

    /**
     * 重新加載所有副本配置
     */
    public void reloadDungeons() {
        dungeons.clear();
        plugin.reloadConfig();
        loadDungeons();
    }

    /**
     * 获取波次副本管理器
     *
     * @return 波次副本管理器实例
     */
    public WaveDungeonManager getWaveDungeonManager() {
        return waveDungeonManager;
    }
}