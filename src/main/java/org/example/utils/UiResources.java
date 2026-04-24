package org.example.utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.example.MainApp;

/**
 * Ressources UI chargées depuis le classpath (évite les URLs relatives FXML {@code @...} qui
 * dépendent du chemin disque {@code file:/C:/.../target/classes/...} et provoquent des erreurs).
 */
public final class UiResources {

    private UiResources() {
    }

    /** Logo barre latérale admin : {@code /images/logo.png}. */
    public static void applySidebarLogo(ImageView view) {
        if (view == null) {
            return;
        }
        var url = MainApp.class.getResource("/images/logo.png");
        if (url == null) {
            return;
        }
        try {
            Image img = new Image(url.toExternalForm(), true);
            if (!img.isError()) {
                view.setImage(img);
            }
        } catch (Throwable ignored) {
            // ImageView reste vide
        }
    }
}
