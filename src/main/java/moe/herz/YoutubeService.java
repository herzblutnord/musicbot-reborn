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
import java.text.DecimalFormat;

import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Channel;

public class YoutubeService {
    private YouTube youtube;
    private final String apiKey;
    private static final Logger logger = LoggerFactory.getLogger(YoutubeService.class);

    public YoutubeService(Config config) {
        this.apiKey = config.getytapiKey();
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

    public String getPlaylistDetails(String playlistId) {
        try {
            YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet,contentDetails"));
            request.setKey(apiKey);
            request.setId(Collections.singletonList(playlistId));

            PlaylistListResponse response = request.execute();
            List<Playlist> playlists = response.getItems();

            if (!playlists.isEmpty()) {
                Playlist playlist = playlists.get(0);
                String title = playlist.getSnippet().getTitle();
                Long itemCount = playlist.getContentDetails().getItemCount();

                return String.format("YouTube Playlist | %s | Number of Videos: %d", title, itemCount);
            }
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
        return null;
    }

    public String getChannelDetails(String channelId) {
        try {
            YouTube.Channels.List request = youtube.channels().list(Collections.singletonList("snippet,statistics"));
            request.setKey(apiKey);
            request.setId(Collections.singletonList(channelId));

            ChannelListResponse response = request.execute();
            List<Channel> channels = response.getItems();

            if (!channels.isEmpty()) {
                Channel channel = channels.get(0);
                String title = channel.getSnippet().getTitle();
                BigInteger subscriberCount = channel.getStatistics().getSubscriberCount();

                // Convert subscriberCount to a human-readable format
                String humanReadableSubCount = toHumanReadableFormat(subscriberCount);

                return String.format("%s | Number of Followers: %s", title, humanReadableSubCount);
            }
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
        return null;
    }

    // This method converts a BigInteger to a human-readable string
    public String toHumanReadableFormat(BigInteger number) {
        String[] suffix = {"", "K", "M", "B", "T"};
        int i;
        double num = number.doubleValue();
        for (i = 0; i < suffix.length; i++) {
            if (num < 1000) break;
            num /= 1000;
        }
        DecimalFormat df = new DecimalFormat("#.#");
        return df.format(num) + suffix[i];
    }

    public String getChannelIdFromUsernameUsingSearch(String username) {
        try {
            // Create a search.list() request
            YouTube.Search.List searchRequest = youtube.search().list(Collections.singletonList("snippet"));

            // Include the "@" symbol in the search query
            searchRequest.setQ("@" + username);

            searchRequest.setType(Collections.singletonList("channel"));
            searchRequest.setKey(apiKey);

            // Execute the request and get the response
            SearchListResponse searchResponse = searchRequest.execute();
            List<SearchResult> searchResultList = searchResponse.getItems();

            // If no channels are found for the given username, return null
            if (searchResultList == null || searchResultList.isEmpty()) {
                return null;
            }

            // Since you've found that the first result is reliably the channel you're looking for,
            // you can simply return its channel ID
            return searchResultList.get(0).getSnippet().getChannelId();

        } catch (Exception e) {
            logger.error("An error occurred while retrieving the channel ID", e);
            return null;
        }
    }

}
