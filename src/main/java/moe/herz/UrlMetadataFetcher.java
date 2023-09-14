package moe.herz;

import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.List;

public class UrlMetadataFetcher {
    private static final Logger logger = LoggerFactory.getLogger(UrlMetadataFetcher.class);
    private static final List<String> ALLOWED_SCHEMES = Arrays.asList("http", "https");

    public static boolean isAllowedIP(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            byte[] bytes = address.getAddress();

            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            int b2 = bytes[2] & 0xFF;
            int b3 = bytes[3] & 0xFF;

            if (address.isLoopbackAddress() || "localhost".equalsIgnoreCase(host)) return false;

            if (b0 == 10 ||
                    (b0 == 172 && b1 >= 16 && b1 <= 31) ||
                    (b0 == 192 && b1 == 168) ||
                    (b0 == 0) ||
                    (b0 == 100 && b1 >= 64 && b1 <= 127) ||
                    (b0 == 169 && b1 == 254) ||
                    (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) ||
                    (b0 == 198 && (b1 == 18 || b1 == 19)) ||
                    (b0 == 198 && b1 == 51 && b2 == 100) ||
                    (b0 == 203 && b1 == 0 && b2 == 113) ||
                    (b0 >= 224) ||
                    (b0 == 240) ||
                    (b0 == 255 && b1 == 255 && b2 == 255 && b3 == 255)) {
                return false;
            }

            return true;

        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isValidScheme(URI uri) {
        return ALLOWED_SCHEMES.contains(uri.getScheme());
    }

    public static String fetchWebsiteMetadata(String url) {
        try {
            URI uri = new URI(url);

            if (!isAllowedIP(uri.getHost()) || !isValidScheme(uri)) {
                return "Not a allowed URL. What are you trying to do here?";
            }

            try {
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);  // 5 seconds
                connection.setReadTimeout(5000);  // 5 seconds
                connection.setInstanceFollowRedirects(false);  // Disable redirects
                connection.connect();

                int statusCode = connection.getResponseCode();
                if (statusCode != 200) {
                    throw new IOException("Non-OK HTTP status");
                }

                String contentType = connection.getHeaderField("Content-Type");
                if (contentType == null || !contentType.startsWith("text/html")) {
                    throw new IOException("Invalid content type");
                }

                Document doc = Jsoup.parse(connection.getInputStream(), null, url);
                return doc.title();

            } catch (IOException e) {
                logger.error("An error occurred while fetching via HttpURLConnection. Trying HtmlUnit fallback...", e);

                try (final WebClient webClient = new WebClient()) {
                    webClient.getOptions().setJavaScriptEnabled(false);  // Disable JavaScript
                    final HtmlPage page = webClient.getPage(url);
                    return page.getTitleText();
                } catch (Exception ex) {
                    logger.error("An error occurred while fetching via HtmlUnit", ex);
                    return null;  // This will not return an error to the IRC users.
                }
            }

        } catch (URISyntaxException e) {
            logger.error("An error occurred", e);
            return "Invalid URL";
        } catch (Exception e) {
            logger.error("An error occurred", e);
            return null;  // This will not return an error to the IRC users.
        }
    }
}