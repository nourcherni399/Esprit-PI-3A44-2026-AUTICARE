package org.example.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.models.Role;
import org.example.models.User;
import org.example.services.MedecinRatingService;
import org.example.services.UserService;
import org.example.utils.AppState;
import org.example.MainApp;
import org.example.utils.PublicRdvDoctorSidebarHelper;
import org.example.utils.RdvTarifFormat;
import org.example.utils.UserAvatarGraphic;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Liste des médecins ayant un compte actif (table {@code user}) pour le parcours « Prendre RDV ».
 */
public class PageRdvController implements PublicShellAware {

    private static final String ALL_SPECIALTIES = "Toutes les spécialités";

    private PublicShellController shell;

    @FXML
    private VBox rdvDoctorsList;
    @FXML
    private Label rdvCountNumLabel;
    @FXML
    private ComboBox<String> rdvSpecialtyFilter;
    @FXML
    private TextField rdvLieuFilter;

    private final UserService userService = new UserService();
    private final MedecinRatingService ratingService = new MedecinRatingService();
    private List<User> medecinsCompteActif = new ArrayList<>();

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @FXML
    private void initialize() {
        loadMedecins();
        fillSpecialtyFilter();
        if (rdvSpecialtyFilter != null) {
            rdvSpecialtyFilter.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshDoctorCards());
        }
        if (rdvLieuFilter != null) {
            rdvLieuFilter.textProperty().addListener((o, a, b) -> refreshDoctorCards());
        }
        refreshDoctorCards();
    }

    private void loadMedecins() {
        medecinsCompteActif = new ArrayList<>();
        try {
            medecinsCompteActif = userService.findByRole(Role.MEDECIN).stream()
                    .filter(User::isActif)
                    .sorted(Comparator
                            .comparing((User u) -> blankToEmpty(u.getNom()), String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(u -> blankToEmpty(u.getPrenom()), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        } catch (SQLException ignored) {
            // liste vide
        }
    }

    private void fillSpecialtyFilter() {
        if (rdvSpecialtyFilter == null) {
            return;
        }
        TreeSet<String> specs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (User u : medecinsCompteActif) {
            String s = u.getSpecialite();
            if (s != null && !s.isBlank()) {
                specs.add(s.trim());
            }
        }
        List<String> items = new ArrayList<>();
        items.add(ALL_SPECIALTIES);
        items.addAll(specs);
        rdvSpecialtyFilter.getItems().setAll(items);
        rdvSpecialtyFilter.getSelectionModel().selectFirst();
    }

    private void refreshDoctorCards() {
        if (rdvDoctorsList == null) {
            return;
        }
        rdvDoctorsList.getChildren().clear();
        List<User> filtered = medecinsCompteActif.stream()
                .filter(this::matchesFilters)
                .collect(Collectors.toList());
        if (rdvCountNumLabel != null) {
            rdvCountNumLabel.setText(String.valueOf(filtered.size()));
        }
        if (filtered.isEmpty()) {
            Label empty = new Label(medecinsCompteActif.isEmpty()
                    ? "Aucun professionnel avec compte actif pour le moment. Les médecins inscrits sur AutiCare apparaîtront ici."
                    : "Aucun professionnel ne correspond à ces filtres.");
            empty.setWrapText(true);
            empty.getStyleClass().add("rdv-tip-text");
            rdvDoctorsList.getChildren().add(empty);
            return;
        }
        boolean alt = false;
        for (User u : filtered) {
            rdvDoctorsList.getChildren().add(buildDoctorCard(u, alt));
            alt = !alt;
        }
    }

    private boolean matchesFilters(User u) {
        String selectedSpec = rdvSpecialtyFilter != null ? rdvSpecialtyFilter.getSelectionModel().getSelectedItem() : null;
        if (selectedSpec != null && !selectedSpec.isBlank() && !ALL_SPECIALTIES.equals(selectedSpec)) {
            String us = u.getSpecialite() != null ? u.getSpecialite().trim() : "";
            if (!us.equalsIgnoreCase(selectedSpec.trim())) {
                return false;
            }
        }
        String lieu = rdvLieuFilter != null && rdvLieuFilter.getText() != null
                ? rdvLieuFilter.getText().trim().toLowerCase(Locale.ROOT)
                : "";
        if (!lieu.isEmpty()) {
            String cab = u.getCabinet() != null ? u.getCabinet().toLowerCase(Locale.ROOT) : "";
            String adr = u.getAdresse() != null ? u.getAdresse().toLowerCase(Locale.ROOT) : "";
            if (!cab.contains(lieu) && !adr.contains(lieu)) {
                return false;
            }
        }
        return true;
    }

    private VBox buildDoctorCard(User u, boolean altAvatar) {
        VBox card = new VBox(16);
        card.getStyleClass().addAll("rdv-card", "rdv-card-v2");

        String cab = u.getCabinet() != null && !u.getCabinet().isBlank() ? u.getCabinet().trim() : "";
        String adr = u.getAdresse() != null && !u.getAdresse().isBlank() ? u.getAdresse().trim() : "";
        String mail = u.getEmail() != null && !u.getEmail().isBlank() ? u.getEmail().trim() : "";
        String tel = u.getTelephone() != null && !u.getTelephone().isBlank() ? u.getTelephone().trim() : "";

        String spec = u.getSpecialite() != null && !u.getSpecialite().isBlank()
                ? u.getSpecialite().trim()
                : "Professionnel";

        User cur = AppState.getCurrentUser();
        MedecinRatingService.AvgRating avg = safeAverageRating(u.getId());
        Optional<MedecinRatingService.PatientReview> myReview = Optional.empty();
        if (cur != null) {
            myReview = safeFindReview(u.getId(), cur);
        }
        AtomicInteger starPick = new AtomicInteger(0);
        myReview.ifPresent(r -> starPick.set(r.stars()));

        HBox header = new HBox(14);
        header.setAlignment(Pos.TOP_LEFT);

        StackPane av = UserAvatarGraphic.build(u, 58, UserAvatarGraphic.initialsFor(u), "rdv-av-txt");
        av.getStyleClass().add("rdv-av");
        if (altAvatar) {
            av.getStyleClass().add("rdv-av-alt");
        }

        VBox nameCol = new VBox(6);
        nameCol.setAlignment(Pos.TOP_LEFT);

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(PublicRdvDoctorSidebarHelper.formatDrName(u));
        nameLbl.getStyleClass().add("rdv-name");

        Label badge = new Label(spec);
        badge.getStyleClass().addAll("rdv-badge", "rdv-badge-purple");

        HBox starsRow = (cur != null && cur.getId() != u.getId())
                ? buildInteractiveStarsRow(starPick)
                : buildReadOnlyStarsRow(avg);
        titleRow.getChildren().addAll(nameLbl, badge, starsRow);

        Label specSub = new Label(buildSpecialiteSubline(spec));
        specSub.getStyleClass().add("rdv-card-spec-sub");

        Label tarifSub = new Label("Tarif consultation : " + RdvTarifFormat.format(u.getTarifConsultation()));
        tarifSub.getStyleClass().add("rdv-card-tarif-highlight");

        nameCol.getChildren().addAll(titleRow, specSub, tarifSub);
        if (avg.count() > 0) {
            Label avgTxt = new Label(avg.labelFr());
            avgTxt.getStyleClass().add("rdv-card-rating-avg-tiny");
            nameCol.getChildren().add(avgTxt);
        } else {
            Label noAvg = new Label("Pas encore d'avis");
            noAvg.getStyleClass().add("rdv-card-rating-hint");
            nameCol.getChildren().add(noAvg);
        }

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button rdvBtn = new Button("Prendre RDV");
        rdvBtn.getStyleClass().add("rdv-btn");
        rdvBtn.setOnAction(e -> startBooking(u));

        header.getChildren().addAll(av, nameCol, headerSpacer, rdvBtn);

        Label bio = new Label(buildRdvCardBio(cab));
        bio.setWrapText(true);
        bio.getStyleClass().add("rdv-card-bio");

        VBox ratingSection = buildReviewBlock(u, starPick, myReview);

        Label infoHeading = new Label("Coordonnées & lieu");
        infoHeading.getStyleClass().add("rdv-mod-section-title");

        VBox addrCell = rdvModernContactCell("📍", "Adresse", formatLocationHint(adr, cab));
        addrCell.getStyleClass().add("rdv-mod-cell-address");

        HBox telMailRow = new HBox(0);
        telMailRow.setMaxWidth(Double.MAX_VALUE);
        telMailRow.getStyleClass().add("rdv-mod-split-row");

        VBox telCell = rdvModernContactCell("📞", "Téléphone", tel);
        telCell.getStyleClass().add("rdv-mod-cell-half-left");
        Region telMailSep = new Region();
        telMailSep.getStyleClass().add("rdv-mod-vsep");
        VBox mailCell = rdvModernContactCell("✉", "E-mail", mail);
        mailCell.getStyleClass().add("rdv-mod-cell-half-right");
        HBox.setHgrow(telCell, Priority.ALWAYS);
        HBox.setHgrow(mailCell, Priority.ALWAYS);
        telMailRow.getChildren().addAll(telCell, telMailSep, mailCell);

        VBox infoPanel = new VBox(0);
        infoPanel.getStyleClass().add("rdv-card-info-modern");
        infoPanel.getChildren().addAll(infoHeading, addrCell, telMailRow);

        if (!cab.isBlank()) {
            VBox cabCell = rdvModernContactCell("🏥", "Cabinet", cab);
            cabCell.getStyleClass().add("rdv-mod-cabinet-cell");
            infoPanel.getChildren().add(cabCell);
        }

        card.getChildren().addAll(header, bio, ratingSection, infoPanel);
        return card;
    }

    private VBox buildReviewBlock(User medecin, AtomicInteger starSelection, Optional<MedecinRatingService.PatientReview> myReview) {
        VBox box = new VBox(10);
        box.getStyleClass().add("rdv-card-rating-section");

        User cur = AppState.getCurrentUser();
        if (cur == null) {
            Label hint = new Label("Connectez-vous pour noter ce praticien (étoiles ci-dessus) et rédiger un commentaire.");
            hint.setWrapText(true);
            hint.getStyleClass().add("rdv-card-rating-hint");
            box.getChildren().add(hint);
            return box;
        }
        if (cur.getId() == medecin.getId()) {
            return box;
        }

        VBox card = new VBox(12);
        card.getStyleClass().add("rdv-review-card");

        Label cardTitle = new Label("Votre avis");
        cardTitle.getStyleClass().add("rdv-review-card-title");

        Label cardSub = new Label("Une note via les étoiles dans l’en-tête, puis un commentaire optionnel.");
        cardSub.setWrapText(true);
        cardSub.getStyleClass().add("rdv-review-card-sub");

        TextArea commentArea = new TextArea();
        commentArea.setPromptText("Décrivez votre expérience : accueil, consultation, suivi…");
        commentArea.setWrapText(true);
        commentArea.setPrefRowCount(4);
        commentArea.setMinHeight(96);
        commentArea.getStyleClass().add("rdv-review-textarea");

        final int maxChars = 500;
        Label counter = new Label("0 / " + maxChars);
        counter.getStyleClass().add("rdv-review-counter");
        commentArea.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.length() > maxChars) {
                commentArea.setText(newV.substring(0, maxChars));
                return;
            }
            int n = newV == null ? 0 : newV.length();
            counter.setText(n + " / " + maxChars);
        });
        counter.setText((commentArea.getText() != null ? commentArea.getText().length() : 0) + " / " + maxChars);

        Button publish = new Button("Publier mon avis");
        publish.getStyleClass().addAll("rdv-review-submit", "rdv-review-btn-primary");
        publish.setMaxWidth(Region.USE_PREF_SIZE);

        Button seeAllReviews = new Button("Voir tous les avis");
        seeAllReviews.getStyleClass().addAll("rdv-review-submit", "rdv-review-btn-secondary");
        seeAllReviews.setMaxWidth(Region.USE_PREF_SIZE);
        seeAllReviews.setOnAction(ev -> showAllReviewsDialog(medecin));

        VBox reviewActions = new VBox(6);
        reviewActions.setAlignment(Pos.CENTER);
        reviewActions.getStyleClass().add("rdv-review-actions");
        reviewActions.getChildren().addAll(publish, seeAllReviews);

        int medecinId = medecin.getId();
        int patientId = cur.getId();
        publish.setOnAction(ev -> {
            int s = starSelection.get();
            if (s < 1 || s > 5) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setTitle("Avis");
                a.setHeaderText(null);
                a.setContentText("Cliquez sur les étoiles à côté du nom pour choisir une note de 1 à 5.");
                a.showAndWait();
                return;
            }
            try {
                String raw = commentArea.getText() != null ? commentArea.getText().trim() : "";
                ratingService.upsertReview(medecinId, patientId, s, raw.isEmpty() ? null : raw);
                refreshDoctorCards();
            } catch (SQLException ex) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setTitle("Avis");
                a.setHeaderText(null);
                a.setContentText(ex.getMessage() != null ? ex.getMessage() : "Impossible d'enregistrer votre avis.");
                a.showAndWait();
            }
        });

        card.getChildren().addAll(cardTitle, cardSub, commentArea, counter, reviewActions);
        box.getChildren().add(card);
        return box;
    }

    private void showAllReviewsDialog(User medecin) {
        if (medecin == null || medecin.getId() <= 0) {
            return;
        }
        List<MedecinRatingService.PublicReview> reviews;
        try {
            reviews = ratingService.listReviewsForMedecin(medecin.getId());
        } catch (SQLException ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Avis");
            err.setHeaderText(null);
            err.setContentText(ex.getMessage() != null ? ex.getMessage() : "Impossible de charger les avis.");
            err.showAndWait();
            return;
        }

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Avis");
        dialog.setHeaderText("Avis pour " + PublicRdvDoctorSidebarHelper.formatDrName(medecin));

        DialogPane pane = dialog.getDialogPane();
        pane.setPrefWidth(620);
        pane.setPrefHeight(520);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10, 4, 2, 4));

        if (reviews.isEmpty()) {
            Label empty = new Label("Aucun commentaire disponible pour ce médecin.");
            empty.setWrapText(true);
            empty.getStyleClass().add("rdv-card-rating-hint");
            content.getChildren().add(empty);
        } else {
            for (MedecinRatingService.PublicReview review : reviews) {
                VBox reviewCard = new VBox(6);
                reviewCard.setPadding(new Insets(10));
                reviewCard.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10; "
                        + "-fx-border-color: #e6e8ef; -fx-border-radius: 10;");

                String headerTxt = starsToText(review.stars()) + "  " + review.auteurAffiche() + "  •  " + review.dateAffiche();
                Label header = new Label(headerTxt);
                header.setStyle("-fx-font-weight: 700; -fx-text-fill: #2f3a52;");
                header.setWrapText(true);

                String comment = review.commentaire() != null && !review.commentaire().isBlank()
                        ? review.commentaire().trim()
                        : "(Aucun commentaire texte)";
                Label body = new Label(comment);
                body.setWrapText(true);
                body.setStyle("-fx-text-fill: #3d4a63;");

                reviewCard.getChildren().addAll(header, body);
                content.getChildren().add(reviewCard);
            }
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(430);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private static String starsToText(int stars) {
        int s = Math.max(0, Math.min(5, stars));
        return "★".repeat(s) + "☆".repeat(5 - s);
    }

    private static HBox buildReadOnlyStarsRow(MedecinRatingService.AvgRating avg) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rdv-stars-row");
        int filled = 0;
        if (avg.count() > 0) {
            filled = (int) Math.round(avg.average());
            filled = Math.min(5, Math.max(0, filled));
        }
        for (int i = 0; i < 5; i++) {
            Label star = new Label(i < filled ? "★" : "☆");
            star.getStyleClass().add(i < filled ? "rdv-star-readonly-on" : "rdv-star-readonly-off");
            row.getChildren().add(star);
        }
        return row;
    }

    private static HBox buildInteractiveStarsRow(AtomicInteger starPick) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rdv-stars-row");
        Label[] labs = new Label[5];
        for (int i = 0; i < 5; i++) {
            final int value = i + 1;
            Label L = new Label("☆");
            L.setCursor(Cursor.HAND);
            L.getStyleClass().add("rdv-star-btn");
            labs[i] = L;
            L.setOnMouseClicked(e -> {
                starPick.set(value);
                applyInteractiveStarStyles(labs, starPick.get());
            });
            L.setOnMouseEntered(e -> applyInteractiveStarStyles(labs, value));
            L.setOnMouseExited(e -> applyInteractiveStarStyles(labs, starPick.get()));
            row.getChildren().add(L);
        }
        applyInteractiveStarStyles(labs, starPick.get());
        return row;
    }

    private static void applyInteractiveStarStyles(Label[] labels, int lit) {
        int n = Math.max(0, Math.min(5, lit));
        for (int i = 0; i < 5; i++) {
            boolean on = n > 0 && i < n;
            labels[i].setText(on ? "★" : "☆");
            labels[i].getStyleClass().removeAll("rdv-star-on", "rdv-star-off");
            labels[i].getStyleClass().add(on ? "rdv-star-on" : "rdv-star-off");
        }
    }

    private static String buildSpecialiteSubline(String spec) {
        String s = spec != null && !spec.isBlank() ? spec.trim() : "—";
        return "Spécialité : " + s + ".";
    }

    private MedecinRatingService.AvgRating safeAverageRating(int medecinId) {
        try {
            return ratingService.averageForMedecin(medecinId);
        } catch (SQLException e) {
            return new MedecinRatingService.AvgRating(0, 0);
        }
    }

    private Optional<MedecinRatingService.PatientReview> safeFindReview(int medecinId, User patient) {
        try {
            return ratingService.findPatientReview(medecinId, patient.getId());
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /** Bloc coordonnées type fiche moderne (sans pastille carrée). */
    private static VBox rdvModernContactCell(String emoji, String fieldKey, String value) {
        VBox cell = new VBox(6);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.getStyleClass().add("rdv-mod-cell");

        HBox cap = new HBox(8);
        cap.setAlignment(Pos.CENTER_LEFT);
        Label ic = new Label(emoji);
        ic.getStyleClass().add("rdv-mod-emoji");
        Label kl = new Label(fieldKey.toUpperCase(Locale.FRENCH));
        kl.getStyleClass().add("rdv-mod-key");
        cap.getChildren().addAll(ic, kl);

        String v = value != null && !value.isBlank() ? value.trim() : "—";
        Label vl = new Label(v);
        vl.setWrapText(true);
        vl.getStyleClass().add("rdv-mod-value");

        cell.getChildren().addAll(cap, vl);
        return cell;
    }

    private static String buildRdvCardBio(String cabinetName) {
        String base = "Praticien accompagnant les personnes avec TSA.";
        if (cabinetName != null && !cabinetName.isBlank()) {
            return base + " Cabinet : " + cabinetName.trim() + ".";
        }
        return base;
    }

    private static String formatLocationHint(String adresse, String cabinetName) {
        if (adresse != null && !adresse.isBlank()) {
            String[] parts = adresse.split("[,;\n]");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last.length() > 64 ? last.substring(0, 61) + "…" : last;
            }
        }
        if (cabinetName != null && !cabinetName.isBlank()) {
            return cabinetName.trim();
        }
        return "—";
    }

    private static String blankToEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * Invité : remplace la liste des professionnels par une petite carte (plus de modale plein écran).
     */
    private void showLoginRequiredInline(User u) {
        if (rdvDoctorsList == null) {
            return;
        }
        rdvDoctorsList.getChildren().clear();

        String drName = u != null ? PublicRdvDoctorSidebarHelper.formatDrName(u) : "ce professionnel";
        String subText = "ce professionnel".equals(drName)
                ? "Connectez-vous pour finaliser votre rendez-vous avec " + drName + "."
                : "Connectez-vous pour finaliser votre rendez-vous avec le " + drName + ".";

        StackPane logoMini = new StackPane();
        logoMini.setMinSize(40, 40);
        logoMini.setPrefSize(40, 40);
        logoMini.setMaxSize(40, 40);
        logoMini.getStyleClass().add("rdv-inline-login-logo");
        Label letter = new Label("A");
        letter.getStyleClass().add("rdv-inline-login-logo-letter");
        logoMini.getChildren().add(letter);

        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Vous devez vous connecter");
        title.getStyleClass().add("rdv-inline-login-title");
        Label sparkle = new Label("✦");
        sparkle.getStyleClass().add("rdv-inline-login-sparkle");
        titleRow.getChildren().addAll(title, sparkle);

        Label sub = new Label(subText);
        sub.setWrapText(true);
        sub.getStyleClass().add("rdv-inline-login-sub");

        VBox textCol = new VBox(8);
        textCol.getChildren().addAll(titleRow, sub);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        HBox topRow = new HBox(14);
        topRow.setAlignment(Pos.TOP_LEFT);
        topRow.getChildren().addAll(logoMini, textCol);

        Button btnLogin = new Button("Se connecter");
        btnLogin.getStyleClass().add("rdv-inline-login-btn-primary");
        btnLogin.setOnAction(e -> {
            try {
                if (shell != null) {
                    shell.loadPage("login");
                } else {
                    MainApp.showLogin();
                }
            } catch (IOException ex) {
                showNavError(ex);
            }
        });

        Button btnSignup = new Button("Créer un compte");
        btnSignup.getStyleClass().add("rdv-inline-login-btn-secondary");
        btnSignup.setOnAction(e -> {
            try {
                if (shell != null) {
                    shell.loadPage("signup");
                } else {
                    MainApp.showPublicPage("signup");
                }
            } catch (IOException ex) {
                showNavError(ex);
            }
        });

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        actions.setMaxWidth(Double.MAX_VALUE);
        actions.getStyleClass().add("rdv-inline-login-actions");
        actions.getChildren().addAll(btnLogin, btnSignup);

        Hyperlink back = new Hyperlink("← Afficher les professionnels");
        back.getStyleClass().add("rdv-inline-login-back");
        back.setOnAction(e -> refreshDoctorCards());

        VBox card = new VBox(14);
        card.setMaxWidth(420);
        card.setPadding(new Insets(20, 22, 22, 22));
        card.getStyleClass().add("rdv-inline-login-card");
        card.getChildren().addAll(topRow, actions, back);

        StackPane holder = new StackPane();
        holder.setPadding(new Insets(4, 8, 20, 8));
        holder.setMinHeight(260);
        holder.getStyleClass().add("rdv-inline-login-holder");
        holder.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        rdvDoctorsList.getChildren().add(holder);
    }

    private void showNavError(IOException ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Navigation");
        a.setHeaderText(null);
        a.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        a.showAndWait();
    }

    private void startBooking(User u) {
        if (AppState.getCurrentUser() == null) {
            showLoginRequiredInline(u);
            return;
        }
        if (u == null || u.getId() <= 0) {
            return;
        }
        String displayName = PublicRdvDoctorSidebarHelper.formatDrName(u);
        AppState.beginPublicRdvBooking(u.getId(), displayName);
        if (shell == null) {
            return;
        }
        try {
            shell.loadPage("rdv-booking");
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Navigation");
            a.setHeaderText(null);
            a.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            a.showAndWait();
        }
    }

    private static int resolveMedecinId(String nameKey) {
        String key = nameKey.toLowerCase(Locale.ROOT);
        try {
            UserService us = new UserService();
            for (User u : us.findByRole(Role.MEDECIN)) {
                String nom = u.getNom() != null ? u.getNom().toLowerCase(Locale.ROOT) : "";
                String prenom = u.getPrenom() != null ? u.getPrenom().toLowerCase(Locale.ROOT) : "";
                if (nom.contains(key) || prenom.contains(key) || (nom + " " + prenom).contains(key)) {
                    return u.getId();
                }
            }
        } catch (SQLException ignored) {
            // fallback ci-dessous
        }
        return -1;
    }
}
