package moe.herz;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class LastFmService {
    private final String apiKey;
    private final Connection dbConnection;
    private static final Logger logger = LoggerFactory.getLogger(LastFmService.class);

    public LastFmService(Config config) {
        this.apiKey = config.getProperty("lfm.apiKey");
        this.dbConnection = config.getDbConnection();
    }

    public String getCurrentTrack(String username) {
        // Check if username is an IRC username or a Last.fm username
        String lastfmUsername = getLastFmUsernameFromDb(username);
        if (lastfmUsername == null) {
            lastfmUsername = username;
        }
        lastfmUsername = URLEncoder.encode(lastfmUsername, StandardCharsets.UTF_8);

        String url = String.format("https://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user=%s&api_key=%s&format=json",
                lastfmUsername, apiKey);

        try {
            URI uri = new URI(url);

            HttpRequest request = HttpRequest.newBuilder(uri).build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream inputStream = response.body();
                 Scanner scanner = new Scanner(inputStream)) {

                String json = scanner.useDelimiter("\\A").next();

                // Parse the JSON response using Gson
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

                if (jsonObject.has("error")) {
                    int errorCode = jsonObject.get("error").getAsInt();  // get the error code as an integer
                    String errorMessage = jsonObject.get("message").getAsString();
                    return "Error: " + errorCode + " - " + errorMessage;
                } else {
                    JsonObject recentTracks = jsonObject.getAsJsonObject("recenttracks");

                    if (recentTracks.has("track")) {
                        JsonElement trackElement = recentTracks.get("track");

                        if (trackElement.isJsonArray()) {
                            JsonArray trackArray = trackElement.getAsJsonArray();

                            if (!trackArray.isEmpty()) {
                                JsonObject trackObject = trackArray.get(0).getAsJsonObject();
                                boolean nowPlaying = trackObject.has("@attr") && trackObject.getAsJsonObject("@attr").has("nowplaying");

                                String trackName = trackObject.get("name").getAsString();
                                String artistName = trackObject.getAsJsonObject("artist").get("#text").getAsString();
                                // Inside the getCurrentTrack method, after retrieving the track and artist names

                                JsonElement albumElement = trackObject.get("album");
                                String album = null;
                                if (albumElement != null && !albumElement.isJsonNull()) {
                                    album = albumElement.getAsJsonObject().get("#text").getAsString();
                                }

                                String topTags = "";
                                if (album != null) {
                                    topTags = getTopTags(artistName);
                                }

                                if (nowPlaying) {
                                    String nowPlayingMessage = "Currently playing: " + trackName + " by " + artistName;
                                    if (album != null && !topTags.isEmpty()) {
                                        nowPlayingMessage += " | Tags: " + topTags;
                                    }
                                    return nowPlayingMessage;
                                } else {
                                    String lastPlayedMessage = "Last played track: " + trackName + " by " + artistName;
                                    if (album != null && !topTags.isEmpty()) {
                                        lastPlayedMessage += " | Tags: " + topTags;
                                    }
                                    return lastPlayedMessage;
                                }
                            }
                        }
                    }
                    return "No recent tracks found";
                }
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("An error occurred", e);
            return "Error retrieving last.fm data";
        }
    }

    public String getTopTags(String artist) {
        String artistEncoded = URLEncoder.encode(artist, StandardCharsets.UTF_8);

        // Using artist.gettoptags method in URL
        String url = String.format("https://ws.audioscrobbler.com/2.0/?method=artist.gettoptags&artist=%s&api_key=%s&format=json",
                artistEncoded, apiKey);
        try {
            URI uri = new URI(url);
            HttpRequest request = HttpRequest.newBuilder(uri).build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream inputStream = response.body();
                 Scanner scanner = new Scanner(inputStream)) {

                String json = scanner.useDelimiter("\\A").next();

                // Parse the JSON response using Gson
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

                if (jsonObject.has("toptags")) {
                    JsonObject topTags = jsonObject.getAsJsonObject("toptags");
                    JsonArray tagArray = topTags.getAsJsonArray("tag");

                    // Get the top three tags
                    int tagCount = Math.min(tagArray.size(), 3);
                    StringBuilder topTagsBuilder = new StringBuilder();
                    for (int i = 0; i < tagCount; i++) {
                        JsonObject tagObject = tagArray.get(i).getAsJsonObject();
                        String tagName = tagObject.get("name").getAsString();
                        topTagsBuilder.append(tagName);
                        if (i < tagCount - 1) {
                            topTagsBuilder.append(", ");
                        }
                    }
                    return topTagsBuilder.toString();
                }
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("An error occurred", e);
        }
        return "";
    }

    public void saveLastFmUsername(String ircUsername, String lastfmUsername) {
        try {
            String sql = "INSERT INTO lastfmnames (username, lastfm_username) VALUES (?, ?) ON CONFLICT (username) DO UPDATE SET lastfm_username = ?";
            PreparedStatement stmt = dbConnection.prepareStatement(sql);
            stmt.setString(1, ircUsername);
            stmt.setString(2, lastfmUsername);
            stmt.setString(3, lastfmUsername);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("An error occurred", e);
        }
    }

    public String getLastFmUsernameFromDb(String username) {
        try {
            String sql = "SELECT lastfm_username FROM lastfmnames WHERE username = ?";
            PreparedStatement stmt = dbConnection.prepareStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("lastfm_username");
            } else {
                return null;
            }
        } catch (SQLException e) {
            logger.error("An error occurred", e);
            return null;
        }
    }
}