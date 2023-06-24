package moe.herz;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

public class UrlMetadataFetcher {

    public static String fetchWebsiteMetadata(String url) {
        try {
            URI uri = new URI(url); // Check that it's a valid URL
            try {
                // Send an HTTP GET request
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                // Parse the HTML content
                Document doc = Jsoup.parse(connection.getInputStream(), null, url);
                return doc.title();
            } catch (IOException e) {
                // If the GET request fails, fall back to HtmlUnit
                try (final WebClient webClient = new WebClient()) {
                    webClient.getOptions().setJavaScriptEnabled(false);  // Disable JavaScript
                    final HtmlPage page = webClient.getPage(url);
                    return page.getTitleText();
                }
            }
        } catch (URISyntaxException e) {
            return "Invalid URL";
        } catch (Exception e) {
            return "Error connecting to URL";
        }
    }
}
