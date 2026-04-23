package org.example.utils;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prépare des URLs Google Maps en mode <em>embed</em> pour {@link javafx.scene.web.WebView}.
 * <p>
 * Pour obtenir une carte « plein cadre » comme une iframe (capture 2 : pas de panneau latéral
 * recherche / « affichage limité » / connexion), il faut préférer une URL du type
 * {@code q=lat,lng&output=embed}. On extrait donc les coordonnées des liens « Partager »
 * classiques ({@code @lat,lng}, {@code !3d!4d}, etc.).
 * </p>
 */
public final class GoogleMapsEmbedUrls {

    /** Ex. {@code @50.715,-1.987} ou {@code @36,10} dans l’URL (partage / Street View). */
    private static final Pattern AT_LAT_LNG = Pattern.compile("@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    /** Ex. {@code &ll=50.7,-1.9} */
    private static final Pattern LL_PARAM = Pattern.compile("[?&]ll=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:&|$)");
    /** Ex. {@code ?q=36.8,10.2} (uniquement si les deux segments sont numériques). */
    private static final Pattern Q_COORD_PAIR = Pattern.compile("[?&]q=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:&|$)");
    /** Ex. {@code !3d36.8!4d10.2} (formats mobile / certains liens). */
    private static final Pattern D3_D4 = Pattern.compile("!3d(-?\\d+\\.?\\d*)!4d(-?\\d+\\.?\\d*)");
    /** Ex. {@code center=50.7,-1.9}. */
    private static final Pattern CENTER_PARAM = Pattern.compile("[?&]center=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");

    private GoogleMapsEmbedUrls() {
    }

    /**
     * URL de carte minimaliste (style iframe) : même rendu que « capture 2 » (carte + repère, peu de chrome).
     */
    public static String embedUrlForCoordinates(double lat, double lng, int zoom) {
        return String.format(Locale.US,
                "https://www.google.com/maps?q=%f,%f&z=%d&output=embed&hl=fr&maptype=roadmap",
                lat, lng, zoom);
    }

    /**
     * Tente d’extraire lat/lng d’un lien Google Maps (place, partage, etc.).
     */
    public static Optional<double[]> tryExtractLatLng(String googleMapsHttpUrl) {
        if (googleMapsHttpUrl == null || googleMapsHttpUrl.isBlank()) {
            return Optional.empty();
        }
        String u = googleMapsHttpUrl.trim();

        Matcher m1 = AT_LAT_LNG.matcher(u);
        if (m1.find()) {
            try {
                return Optional.of(new double[]{
                        Double.parseDouble(m1.group(1)),
                        Double.parseDouble(m1.group(2))
                });
            } catch (NumberFormatException ignored) {
                /* motif suivant */
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
                /* motif suivant */
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
                /* motif suivant */
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
                /* motif suivant */
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
                /* motif suivant */
            }
        }

        return Optional.empty();
    }

    /**
     * Ajoute {@code output=embed} lorsque c’est pertinent (repli si pas de coordonnées extractibles).
     */
    public static String forWebViewEmbed(String googleMapsHttpUrl) {
        if (googleMapsHttpUrl == null || googleMapsHttpUrl.isBlank()) {
            return null;
        }
        String u = googleMapsHttpUrl.trim();
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

    /**
     * Indique si l’URL doit être affichée via un document HTML contenant une {@code iframe}.
     * <p>
     * Les URLs {@code /maps/embed?pb=…} (API Embed) refusent la navigation « pleine page » dans un
     * WebView et affichent : « The Google Maps Embed API must be used in an iframe. »
     * </p>
     */
    public static boolean requiresIframeDocument(String httpUrl) {
        if (httpUrl == null || httpUrl.isBlank()) {
            return false;
        }
        String u = httpUrl.trim().toLowerCase();
        return u.contains("google.com/maps") || u.contains("maps.google.com");
    }

    /**
     * Document HTML minimal : carte en plein cadre (équivalent d’une iframe intégrée sur une page).
     */
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
