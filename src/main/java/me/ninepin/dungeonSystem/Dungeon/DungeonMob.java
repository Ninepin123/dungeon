package me.ninepin.dungeonSystem.Dungeon;

import org.bukkit.Location;

public class DungeonMob {

    private final String id;
    private final Location location;

    public DungeonMob(String id, Location location) {
        this.id = id;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }
}