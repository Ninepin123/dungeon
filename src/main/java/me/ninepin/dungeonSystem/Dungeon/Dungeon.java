package me.ninepin.dungeonSystem.Dungeon;

import org.bukkit.Location;

import java.util.List;

public class Dungeon {

    private final String id;
    private final int levelRequired;
    private final int maxPlayers;
    private final Location spawnPoint;
    private final List<DungeonMob> mobs;
    private final Location deathWaitingArea;
    private final String targetMobId;

    public Dungeon(String id, int levelRequired, int maxPlayers, Location spawnPoint, Location deathWaitingArea, List<DungeonMob> mobs, String targetMobId) {
        this.id = id;
        this.levelRequired = levelRequired;
        this.maxPlayers = maxPlayers;
        this.spawnPoint = spawnPoint;
        this.deathWaitingArea = deathWaitingArea;
        this.mobs = mobs;
        this.targetMobId = targetMobId;
    }

    /**
     * 获取副本类型
     * @return 副本类型 "normal"
     */
    public String getType() {
        return "normal";
    }

    public Location getDeathWaitingArea() {
        return deathWaitingArea;
    }

    public String getId() {
        return id;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    public String getTargetMobId() {
        return targetMobId;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public Location getSpawnPoint() {
        return spawnPoint;
    }

    public List<DungeonMob> getMobs() {
        return mobs;
    }
}