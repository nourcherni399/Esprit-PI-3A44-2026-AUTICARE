package org.example.utils;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prépare des URLs de carte en mode <em>embed</em> pour {@link javafx.scene.web.WebView}.
 */
public final class MapEmbedUrls {

    private static final Pattern AT_LAT_LNG = Pattern.compile("@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LL_PARAM = Pattern.compile("[?&]ll=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:&|$)");
    private static final Pattern Q_COORD_PAIR = Pattern.compile("[?&]q=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:&|$)");
    private static final Pattern D3_D4 = Pattern.compile("!3d(-?\\d+\\.?\\d*)!4d(-?\\d+\\.?\\d*)");
    private static final Pattern CENTER_PARAM = Pattern.compile("[?&]center=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");

    private MapEmbedUrls() {
    }

    public static String embedUrlForCoordinates(double lat, double lng, int zoom) {
        return String.format(Locale.US,
                "https://www.google.com/maps?q=%f,%f&z=%d&output=embed&hl=fr&maptype=roadmap",
                lat, lng, zoom);
    }

    public static String openStreetMapEmbedUrlForCoordinates(double lat, double lng) {
        double d = 0.0045;
        double minLon = lng - d;
        double maxLon = lng + d;
        double minLat = lat - d;
        double maxLat = lat + d;
        return String.format(Locale.US,
                "https://www.openstreetmap.org/export/embed.html?bbox=%f,%f,%f,%f&layer=mapnik&marker=%f,%f",
                minLon, minLat, maxLon, maxLat, lat, lng);
    }

    public static String openStreetMapPageUrlForCoordinates(double lat, double lng, int zoom) {
        int z = Math.max(3, Math.min(19, zoom));
        return String.format(Locale.US,
                "https://www.openstreetmap.org/?mlat=%f&mlon=%f#map=%d/%f/%f",
                lat, lng, z, lat, lng);
    }

    public static String openStreetMapStaticTileUrl(double lat, double lng, int zoom) {
        double latRad = Math.toRadians(lat);
        int n = 1 << zoom;
        int x = (int) Math.floor((lng + 180.0) / 360.0 * n);
        int y = (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);
        x = Math.max(0, Math.min(n - 1, x));
        y = Math.max(0, Math.min(n - 1, y));
        return String.format(Locale.US, "https://tile.openstreetmap.org/%d/%d/%d.png", zoom, x, y);
    }

    public static Optional<double[]> tryExtractLatLng(String mapHttpUrl) {
        if (mapHttpUrl == null || mapHttpUrl.isBlank()) {
            return Optional.empty();
        }
        String u = mapHttpUrl.trim();

        Matcher m1 = AT_LAT_LNG.matcher(u);
        if (m1.find()) {
            try {
                return Optional.of(new double[]{
                        Double.parseDouble(m1.group(1)),
                        Double.parseDouble(m1.group(2))
                });
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher mLl = LL_PARAM.matcher(u);
        if (mLl.find()) {
            try {
                return Optional.of(new double[]{
                        Double.parseDouble(mLl.group(1)),
                        Double.parseDouble(mLl.group(2))
                });
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher mQ = Q_COORD_PAIR.matcher(u);
        if (mQ.find()) {
            try {
                return Optional.of(new double[]{
                        Double.parseDouble(mQ.group(1)),
                        Double.parseDouble(mQ.group(2))
                });
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher m2 = D3_D4.matcher(u);
        if (m2.find()) {
            try {
                return Optional.of(new double[]{
                        Double.parseDouble(m2.group(1)),
                        Double.parseDouble(m2.group(2))
                });
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher m3 = CENTER_PARAM.matcher(u);
        if (m3.find()) {
            try {
                return Optional.of(new double[]{
                        Double.parseDouble(m3.group(1)),
                        Double.parseDouble(m3.group(2))
                });
            } catch (NumberFormatException ignored) {
            }
        }

        return Optional.empty();
    }

    public static String forWebViewEmbed(String mapHttpUrl) {
        if (mapHttpUrl == null || mapHttpUrl.isBlank()) {
            return null;
        }
        String u = mapHttpUrl.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return u;
        }
        String lower = u.toLowerCase();
        if (!lower.contains("google") && !lower.contains("goo.gl") && !lower.contains("maps.app")) {
            return u;
        }
        if (lower.contains("goo.gl") || lower.contains("maps.app.goo.gl")) {
            return u;
        }
        if (u.contains("output=embed") || u.contains("/maps/embed")) {
            return u;
        }
        int hash = u.indexOf('#');
        String base = hash >= 0 ? u.substring(0, hash) : u;
        String frag = hash >= 0 ? u.substring(hash) : "";
        String join = base.contains("?") ? "&" : "?";
        if (!base.contains("output=")) {
            base = base + join + "output=embed&hl=fr";
        }
        return base + frag;
    }

    public static boolean requiresIframeDocument(String httpUrl) {
        if (httpUrl == null || httpUrl.isBlank()) {
            return false;
        }
        String u = httpUrl.trim().toLowerCase();
        return u.contains("google.com/maps")
                || u.contains("maps.google.com");
    }

    public static String htmlDocumentWithMapIframe(String mapHttpUrl) {
        String src = escapeHtmlAttr(mapHttpUrl == null ? "" : mapHttpUrl.trim());
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<style>html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden}"
                + "iframe{border:0;width:100%;height:100%;display:block}</style>"
                + "</head><body>"
                + "<iframe src=\"" + src + "\" "
                + "allowfullscreen "
                + "referrerpolicy=\"no-referrer-when-downgrade\"></iframe>"
                + "</body></html>";
    }

    private static String escapeHtmlAttr(String s) {
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
