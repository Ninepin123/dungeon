package me.ninepin.dungeonSystem.key;

import me.ninepin.dungeonSystem.Dungeon.Dungeon;
import me.ninepin.dungeonSystem.Dungeon.WaveDungeon;
import me.ninepin.dungeonSystem.DungeonSystem;
import me.ninepin.dungeonSystem.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KeyManager {
    private final DungeonSystem plugin;
    private final File keyFile;
    private FileConfiguration keyConfig;
    private final NamespacedKey dungeonKeyKey;
    private final NamespacedKey dungeonTypeKey;
    private final Map<UUID, Long> playerKeyCooldowns;
    // 儲存副本基礎ID到實例ID的映射
    private final Map<String, String> baseToDungeonId;
    private long keyCooldownTime;
    private final Map<UUID, Boolean> playersToNotify;

    public KeyManager(DungeonSystem plugin) {
        this.plugin = plugin;
        this.keyFile = new File(plugin.getDataFolder(), "keys.yml");
        this.dungeonKeyKey = new NamespacedKey(plugin, "dungeon_key");
        this.dungeonTypeKey = new NamespacedKey(plugin, "dungeon_type");
        this.playerKeyCooldowns = new HashMap<>();
        this.baseToDungeonId = new HashMap<>();
        this.keyCooldownTime = plugin.getConfig().getLong("key-settings.cooldown-seconds", 180) * 1000L;
        this.playersToNotify = new HashMap<>();
        loadConfig();
        startCooldownCheckTask();
    }

    private void startCooldownCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkAndNotifyCooldownComplete();
        }, 20L, 20L); // 每秒檢查一次
    }

    private void checkAndNotifyCooldownComplete() {
        long currentTime = System.currentTimeMillis();
        List<UUID> completedCooldowns = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : playerKeyCooldowns.entrySet()) {
            UUID playerId = entry.getKey();
            long cooldownEnd = entry.getValue();

            // 如果冷卻已完成且玩家需要通知
            if (currentTime >= cooldownEnd && playersToNotify.containsKey(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    // 發送通知訊息
                    notifyPlayerCooldownComplete(player);
                    // 播放通知音效
                    playCooldownCompleteSound(player);
                }

                completedCooldowns.add(playerId);
            }
        }

        // 清理已完成的冷卻記錄
        for (UUID playerId : completedCooldowns) {
            playerKeyCooldowns.remove(playerId);
            playersToNotify.remove(playerId);
        }
    }

    private void notifyPlayerCooldownComplete(Player player) {
        // 發送聊天訊息
        player.sendMessage("§a§l[副本系統] §f你的副本入場卷冷卻已完成！可以再次使用了");

        // 發送標題通知（可選）
        player.sendTitle("§a副本入場卷", "§f冷卻完成！", 10, 40, 10);

        plugin.getLogger().info("通知玩家 " + player.getName() + " 副本鑰匙冷卻已完成");
    }

    private void playCooldownCompleteSound(Player player) {
        try {
            // 可以在配置文件中設定這個音效
            String soundName = plugin.getConfig().getString("key-settings.cooldown-complete-sound", "ENTITY_PLAYER_LEVELUP");
            double volume = plugin.getConfig().getDouble("key-settings.cooldown-complete-volume", 1.0);
            double pitch = plugin.getConfig().getDouble("key-settings.cooldown-complete-pitch", 1.0);

            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
        } catch (IllegalArgumentException e) {
            // 如果音效名稱無效，使用預設音效
            plugin.getLogger().warning("無效的冷卻完成音效名稱，使用預設音效");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private void loadConfig() {
        if (!keyFile.exists()) {
            plugin.saveResource("keys.yml", false);
        }
        keyConfig = YamlConfiguration.loadConfiguration(keyFile);

        // 確保權限節點存在
        if (!keyConfig.contains("permissions")) {
            keyConfig.createSection("permissions");
            saveConfig();
        }

        initializeKeys();
    }

    public void reload() {
        // 清空現有的配置信息
        keyConfig = null;
        baseToDungeonId.clear();
        this.keyCooldownTime = plugin.getConfig().getLong("key-settings.cooldown-seconds", 180) * 1000L;
        plugin.getLogger().info("重新載入鑰匙冷卻時間: " + (keyCooldownTime / 1000L) + " 秒");
        // 重新加載配置
        loadConfig();
        // 重新初始化副本鑰匙
        initializeKeys();
        plugin.getLogger().info("重新加載了副本入場卷設定");
    }

    private void initializeKeys() {
        // 先建立所有副本的基礎ID映射
        buildBaseIdMapping();

        // 遍歷所有副本的基礎ID，確保每個基礎ID都有對應的鑰匙配置
        for (String baseId : baseToDungeonId.keySet()) {
            // 获取一个实例ID来获取副本类型
            String instanceId = baseToDungeonId.get(baseId);
            Dungeon dungeon = plugin.getDungeonManager().getDungeon(instanceId);
            boolean isWaveDungeon = (dungeon instanceof WaveDungeon);

            // 設定權限ID
            String permission = "dungeonsystem.key." + baseId.toLowerCase();
            if (isWaveDungeon) {
                permission += ".wave";
            }

            // 將權限保存到配置中
            keyConfig.set("permissions." + baseId, permission);

            // 根据副本类型设置不同的默认配置
            if (!keyConfig.contains("keys." + baseId)) {
                String displayName;
                List<String> lore = new ArrayList<>();
                String material;
                int customModelData;

                if (isWaveDungeon) {
                    // 波次副本钥匙配置
                    displayName = "§b" + baseId + " 波次副本入場卷";
                    lore.add("§7使用此卷進入 " + baseId + " 波次副本");

                    // 如果是波次副本，添加波次信息
                    if (dungeon instanceof WaveDungeon) {
                        WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                        lore.add("§b總波數: §f" + waveDungeon.getTotalWaves());
                    }

                    lore.add("§7需要清理所有波次的怪物");
                    lore.add("§7右鍵點擊使用");
                    material = "BOOK";  // 使用不同的材质
                    customModelData = 2; // 不同的模型数据
                } else {
                    // 普通副本钥匙配置
                    displayName = "§e" + baseId + " 副本入場卷";
                    lore.add("§7使用此卷進入 " + baseId + " 副本");
                    lore.add("§7右鍵點擊使用");
                    material = "PAPER";
                    customModelData = 1;
                }

                keyConfig.set("keys." + baseId + ".name", displayName);
                keyConfig.set("keys." + baseId + ".lore", lore);
                keyConfig.set("keys." + baseId + ".material", material);
                keyConfig.set("keys." + baseId + ".custom_model_data", customModelData);
                keyConfig.set("keys." + baseId + ".is_wave", isWaveDungeon);

                // 添加預設音效配置
                String defaultSound = isWaveDungeon ? "BLOCK_LEVER_CLICK" : "ENTITY_EXPERIENCE_ORB_PICKUP";
                keyConfig.set("keys." + baseId + ".use_sound.sound", defaultSound);
                keyConfig.set("keys." + baseId + ".use_sound.volume", 1.0);
                keyConfig.set("keys." + baseId + ".use_sound.pitch", 1.0);

                saveConfig();

                plugin.getLogger().info("為副本 " + baseId + " 創建了鑰匙配置（類型：" + (isWaveDungeon ? "波次" : "普通") + "）");
            } else {
                // 检查现有条目是否需要更新波次状态
                keyConfig.set("keys." + baseId + ".is_wave", isWaveDungeon);

                // 確保現有配置有音效設定，如果沒有就添加預設值
                if (!keyConfig.contains("keys." + baseId + ".use_sound")) {
                    String defaultSound = isWaveDungeon ? "BLOCK_LEVER_CLICK" : "ENTITY_EXPERIENCE_ORB_PICKUP";
                    keyConfig.set("keys." + baseId + ".use_sound.sound", defaultSound);
                    keyConfig.set("keys." + baseId + ".use_sound.volume", 1.0);
                    keyConfig.set("keys." + baseId + ".use_sound.pitch", 1.0);
                    plugin.getLogger().info("為副本 " + baseId + " 添加了預設音效配置");
                }

                // 如果副本类型发生变化(普通→波次或波次→普通)，可能需要更新配置
                boolean configIsWave = keyConfig.getBoolean("keys." + baseId + ".is_wave");
                if (configIsWave != isWaveDungeon) {
                    plugin.getLogger().info("副本 " + baseId + " 类型已变更，更新入场卷配置");
                    // 這裡可以添加邏輯來更新現有配置的名稱和描述，
                    // 但為避免覆蓋管理員的自定義設置，這裡不自動更新
                }
            }
        }
        saveConfig();
    }

    /**
     * 檢查玩家是否在鑰匙使用冷卻中
     *
     * @param playerId 玩家UUID
     * @return 是否在冷卻中
     */
    public boolean isPlayerOnKeyCooldown(UUID playerId) {
        Long cooldownEnd = playerKeyCooldowns.get(playerId);
        if (cooldownEnd == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= cooldownEnd) {
            // 冷卻已結束，移除記錄
            playerKeyCooldowns.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * 獲取玩家剩餘的冷卻時間（秒）
     *
     * @param playerId 玩家UUID
     * @return 剩餘冷卻時間（秒），如果沒有冷卻則返回0
     */
    public long getPlayerKeyCooldownRemaining(UUID playerId) {
        Long cooldownEnd = playerKeyCooldowns.get(playerId);
        if (cooldownEnd == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= cooldownEnd) {
            playerKeyCooldowns.remove(playerId);
            return 0;
        }

        return (cooldownEnd - currentTime) / 1000L; // 轉換為秒
    }

    /**
     * 檢查玩家是否開啟了冷卻通知
     *
     * @param playerId 玩家UUID
     * @return 是否開啟通知
     */
    public boolean isPlayerNotificationEnabled(UUID playerId) {
        return playersToNotify.containsKey(playerId);
    }

    /**
     * 設置玩家是否接收冷卻完成通知
     *
     * @param playerId 玩家UUID
     * @param notify   是否通知
     */
    public void setPlayerNotification(UUID playerId, boolean notify) {
        if (playerKeyCooldowns.containsKey(playerId)) {
            if (notify) {
                playersToNotify.put(playerId, true);
            } else {
                playersToNotify.remove(playerId);
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                if (notify) {
                    player.sendMessage("§a已開啟副本入場卷冷卻完成通知");
                } else {
                    player.sendMessage("§c已關閉副本入場卷冷卻完成通知");
                }
            }
        }
    }

    /**
     * 為玩家設置鑰匙使用冷卻
     *
     * @param playerId 玩家UUID
     */
    public void setPlayerKeyCooldown(UUID playerId) {
        long cooldownEnd = System.currentTimeMillis() + keyCooldownTime;
        playerKeyCooldowns.put(playerId, cooldownEnd);

        // 新增：標記此玩家需要接收冷卻完成通知
        playersToNotify.put(playerId, true);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            int cooldownSeconds = (int) (keyCooldownTime / 1000L);
            player.sendMessage("§e鑰匙使用冷卻已開始，" + formatTime(cooldownSeconds) + " 內無法再次使用鑰匙");
            player.sendMessage("§7冷卻完成時會收到通知");
        }

        plugin.getLogger().info("玩家 " + (player != null ? player.getName() : playerId.toString()) +
                " 開始鑰匙冷卻，持續 " + (keyCooldownTime / 1000L) + " 秒");
    }

    /**
     * 移除玩家的鑰匙冷卻（管理員指令用）
     *
     * @param playerId 玩家UUID
     * @return 是否成功移除
     */
    public boolean removePlayerKeyCooldown(UUID playerId) {
        boolean hadCooldown = playerKeyCooldowns.containsKey(playerId);
        playerKeyCooldowns.remove(playerId);
        playersToNotify.remove(playerId); // 新增：同時移除通知標記

        // 如果玩家在線，通知他們冷卻已被移除
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && hadCooldown) {
            player.sendMessage("§a管理員已移除你的副本入場卷冷卻");
        }

        return hadCooldown;
    }


    /**
     * 檢查隊伍中是否有任何成員在鑰匙冷卻中
     *
     * @param party 隊伍對象
     * @return 在冷卻中的成員列表
     */
    public List<String> getPartyMembersOnCooldown(Party party) {
        List<String> membersOnCooldown = new ArrayList<>();

        for (UUID memberUUID : party.getMemberUUIDs()) {
            if (isPlayerOnKeyCooldown(memberUUID)) {
                Player member = Bukkit.getPlayer(memberUUID);
                String memberName = member != null ? member.getName() : party.getMembers().get(memberUUID);

                long remainingTime = getPlayerKeyCooldownRemaining(memberUUID);
                membersOnCooldown.add(memberName + " (" + formatTime((int) remainingTime) + ")");
            }
        }

        return membersOnCooldown;
    }

    /**
     * 格式化時間顯示
     *
     * @param seconds 秒數
     * @return 格式化的時間字符串
     */
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

    /**
     * 獲取所有在冷卻中的玩家信息（管理員指令用）
     *
     * @return 冷卻信息列表
     */
    public List<String> getAllPlayerCooldowns() {
        List<String> cooldownInfo = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // 清理過期的冷卻記錄
        playerKeyCooldowns.entrySet().removeIf(entry -> currentTime >= entry.getValue());

        for (Map.Entry<UUID, Long> entry : playerKeyCooldowns.entrySet()) {
            UUID playerId = entry.getKey();
            long remainingTime = (entry.getValue() - currentTime) / 1000L;

            Player player = Bukkit.getPlayer(playerId);
            String playerName = player != null ? player.getName() : playerId.toString();

            cooldownInfo.add("§e" + playerName + "§f: §c" + formatTime((int) remainingTime));
        }

        return cooldownInfo;
    }
    /**
     * 手動觸發冷卻完成通知（測試用）
     * @param player 玩家
     */
    public void testCooldownNotification(Player player) {
        notifyPlayerCooldownComplete(player);
        playCooldownCompleteSound(player);
        player.sendMessage("§e[測試] 冷卻完成通知已觸發");
    }
    /**
     * 清理所有過期的冷卻記錄（定時任務用）
     */
    /**
     * 清理所有過期的冷卻記錄（定時任務用）（修改版本）
     */
    public void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;

        Iterator<Map.Entry<UUID, Long>> iterator = playerKeyCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID playerId = entry.getKey();
            if (currentTime >= entry.getValue()) {
                iterator.remove();
                playersToNotify.remove(playerId); // 新增：同時清理通知記錄
                removedCount++;
            }
        }

        if (removedCount > 0) {
            plugin.getLogger().info("清理了 " + removedCount + " 個過期的鑰匙冷卻記錄");
        }
    }

    /**
     * 獲取鑰匙的音效配置
     *
     * @param baseId 副本基礎ID
     * @return 包含音效信息的 Map，如果沒有配置則返回預設值
     */
    public Map<String, Object> getKeySoundConfig(String baseId) {
        Map<String, Object> soundConfig = new HashMap<>();

        if (keyConfig.contains("keys." + baseId + ".use_sound")) {
            soundConfig.put("sound", keyConfig.getString("keys." + baseId + ".use_sound.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
            soundConfig.put("volume", keyConfig.getDouble("keys." + baseId + ".use_sound.volume", 1.0));
            soundConfig.put("pitch", keyConfig.getDouble("keys." + baseId + ".use_sound.pitch", 1.0));
        } else {
            // 預設音效
            soundConfig.put("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
            soundConfig.put("volume", 1.0);
            soundConfig.put("pitch", 1.0);
        }

        return soundConfig;
    }

    /**
     * 播放鑰匙使用音效
     *
     * @param player 玩家
     * @param baseId 副本基礎ID
     */
    public void playKeyUseSound(Player player, String baseId) {
        Map<String, Object> soundConfig = getKeySoundConfig(baseId);

        try {
            String soundName = (String) soundConfig.get("sound");
            double volume = (Double) soundConfig.get("volume");
            double pitch = (Double) soundConfig.get("pitch");

            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("無效的音效名稱: " + soundConfig.get("sound") + "，使用預設音效");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    /**
     * 建立副本基礎ID到實例ID的映射
     */
    private void buildBaseIdMapping() {
        baseToDungeonId.clear();

        // 從DungeonManager獲取instanceToDungeon映射
        Map<String, Dungeon> allDungeons = plugin.getDungeonManager().getAllDungeons();

        for (Map.Entry<String, Dungeon> entry : allDungeons.entrySet()) {
            String instanceId = entry.getKey();

            // 從實例ID中提取基礎ID
            String baseId = instanceId;
            if (instanceId.contains("_")) {
                String[] parts = instanceId.split("_");
                if (parts.length > 1) {
                    try {
                        // 嘗試解析最後一部分是否為數字
                        Integer.parseInt(parts[parts.length - 1]);
                        StringBuilder baseIdBuilder = new StringBuilder(parts[0]);
                        for (int i = 1; i < parts.length - 1; i++) {
                            baseIdBuilder.append("_").append(parts[i]);
                        }
                        baseId = baseIdBuilder.toString();
                    } catch (NumberFormatException e) {
                        // 不是數字，則整個ID就是基礎ID
                        baseId = instanceId;
                    }
                }
            }

            // 存儲映射 (如果已有該基礎ID的映射，則不覆蓋)
            if (!baseToDungeonId.containsKey(baseId)) {
                baseToDungeonId.put(baseId, instanceId);
                plugin.getLogger().info("映射副本基礎ID " + baseId + " 到實例 " + instanceId);
            }
        }
    }

    private void saveConfig() {
        try {
            keyConfig.save(keyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("無法保存 keys.yml: " + e.getMessage());
        }
    }

    /**
     * 根據基礎ID創建副本鑰匙
     */
    public ItemStack createKey(String baseId) {
        if (!keyConfig.contains("keys." + baseId)) {
            plugin.getLogger().warning("找不到副本 " + baseId + " 的鑰匙配置");
            return null;
        }

        String materialName = keyConfig.getString("keys." + baseId + ".material", "PAPER");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.PAPER;
            plugin.getLogger().warning("無效的材質名稱: " + materialName + "，使用默認 PAPER");
        }

        ItemStack keyItem = new ItemStack(material);
        ItemMeta meta = keyItem.getItemMeta();
        if (meta != null) {
            String name = keyConfig.getString("keys." + baseId + ".name", "§e" + baseId + " 副本入場卷");
            List<String> lore = keyConfig.getStringList("keys." + baseId + ".lore");

            // 检查副本类型是否发生变化，如果是，则确保lore中包含正确的信息
            boolean isWave = keyConfig.getBoolean("keys." + baseId + ".is_wave");

            // 獲取一個實例ID來獲取副本對象
            String instanceId = baseToDungeonId.get(baseId);
            if (instanceId != null) {
                Dungeon dungeon = plugin.getDungeonManager().getDungeon(instanceId);

                if (dungeon instanceof WaveDungeon && !lore.stream().anyMatch(line -> line.contains("波次"))) {
                    // 添加波次相关描述
                    WaveDungeon waveDungeon = (WaveDungeon) dungeon;
                    List<String> newLore = new ArrayList<>();
                    newLore.add("§7使用此卷進入 " + baseId + " 波次副本");
                    newLore.add("§b總波數: §f" + waveDungeon.getTotalWaves());
                    newLore.add("§7需要清理所有波次的怪物");
                    newLore.add("§7右鍵點擊使用");
                    lore = newLore;
                }
            }

            meta.setDisplayName(name);
            meta.setLore(lore);

            // 獲取並設置 custom_model_data
            int customModelData = keyConfig.getInt("keys." + baseId + ".custom_model_data", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            // 存儲副本基礎ID到物品中
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(dungeonKeyKey, PersistentDataType.STRING, baseId);

            // 存储副本类型
            pdc.set(dungeonTypeKey, PersistentDataType.STRING, isWave ? "wave" : "normal");

            keyItem.setItemMeta(meta);
        }

        return keyItem;
    }

    public void giveKey(Player player, String baseId, int amount) {
        // 檢查玩家是否有權限獲得這個鑰匙
        String permission = keyConfig.getString("permissions." + baseId);
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage("§c你沒有獲取此副本入場卷的權限");
            return;
        }

        ItemStack keyItem = createKey(baseId);
        if (keyItem == null) {
            player.sendMessage("§c找不到該副本的入場卷設定");
            return;
        }

        keyItem.setAmount(amount);
        player.getInventory().addItem(keyItem);
        player.sendMessage("§a你獲得了 §e" + amount + "個 " + keyItem.getItemMeta().getDisplayName());
    }

    public boolean isDungeonKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(dungeonKeyKey, PersistentDataType.STRING);
    }

    /**
     * 從鑰匙中獲取副本基礎ID
     */
    public String getDungeonIdFromKey(ItemStack item) {
        if (!isDungeonKey(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(dungeonKeyKey, PersistentDataType.STRING);
    }

    public String getDungeonTypeFromKey(ItemStack item) {
        if (!isDungeonKey(item)) {
            return "normal";
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(dungeonTypeKey, PersistentDataType.STRING)) {
            return pdc.get(dungeonTypeKey, PersistentDataType.STRING);
        }
        return "normal"; // 默认为普通副本
    }

    public boolean isWaveDungeonKey(ItemStack item) {
        return "wave".equals(getDungeonTypeFromKey(item));
    }

    /**
     * 檢查玩家是否有使用特定副本鑰匙的權限
     */
    public boolean hasKeyPermission(Player player, String baseId) {
        String permission = keyConfig.getString("permissions." + baseId);
        return permission == null || player.hasPermission(permission);
    }

    public NamespacedKey getDungeonKeyKey() {
        return dungeonKeyKey;
    }

    /**
     * 根據基礎ID獲取一個可用的實例ID
     */
    public String getInstanceIdFromBaseId(String baseId) {
        // 首先檢查這個ID是否是直接的基礎ID映射
        if (baseToDungeonId.containsKey(baseId)) {
            return baseToDungeonId.get(baseId);
        }

        // 如果不是，則嘗試作為實例ID直接使用
        if (plugin.getDungeonManager().getDungeon(baseId) != null) {
            return baseId;
        }

        // 最後，嘗試尋找該基礎ID的可用實例
        return plugin.getDungeonManager().findAvailableInstance(baseId);
    }
}