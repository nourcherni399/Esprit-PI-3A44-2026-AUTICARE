package org.example.utils;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.example.models.User;

import java.nio.file.Path;

/**
 * Avatar liste / fiche : image depuis {@code user.image} + racine {@code public}, sinon initiales.
 */
public final class UserAvatarGraphic {

    private UserAvatarGraphic() {
    }

    public static String initialsFor(User u) {
        if (u == null) {
            return "?";
        }
        String di = "";
        if (u.getPrenom() != null && !u.getPrenom().isEmpty()) {
            di += u.getPrenom().substring(0, 1).toUpperCase();
        }
        if (u.getNom() != null && !u.getNom().isEmpty()) {
            di += u.getNom().substring(0, 1).toUpperCase();
        }
        return di.isEmpty() ? "?" : di;
    }

    /**
     * @param labelStyleClass classe CSS pour le fallback initiales ; {@code null} → {@code admin-avatar}
     */
    public static StackPane build(User u, double size, String initials, String labelStyleClass) {
        StackPane stack = new StackPane();
        stack.setMinSize(size, size);
        stack.setMaxSize(size, size);
        stack.setPrefSize(size, size);

        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(size);
        clip.setArcHeight(size);
        stack.setClip(clip);

        ImageView iv = new ImageView();
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);

        boolean shown = false;
        if (u != null && u.getImage() != null && !u.getImage().isBlank()) {
            Path file = UserPublicAssets.resolvePublicRelative(u.getImage());
            if (file != null && java.nio.file.Files.isRegularFile(file)) {
                try {
                    String uri = file.toUri().toString();
                    Image img = new Image(uri, size, size, true, true, true);
                    if (!img.isError()) {
                        iv.setImage(img);
                        stack.getChildren().add(iv);
                        shown = true;
                    }
                } catch (Exception ignored) {
                    // fallback initiales
                }
            }
        }

        if (!shown) {
            javafx.scene.control.Label lab = new javafx.scene.control.Label(initials != null ? initials : "?");
            String css = labelStyleClass != null && !labelStyleClass.isBlank() ? labelStyleClass : "admin-avatar";
            lab.getStyleClass().add(css);
            lab.setMinSize(size, size);
            lab.setMaxSize(size, size);
            lab.setAlignment(javafx.geometry.Pos.CENTER);
            stack.getChildren().add(lab);
        }

        return stack;
    }
}
