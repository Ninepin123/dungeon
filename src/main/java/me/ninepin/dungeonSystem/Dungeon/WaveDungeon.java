package me.ninepin.dungeonSystem.Dungeon;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WaveDungeon extends Dungeon {

    private final int totalWaves;
    private final Map<Integer, List<DungeonMob>> waveMobs; // 每一波的怪物列表
    private int currentWave;
    private boolean inProgress;

    public WaveDungeon(String id, int levelRequired, int maxPlayers, Location spawnPoint,
                       Location deathWaitingArea, List<DungeonMob> initialMobs,
                       String targetMobId, int totalWaves, Map<Integer, List<DungeonMob>> waveMobs) {
        // 调用父类构造函数
        super(id, levelRequired, maxPlayers, spawnPoint, deathWaitingArea, initialMobs, targetMobId);
        this.totalWaves = totalWaves;
        this.waveMobs = waveMobs;
        this.currentWave = 0;
        this.inProgress = false;
    }

    /**
     * 获取副本类型
     *
     * @return 副本类型(" normal " 或 " wave ")
     */
    @Override
    public String getType() {
        return "wave";
    }

    /**
     * 获取总波数
     *
     * @return 总波数
     */
    public int getTotalWaves() {
        return totalWaves;
    }

    /**
     * 获取当前波数
     *
     * @return 当前波数
     */
    public int getCurrentWave() {
        return currentWave;
    }

    /**
     * 设置当前波数
     *
     * @param wave 要设置的波数
     */
    public void setCurrentWave(int wave) {
        this.currentWave = wave;
    }

    /**
     * 进入下一波
     *
     * @return 下一波的波数，如果已是最后一波则返回-1
     */
    public int nextWave() {
        if (currentWave < totalWaves) {
            currentWave++;
            return currentWave;
        }
        return -1; // 表示已经是最后一波
    }
    /**
     * 添加怪物到指定波次
     *
     * @param wave 波次
     * @param mob 怪物對象
     */
    public void addMobToWave(int wave, DungeonMob mob) {
        List<DungeonMob> waveList = waveMobs.get(wave);
        if (waveList == null) {
            waveList = new ArrayList<>();
            waveMobs.put(wave, waveList);
        }
        waveList.add(mob);
    }

    /**
     * 获取指定波数的怪物列表
     *
     * @param wave 波数
     * @return 怪物列表
     */
    public List<DungeonMob> getWaveMobs(int wave) {
        return waveMobs.get(wave);
    }

    /**
     * 检查副本是否正在进行中
     *
     * @return 是否进行中
     */
    public boolean isInProgress() {
        return inProgress;
    }

    /**
     * 设置副本进行状态
     *
     * @param inProgress 是否进行中
     */
    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }
}
