package thunder.hack.core.manager.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.compress.utils.Lists;
import thunder.hack.core.manager.IManager;
import thunder.hack.features.modules.client.ClientSettings;
import thunder.hack.utility.Timer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class TelemetryManager implements IManager {
    private final Timer updateTimer = new Timer();
    private int onlinePlayers = 0;
    private int maxOnline = 0;
    private List<String> changelog = new ArrayList<>();
    private List<String> onlinePlayersList = new ArrayList<>();
    private JsonObject stats = new JsonObject();

    public void init() {
        updateData();
    }

    public void updateData() {
        if (!updateTimer.passedMs(30000)) // Обновляем каждые 30 секунд
            return;

        updateTimer.reset();

        // Обновляем онлайн и статистику
        try {
            URL url = new URL("https://apicheat.c0rex86.ru/online");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String line = reader.readLine();
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            onlinePlayers = json.get("online").getAsInt();
            maxOnline = json.get("max_online").getAsInt();
            stats = json.get("stats").getAsJsonObject();
            reader.close();
        } catch (Exception e) {
            onlinePlayers = 0;
            maxOnline = 0;
        }

        // Обновляем список онлайн игроков
        try {
            URL url = new URL("https://apicheat.c0rex86.ru/users/online");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String line = reader.readLine();
            JsonArray json = JsonParser.parseString(line).getAsJsonArray();
            onlinePlayersList.clear();
            for (int i = 0; i < json.size(); i++) {
                onlinePlayersList.add(json.get(i).getAsString());
            }
            reader.close();
        } catch (Exception e) {
            onlinePlayersList.clear();
        }

        // Обновляем changelog
        try {
            URL url = new URL("https://apicheat.c0rex86.ru/changelog");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String line = reader.readLine();
            JsonArray json = JsonParser.parseString(line).getAsJsonArray();
            changelog.clear();
            for (int i = 0; i < json.size(); i++) {
                JsonObject commit = json.get(i).getAsJsonObject();
                String message = commit.get("message").getAsString();
                String date = commit.get("date").getAsString();
                String author = commit.get("author").getAsString();
                String color = commit.get("color").getAsString();
                changelog.add(message + " §7(" + date + " by " + author + ")");
            }
            reader.close();
        } catch (Exception e) {
            changelog.clear();
            changelog.add("[*] No changelog available");
        }
    }

    public int getOnlineCount() {
        updateData();
        return onlinePlayers;
    }

    public List<String> getOnlinePlayers() {
        updateData();
        return onlinePlayersList;
    }

    public int getMaxOnline() {
        return maxOnline;
    }

    public List<String> getChangelog() {
        updateData();
        return changelog;
    }

    public JsonObject getStats() {
        updateData();
        return stats;
    }

    public void onUpdate() {
        if (updateTimer.every(90000))
            fetchData();
    }

    public void fetchData() {
        if (ClientSettings.telemetry.getValue())
            pingServer(mc.getSession().getUsername());
        onlinePlayers = getPlayers(true);
        maxOnline = getPlayers(false);
    }

    public void pingServer(String name) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://apicheat.c0rex86.ru/online?name=" + name))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Throwable ignored) {
        }
    }

    public static int getPlayers(boolean online) {
        final HttpRequest request = HttpRequest.newBuilder(URI.create("https://apicheat.c0rex86.ru/" + (online ? "users/online" : "users")))
                .GET()
                .build();
        int count = 0;

        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
            count = array.size();
        } catch (Throwable ignored) {
        }
        return count;
    }
}
