package moe.herz;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import org.apache.commons.text.StringEscapeUtils;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.List;
import java.util.Collections;

public class YoutubeService {
    private YouTube youtube;
    private final String apiKey;
    private static final Logger logger = LoggerFactory.getLogger(YoutubeService.class);

    public YoutubeService(Config config) {
        this.apiKey = config.getProperty("yt.apiKey");
        try {
            youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(), null)
                    .setApplicationName("musicbot2")
                    .build();
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
    }

    public String searchYoutube(String query) {
        try {
            YouTube.Search.List search = youtube.search().list(Collections.singletonList("id,snippet"));
            search.setKey(apiKey);
            search.setQ(query);
            search.setType(Collections.singletonList("video"));
            search.setFields("items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url, snippet/channelTitle)");
            search.setMaxResults(1L);

            SearchListResponse searchResponse = search.execute();
            List<SearchResult> searchResultList = searchResponse.getItems();

            if (!searchResultList.isEmpty()) {
                SearchResult video = searchResultList.get(0);
                String videoId = video.getId().getVideoId();

                // Get video statistics
                return getVideoDetails(videoId);
            }
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
        return null;
    }

    public String getVideoDetails(String videoId) {
        try {
            YouTube.Videos.List request = youtube.videos().list(Collections.singletonList("snippet,statistics"));
            request.setKey(apiKey);
            request.setId(Collections.singletonList(videoId));

            VideoListResponse response = request.execute();
            List<Video> videos = response.getItems();

            if (!videos.isEmpty()) {
                Video video = videos.get(0);
                String title = video.getSnippet().getTitle();
                String channel = video.getSnippet().getChannelTitle();
                BigInteger views = video.getStatistics().getViewCount();

                // Format views count
                NumberFormat formatter = NumberFormat.getInstance();
                String formattedViews = formatter.format(views);

                // Format the title and channel
                title = StringEscapeUtils.unescapeHtml4(title);
                channel = StringEscapeUtils.unescapeHtml4(channel);

                return //"https://www.youtube.com/watch?v=" + videoId
                        String.format("%s | Channel: %s | Views: %s | ", title, channel, formattedViews) + "https://www.youtube.com/watch?v=" + videoId;
            }
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
        return null;
    }
}
