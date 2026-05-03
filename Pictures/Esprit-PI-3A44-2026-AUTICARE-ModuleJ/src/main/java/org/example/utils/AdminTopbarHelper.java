package org.example.utils;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.example.models.User;

/** Remplit avatar (photo {@code user.image} ou initiales) + nom + email de la barre admin. */
public final class AdminTopbarHelper {

    private static final double TOPBAR_AVATAR = 40;

    private AdminTopbarHelper() {
    }

    public static void applyToTopbar(StackPane avatarHost, Label userNameLabel, Label userEmailLabel) {
        if (avatarHost == null || userNameLabel == null || userEmailLabel == null) {
            return;
        }
        User me = AppState.getCurrentUser();
        if (me == null) {
            userNameLabel.setText("—");
            userEmailLabel.setText("—");
            avatarHost.getChildren().setAll(UserAvatarGraphic.build(null, TOPBAR_AVATAR, "?", null));
            return;
        }
        userNameLabel.setText(((me.getPrenom() != null ? me.getPrenom() : "") + " " + (me.getNom() != null ? me.getNom() : "")).trim());
        userEmailLabel.setText(me.getEmail() != null ? me.getEmail() : "");
        avatarHost.getChildren().setAll(
                UserAvatarGraphic.build(me, TOPBAR_AVATAR, UserAvatarGraphic.initialsFor(me), null));
    }
}
