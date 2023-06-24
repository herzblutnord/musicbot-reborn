package moe.herz;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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

public class LastFmService {
    private final String apiKey;

    public LastFmService(Config config) {
        this.apiKey = config.getProperty("lfm.apiKey");
    }

    public String getCurrentTrack(String username) {
        String url = String.format("http://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user=%s&api_key=%s&format=json",
                username, apiKey);

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
                    int errorCode = jsonObject.get("error").getAsJsonObject().get("code").getAsInt();
                    String errorMessage = jsonObject.get("message").getAsString();
                    return "Error: " + errorCode + " - " + errorMessage;
                } else {
                    JsonObject recentTracks = jsonObject.getAsJsonObject("recenttracks");

                    if (recentTracks.has("track")) {
                        JsonElement trackElement = recentTracks.get("track");

                        if (trackElement.isJsonArray()) {
                            JsonArray trackArray = trackElement.getAsJsonArray();

                            if (trackArray.size() > 0) {
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
                                    topTags = getTopTags(artistName, album);
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
            e.printStackTrace();
            return "Error retrieving last.fm data";
        }
    }

    public String getTopTags(String artist, String album) {
        String artistEncoded = URLEncoder.encode(artist, StandardCharsets.UTF_8);
        String albumEncoded = URLEncoder.encode(album, StandardCharsets.UTF_8);

        String url = String.format("http://ws.audioscrobbler.com/2.0/?method=album.gettoptags&artist=%s&album=%s&api_key=%s&format=json",
                artistEncoded, albumEncoded, apiKey);
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

                    // Get the top two or three tags
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
            e.printStackTrace();
        }
        return "";
    }
}