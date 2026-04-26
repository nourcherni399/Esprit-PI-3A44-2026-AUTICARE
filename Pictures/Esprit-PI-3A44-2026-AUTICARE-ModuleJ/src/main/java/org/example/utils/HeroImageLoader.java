package org.example.utils;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Charge une image distante avec des en-têtes HTTP corrects (sinon beaucoup de CDN refusent JavaFX {@link Image#Image(String)}).
 */
public final class HeroImageLoader {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private HeroImageLoader() {
    }

    /**
     * Charge une image depuis une URL https ou un fichier {@code file:} (image de thématique enregistrée en local).
     */
    public static Image loadImage(String source) {
        if (source == null || source.isBlank()) {
            return softPlaceholder();
        }
        String s = source.trim();
        if (s.startsWith("https://") || s.startsWith("http://")) {
            return loadImageFromUrl(s);
        }
        if (s.startsWith("file:")) {
            try {
                Image img = new Image(s, false);
                if (img.isError()) {
                    return softPlaceholder();
                }
                return img;
            } catch (Exception e) {
                return softPlaceholder();
            }
        }
        return softPlaceholder();
    }

    /**
     * Télécharge l’image ou renvoie un dégradé local si échec.
     */
    public static Image loadImageFromUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return softPlaceholder();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(urlString.trim()))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .GET()
                    .timeout(Duration.ofSeconds(25))
                    .build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return softPlaceholder();
            }
            byte[] data = resp.body();
            if (data == null || data.length == 0) {
                return softPlaceholder();
            }
            try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
                Image img = new Image(in);
                if (img.isError()) {
                    return softPlaceholder();
                }
                return img;
            }
        } catch (Exception e) {
            return softPlaceholder();
        }
    }

    /** Dégradé doux (affichage immédiat ou secours si l’URL échoue). */
    public static Image softPlaceholder() {
        int w = 960;
        int h = 260;
        WritableImage wi = new WritableImage(w, h);
        PixelWriter pw = wi.getPixelWriter();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double t = (x + y) / (double) (w + h);
                pw.setColor(x, y, Color.color(0.72 + t * 0.12, 0.88 + t * 0.06, 0.98, 1.0));
            }
        }
        return wi;
    }
}
