package org.example.utils;

import com.github.sarxos.webcam.Webcam;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class FaceCameraCapture {

    private FaceCameraCapture() {
    }

    public static Optional<File> capture(Window owner) throws FaceCameraException {
        Webcam webcam;
        try {
            webcam = Webcam.getDefault();
        } catch (Exception e) {
            throw new FaceCameraException("CAMERA_UNAVAILABLE", "Caméra indisponible.");
        }
        if (webcam == null) {
            throw new FaceCameraException("CAMERA_UNAVAILABLE", "Aucune caméra détectée.");
        }
        try {
            webcam.open(true);
        } catch (Exception e) {
            throw new FaceCameraException("CAMERA_UNAVAILABLE", "Impossible d'accéder à la caméra.", e);
        }

        AtomicReference<File> out = new AtomicReference<>(null);
        AtomicReference<BufferedImage> lastFrame = new AtomicReference<>(null);
        AtomicReference<BufferedImage> frozenFrame = new AtomicReference<>(null);
        AtomicReference<Boolean> accepted = new AtomicReference<>(false);

        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Capture Face ID");

        Label welcome = new Label("Welcome to AutiCare");
        welcome.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #0f3d53;");
        Label subtitle = new Label("Connexion Face ID sécurisée");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #5b6b7a;");
        Label hint = new Label("Positionnez votre visage puis cliquez sur Capturer.");
        hint.setStyle("-fx-font-size: 13px; -fx-text-fill: #203040;");
        ImageView preview = new ImageView();
        preview.setFitWidth(520);
        preview.setFitHeight(340);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        preview.setStyle("-fx-effect: dropshadow(gaussian, rgba(15,61,83,0.22), 16, 0.25, 0, 3);");

        Label status = new Label("Caméra active");
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: #2f5670;");

        Button captureBtn = new Button("Capturer");
        Button retakeBtn = new Button("Reprendre");
        Button validateBtn = new Button("Valider");
        Button cancelBtn = new Button("Annuler");
        retakeBtn.setDisable(true);
        validateBtn.setDisable(true);
        captureBtn.setStyle("-fx-background-color: #0f6fa5; -fx-text-fill: white; -fx-background-radius: 10; -fx-font-weight: 700;");
        retakeBtn.setStyle("-fx-background-color: #f2f7fb; -fx-text-fill: #0f3d53; -fx-background-radius: 10; -fx-font-weight: 700;");
        validateBtn.setStyle("-fx-background-color: #1d8f5f; -fx-text-fill: white; -fx-background-radius: 10; -fx-font-weight: 700;");
        cancelBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #485a68; -fx-border-color: #cfd9df; -fx-border-radius: 10; -fx-background-radius: 10; -fx-font-weight: 700;");
        captureBtn.setMinWidth(95);
        retakeBtn.setMinWidth(95);
        validateBtn.setMinWidth(95);
        cancelBtn.setMinWidth(95);

        Timeline loop = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            if (!webcam.isOpen()) {
                return;
            }
            if (frozenFrame.get() != null) {
                return;
            }
            BufferedImage frame = webcam.getImage();
            if (frame == null) {
                return;
            }
            lastFrame.set(frame);
            preview.setImage(SwingFXUtils.toFXImage(frame, null));
        }));
        loop.setCycleCount(Timeline.INDEFINITE);
        loop.play();

        captureBtn.setOnAction(e -> {
            BufferedImage frame = lastFrame.get();
            if (frame == null) {
                status.setText("Aucune image capturée.");
                return;
            }
            frozenFrame.set(frame);
            preview.setImage(SwingFXUtils.toFXImage(frame, null));
            status.setText("Image capturée. Validez ou reprenez.");
            captureBtn.setDisable(true);
            retakeBtn.setDisable(false);
            validateBtn.setDisable(false);
        });

        retakeBtn.setOnAction(e -> {
            frozenFrame.set(null);
            status.setText("Caméra active");
            captureBtn.setDisable(false);
            retakeBtn.setDisable(true);
            validateBtn.setDisable(true);
        });

        validateBtn.setOnAction(e -> {
            BufferedImage frame = frozenFrame.get();
            if (frame == null) {
                status.setText("Capturez une image avant validation.");
                return;
            }
            try {
                File tmp = File.createTempFile("faceid-capture-", ".jpg");
                tmp.deleteOnExit();
                ImageIO.write(frame, "jpg", tmp);
                out.set(tmp);
                accepted.set(true);
                stage.close();
            } catch (Exception ex) {
                status.setText("Impossible d'enregistrer la capture.");
            }
        });

        cancelBtn.setOnAction(e -> stage.close());
        stage.setOnCloseRequest(e -> {
            loop.stop();
            if (webcam.isOpen()) {
                webcam.close();
            }
        });
        stage.setOnHiding(e -> {
            loop.stop();
            if (webcam.isOpen()) {
                webcam.close();
            }
        });

        HBox actions = new HBox(10, captureBtn, retakeBtn, validateBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox header = new VBox(4, welcome, subtitle);
        VBox center = new VBox(10, header, hint, preview, status);
        center.setPadding(new Insets(12));
        center.setStyle("-fx-background-color: linear-gradient(to bottom, #f3fbff 0%, #ffffff 55%);");

        BorderPane root = new BorderPane();
        root.setCenter(center);
        root.setBottom(actions);
        BorderPane.setMargin(actions, new Insets(0, 12, 12, 12));
        root.setStyle("-fx-background-color: #ffffff;");
        root.setTop(new Region());

        stage.setScene(new Scene(root, 560, 460));
        stage.showAndWait();

        if (!accepted.get() || out.get() == null) {
            return Optional.empty();
        }
        return Optional.of(out.get());
    }

    public static final class FaceCameraException extends Exception {
        private final String code;

        public FaceCameraException(String code, String message) {
            super(message);
            this.code = code;
        }

        public FaceCameraException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
