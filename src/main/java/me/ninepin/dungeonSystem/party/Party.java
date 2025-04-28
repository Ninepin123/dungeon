package me.ninepin.dungeonSystem.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Party {
    private UUID id;
    private UUID ownerId;
    private Map<UUID, String> members; // UUID -> Player Name
    private int maxSize;

    public Party(Player owner) {
        this.id = UUID.randomUUID();
        this.ownerId = owner.getUniqueId();
        this.members = new HashMap<>();
        this.members.put(owner.getUniqueId(), owner.getName());
        this.maxSize = 4; // Default party size
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public boolean isOwner(UUID playerId) {
        return ownerId.equals(playerId);
    }

    public Set<UUID> getMemberUUIDs() {
        return members.keySet();
    }

    public Map<UUID, String> getMembers() {
        return members;
    }

    public int getSize() {
        return members.size();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    // Party management methods
    // 改為這個方法
    public boolean addMember(UUID playerId) {
        if (members.size() >= maxSize) {
            return false;
        }

        // 嘗試獲取玩家名稱
        String playerName = "Unknown";
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            playerName = player.getName();
        }

        members.put(playerId, playerName);
        return true;
    }

    // 並添加這個方法以保持向後兼容
    public boolean addMember(Player player) {
        return addMember(player.getUniqueId());
    }

    public boolean removeMember(UUID playerId) {
        if (isOwner(playerId)) {
            return false; // Can't remove owner
        }
        return members.remove(playerId) != null;
    }

    public boolean transferOwnership(UUID newOwnerId) {
        if (!members.containsKey(newOwnerId)) {
            return false;
        }
        this.ownerId = newOwnerId;
        return true;
    }
}