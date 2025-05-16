package me.ninepin.dungeonSystem.Dungeon;

import org.bukkit.Location;

public class DungeonMob {

    private final String id;
    private final Location location;
    private final int amount;      // 新增：生成數量
    private final double radius;   // 新增：生成半徑

    // 原始構造函數（向後兼容）
    public DungeonMob(String id, Location location) {
        this(id, location, 1, 0.0);
    }

    // 新的完整構造函數
    public DungeonMob(String id, Location location, int amount, double radius) {
        this.id = id;
        this.location = location;
        this.amount = amount;
        this.radius = radius;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public int getAmount() {
        return amount;
    }

    public double getRadius() {
        return radius;
    }
}