package org.example.utils;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Charge les photos produit / vignettes : JavaFX d’abord, puis flux, puis {@link ImageIO}
 * (JPEG/PNG/GIF/BMP…) pour les cas où le décodage direct échoue (certains fichiers WebP, chemins, etc.).
 */
public final class ProductImageLoader {

    private ProductImageLoader() {
    }

    /**
     * @param pathOrUrl chemin relatif BDD, chemin absolu, ou URL http(s)
     * @param displayW    largeur d’affichage cible (la résolution chargée est bornée à ~×2)
     * @param displayH    hauteur d’affichage cible
     * @return image utilisable, ou {@code null}
     */
    public static Image loadForDisplay(String pathOrUrl, int displayW, int displayH) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return null;
        }
        String n = firstImagePath(pathOrUrl);
        if (n == null || n.isBlank()) {
            return null;
        }
        int rw = Math.min(1600, Math.max(1, displayW * 2));
        int rh = Math.min(1600, Math.max(1, displayH * 2));
        if (n.toLowerCase().startsWith("data:image/")) {
            try {
                Image img = new Image(n, rw, rh, true, true, true);
                return img.isError() ? null : img;
            } catch (Exception ignored) {
                return null;
            }
        }
        if (n.startsWith("http://") || n.startsWith("https://")) {
            Image img = new Image(n, rw, rh, true, true, true);
            return img.isError() ? null : img;
        }
        Path file = resolveLocalFile(n);
        if (file == null) {
            return null;
        }
        return loadLocalFile(file, rw, rh);
    }

    private static Path resolveLocalFile(String n) {
        Path file = UserPublicAssets.findPublicRelativeFile(n);
        if (file != null) {
            return file;
        }
        Path abs = Path.of(n);
        if (abs.isAbsolute() && Files.isRegularFile(abs)) {
            return abs;
        }
        return null;
    }

    private static Image loadLocalFile(Path file, int rw, int rh) {
        String uri = file.toUri().toString();
        try {
            Image img = new Image(uri, rw, rh, true, true, false);
            if (!img.isError()) {
                return img;
            }
        } catch (Exception ignored) {
            // —
        }
        try (InputStream in = Files.newInputStream(file)) {
            Image img = new Image(in);
            if (!img.isError()) {
                return img;
            }
        } catch (IOException ignored) {
            // —
        }
        try {
            BufferedImage bi = ImageIO.read(file.toFile());
            if (bi != null) {
                BufferedImage scaled = scaleIfNeeded(bi, rw, rh);
                return SwingFXUtils.toFXImage(scaled, null);
            }
        } catch (Exception ignored) {
            // —
        }
        return null;
    }

    private static BufferedImage scaleIfNeeded(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxW && h <= maxH) {
            return src;
        }
        double scale = Math.min((double) maxW / w, (double) maxH / h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        int type = src.getTransparency() == BufferedImage.OPAQUE
            ? BufferedImage.TYPE_INT_RGB
            : BufferedImage.TYPE_INT_ARGB;
        BufferedImage out = new BufferedImage(nw, nh, type);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Accepte un champ multi-images (séparateurs ; , \n |) et renvoie la première image. */
    private static String firstImagePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.toLowerCase().startsWith("data:image/")) {
            return trimmed;
        }
        String[] parts = trimmed.split("[;|\\n]");
        for (String p : parts) {
            if (p != null) {
                String x = p.trim();
                if (!x.isBlank()) {
                    return x;
                }
            }
        }
        return trimmed;
    }
}
