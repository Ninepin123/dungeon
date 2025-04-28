package me.ninepin.dungeonSystem;

import me.ninepin.dungeonSystem.Dungeon.*;
import me.ninepin.dungeonSystem.key.KeyManager;
import me.ninepin.dungeonSystem.party.PartyCommand;
import me.ninepin.dungeonSystem.party.PartyManager;
import me.ninepin.dungeonSystem.revive.ReviveItemManager;
import me.ninepin.dungeonSystem.revive.ReviveListener;
import org.bukkit.plugin.java.JavaPlugin;

public class DungeonSystem extends JavaPlugin {

    private DungeonManager dungeonManager;
    private KeyManager keyManager;
    private ReviveItemManager reviveItemManager;
    private PartyManager partyManager;
    private WaveDungeonManager waveDungeonManager;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 初始化各个管理器
        partyManager = new PartyManager(this);
        dungeonManager = new DungeonManager(this);
        keyManager = new KeyManager(this);
        reviveItemManager = new ReviveItemManager(this);

        // 获取WaveDungeonManager实例
        waveDungeonManager = dungeonManager.getWaveDungeonManager();

        // 注册命令
        getCommand("dungeon").setExecutor(new DungeonCommand(this));
        getCommand("party").setExecutor(new PartyCommand(this));
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new DungeonListener(this), this);
        getServer().getPluginManager().registerEvents(new DungeonMobListener(this), this);
        getServer().getPluginManager().registerEvents(new DungeonInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ReviveListener(this), this);

        // 注册波次副本事件监听器
        getServer().getPluginManager().registerEvents(new WaveDungeonListener(this, waveDungeonManager), this);

        // 初始化副本状态检查任务
        dungeonManager.initDungeonCheckTask();

        getLogger().info("DungeonSystem enabled successfully!");
    }



    @Override
    public void onDisable() {
        getLogger().info("DungeonSystem has been disabled!");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        // 如果有其他配置文件需要重新加載，可以在這裡處理
    }

    /**
     * 重新加載所有配置
     */
    public void reloadAllConfigs() {
        reloadConfig();
        if (keyManager != null) {
            keyManager.reload();
        }
        if (reviveItemManager != null) {
            reviveItemManager.reload();
        }
        if (dungeonManager != null) {
            dungeonManager.reloadDungeons();
        }
    }

    // 添加 PartyManager 的 getter 方法
    public PartyManager getPartyManager() {
        return partyManager;
    }

    /**
     * 獲取副本管理器實例
     *
     * @return DungeonManager實例
     */
    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    /**
     * 獲取副本鑰匙管理器實例
     *
     * @return KeyManager實例
     */
    public KeyManager getKeyManager() {
        return keyManager;
    }

    /**
     * 獲取復活物品管理器
     */
    public ReviveItemManager getReviveItemManager() {
        return reviveItemManager;
    }
    public WaveDungeonManager getWaveDungeonManager() {
        return waveDungeonManager;
    }
}
