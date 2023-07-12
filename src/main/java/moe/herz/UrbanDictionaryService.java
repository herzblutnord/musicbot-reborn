package moe.herz;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UrbanDictionaryService {

    private final String apiKey;

    public UrbanDictionaryService(Config config) {
        this.apiKey = config.getProperty("ud.apiKey");
    }

    public List<String> searchUrbanDictionary(String term) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://mashape-community-urban-dictionary.p.rapidapi.com/define?term=" + term)
                .get()
                .addHeader("x-rapidapi-host", "mashape-community-urban-dictionary.p.rapidapi.com")
                .addHeader("x-rapidapi-key", apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                String jsonData = response.body().string();
                JsonElement jsonElement = JsonParser.parseString(jsonData);
                if (jsonElement.getAsJsonObject().get("list").getAsJsonArray().size() > 0) {
                    String definition = jsonElement.getAsJsonObject().get("list").getAsJsonArray().get(0).getAsJsonObject().get("definition").getAsString();
                    return splitMessage(definition, 400);
                } else {
                    return Collections.singletonList("No definition found for " + term);
                }
            } else {
                return Collections.singletonList("Error: Response body is null");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.singletonList("Error connecting to Urban Dictionary API.");
        }
    }

    private List<String> splitMessage(String message, int maxLength) {
        List<String> result = new ArrayList<>();
        String[] lines = message.split("\n");

        for (String line : lines) {
            int index = 0;
            while (index < line.length()) {
                int endIndex = Math.min(index + maxLength, line.length());
                result.add(line.substring(index, endIndex));
                index = endIndex;
                if (result.size() >= 5) {
                    return result;
                }
            }
        }
        return result;
    }
}
