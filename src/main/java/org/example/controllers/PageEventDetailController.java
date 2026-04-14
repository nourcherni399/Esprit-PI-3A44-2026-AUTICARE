package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import org.example.utils.GoogleMapsEmbedUrls;
import org.example.utils.HeroImageLoader;
import org.example.utils.ThematiqueHeroImages;
import org.example.utils.UserPublicAssets;

import java.util.concurrent.CompletableFuture;
import org.example.models.Event;
import org.example.models.EventMessage;
import org.example.models.Role;
import org.example.models.Thematique;
import org.example.models.EventRegistration;
import org.example.models.RegistrationStatus;
import org.example.models.User;
import org.example.services.AdminNotificationService;
import org.example.services.EventMessageService;
import org.example.services.EventRegistrationService;
import org.example.services.EventService;
import org.example.services.ThematiqueService;
import org.example.utils.AppState;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.text.Normalizer;

/**
 * Fiche publique d’un événement : carte, actions d’inscription, redirection connexion / inscription.
 */
public class PageEventDetailController implements PublicShellAware {

    /** Largeur du bandeau thématique = fraction de la colonne (photo moins large que le texte en dessous). */
    private static final double HERO_SHELL_WIDTH_RATIO = 0.72;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter MSG_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    private PublicShellController shell;
    private final EventService eventService = new EventService();
    private final EventRegistrationService registrationService = new EventRegistrationService();
    private final EventMessageService eventMessageService = new EventMessageService();
    private final AdminNotificationService adminNotificationService = new AdminNotificationService();
    private final ThematiqueService thematiqueService = new ThematiqueService();
    private int eventId = -1;
    private Event loaded;
    private boolean heroChromeInstalled;
    private boolean heroFitBound;
    private boolean heroShellWidthBound;

    @FXML
    private StackPane thematiqueHeroShell;
    @FXML
    private ImageView thematiqueHeroImage;
    @FXML
    private Label thematiquePill;
    @FXML
    private Label titleLabel;
    @FXML
    private Label dateLabel;
    @FXML
    private Label timeLabel;
    @FXML
    private Label lieuMetaLabel;
    @FXML
    private Label descriptionLabel;
    @FXML
    private Label addressLabel;
    @FXML
    private Label sidebarLieuLabel;
    @FXML
    private Label weatherMockDateHero;
    @FXML
    private Label weatherMockDateBottom;
    @FXML
    private StackPane mapPlaceholder;
    @FXML
    private javafx.scene.layout.VBox guestAuthBox;
    @FXML
    private javafx.scene.layout.VBox loggedParticipantSidebarBox;
    @FXML
    private javafx.scene.layout.VBox loggedStaffSidebarBox;
    @FXML
    private javafx.scene.layout.VBox loggedBox;
    @FXML
    private VBox discussionThreadBox;
    @FXML
    private TextArea organizerMessageArea;
    @FXML
    private Label registerHint;
    @FXML
    private Button registerBtn;
    @FXML
    private Button joinOnlineBtn;

    @Override
    public void setPublicShell(PublicShellController shell) {
        this.shell = shell;
    }

    @Override
    public void onShellReady() {
        int id = AppState.consumePendingPublicEventDetailId();
        if (id <= 0) {
            alert(Alert.AlertType.WARNING, "Événement", "Aucun événement sélectionné.");
            Platform.runLater(() -> {
                try {
                    if (shell != null) {
                        shell.loadPage("events");
                    }
                } catch (Exception ignored) {
                }
            });
            return;
        }
        this.eventId = id;
        loadEvent();
    }

    private void loadEvent() {
        try {
            Optional<Event> opt = eventService.findById(eventId);
            if (opt.isEmpty()) {
                alert(Alert.AlertType.INFORMATION, "Événement", "Cet événement n'existe pas ou n'est plus disponible.");
                if (shell != null) {
                    shell.loadPage("events");
                }
                return;
            }
            loaded = opt.get();
            bindLabels();
            refreshAuthUi();
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Erreur", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void bindLabels() {
        Event e = loaded;
        String dateStr = DATE_FMT.format(e.getDateDebut().toLocalDate());
        applyThematiqueHeroImage(e);
        if (thematiquePill != null) {
            String th = e.getThematique();
            if (th != null && !th.isBlank()) {
                thematiquePill.setText(th.trim());
                thematiquePill.setVisible(true);
                thematiquePill.setManaged(true);
            } else {
                thematiquePill.setVisible(false);
                thematiquePill.setManaged(false);
            }
        }
        titleLabel.setText(e.getTitre() != null ? e.getTitre() : "");
        dateLabel.setText("📅  " + dateStr);
        timeLabel.setText("🕐  " + TIME_FMT.format(e.getDateDebut()) + " – " + TIME_FMT.format(e.getDateFin()));
        String lieu = e.getLieu() != null ? e.getLieu() : "—";
        lieuMetaLabel.setText("📍  " + lieu);
        sidebarLieuLabel.setText(lieu);
        addressLabel.setText(lieu);
        String desc = e.getDescription() != null && !e.getDescription().isBlank() ? e.getDescription() : "—";
        descriptionLabel.setText(desc);
        if (weatherMockDateHero != null) {
            weatherMockDateHero.setText(dateStr);
        }
        if (weatherMockDateBottom != null) {
            weatherMockDateBottom.setText(dateStr);
        }
        refreshMapPreview();
    }

    private void applyThematiqueHeroImage(Event e) {
        if (thematiqueHeroImage == null || e == null) {
            return;
        }
        ensureHeroChrome();
        /* JavaFX Image(URL) sans User-Agent est souvent bloqué par les CDN ; chargement HTTP explicite. */
        thematiqueHeroImage.setImage(HeroImageLoader.softPlaceholder());
        final String source = resolveThematiqueHeroImageSource(e);
        CompletableFuture.supplyAsync(() -> HeroImageLoader.loadImage(source))
                .thenAccept(img -> Platform.runLater(() -> {
                    if (thematiqueHeroImage != null && img != null) {
                        thematiqueHeroImage.setImage(img);
                    }
                }));
    }

    /**
     * Priorité : image enregistrée sur la fiche thématique (BDD {@code image_chemin} sous la racine {@code public}),
     * sinon image de secours distante (Picsum) selon le libellé.
     */
    private String resolveThematiqueHeroImageSource(Event e) {
        String nomEvt = e.getThematique();
        if (nomEvt != null && !nomEvt.isBlank()) {
            try {
                Optional<Thematique> opt = thematiqueService.findByNomAffiche(nomEvt.trim());
                if (opt.isPresent()) {
                    String rel = opt.get().getImageChemin();
                    if (rel != null && !rel.isBlank()) {
                        var p = UserPublicAssets.resolvePublicRelative(rel.trim());
                        if (p != null && Files.isRegularFile(p)) {
                            return p.toUri().toString();
                        }
                    }
                }
            } catch (SQLException ignored) {
                /* table absente ou erreur : repli Picsum */
            }
        }
        return ThematiqueHeroImages.urlForThematique(e.getThematique());
    }

    private void ensureHeroChrome() {
        if (thematiqueHeroShell == null || thematiqueHeroImage == null) {
            return;
        }
        if (!heroShellWidthBound && thematiqueHeroShell.getParent() instanceof Region parentCol) {
            var heroW = parentCol.widthProperty().multiply(HERO_SHELL_WIDTH_RATIO);
            thematiqueHeroShell.prefWidthProperty().bind(heroW);
            thematiqueHeroShell.maxWidthProperty().bind(heroW);
            thematiqueHeroShell.setMinWidth(0);
            heroShellWidthBound = true;
        }
        if (!heroFitBound) {
            thematiqueHeroImage.fitWidthProperty().bind(thematiqueHeroShell.widthProperty());
            thematiqueHeroImage.setPreserveRatio(true);
            thematiqueHeroImage.setSmooth(true);
            heroFitBound = true;
        }
        if (!heroChromeInstalled) {
            Rectangle clip = new Rectangle();
            clip.setArcWidth(20);
            clip.setArcHeight(20);
            clip.widthProperty().bind(thematiqueHeroShell.widthProperty());
            clip.heightProperty().bind(thematiqueHeroShell.heightProperty());
            thematiqueHeroShell.setClip(clip);
            heroChromeInstalled = true;
        }
    }

    private void refreshMapPreview() {
        if (mapPlaceholder == null) {
            return;
        }
        mapPlaceholder.getChildren().clear();
        String url = resolveMapDisplayUrl(loaded);
        if (url != null) {
            WebView web = new WebView();
            web.setPrefHeight(360);
            web.setMinHeight(280);
            web.setMaxHeight(520);
            web.setMaxWidth(Double.MAX_VALUE);
            StackPane.setAlignment(web, Pos.CENTER);
            web.getEngine().setUserAgent(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            mapPlaceholder.getChildren().add(web);
            if (GoogleMapsEmbedUrls.requiresIframeDocument(url)) {
                web.getEngine().loadContent(GoogleMapsEmbedUrls.htmlDocumentWithMapIframe(url));
            } else {
                web.getEngine().load(url);
            }
            installGoogleTilesRecovery(web, loaded);
            return;
        }
        Label fallback = new Label(
                "Aucun lien Google Maps ni coordonnées : collez un lien dans le formulaire administrateur (champ « Lien Google Maps »), "
                        + "ou renseignez latitude / longitude.");
        fallback.getStyleClass().add("event-detail-map-hint");
        fallback.setWrapText(true);
        mapPlaceholder.getChildren().add(fallback);
    }

    private void installGoogleTilesRecovery(WebView web, Event event) {
        if (web == null || event == null) {
            return;
        }
        final boolean[] retried = {false};
        web.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (retried[0] || newState != Worker.State.SUCCEEDED) {
                return;
            }
            try {
                Object text = web.getEngine().executeScript("document && document.body ? document.body.innerText : ''");
                String body = text != null ? text.toString().toLowerCase(Locale.ROOT) : "";
                if (body.contains("aucune image n'est disponible pour cette zone")
                        || body.contains("no imagery here")
                        || body.contains("no imagery available")) {
                    String retryUrl = buildGoogleRoadmapRetryUrl(event);
                    if (retryUrl != null) {
                        retried[0] = true;
                        web.getEngine().loadContent(GoogleMapsEmbedUrls.htmlDocumentWithMapIframe(retryUrl));
                    }
                }
            } catch (Exception ignored) {
                /* non bloquant */
            }
        });
    }

    private static String buildGoogleRoadmapRetryUrl(Event e) {
        if (e == null) {
            return null;
        }
        if (e.getLatitude() != null && e.getLongitude() != null) {
            return String.format(Locale.US,
                    "https://www.google.com/maps?ll=%f,%f&z=15&t=m&output=embed&hl=fr",
                    e.getLatitude(), e.getLongitude());
        }
        String lieu = e.getLieu();
        if (lieu == null || lieu.isBlank()) {
            return null;
        }
        try {
            return "https://www.google.com/maps?q=" + URLEncoder.encode(lieu.trim(), StandardCharsets.UTF_8)
                    + "&z=15&t=m&output=embed&hl=fr";
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * URL chargée dans le WebView : vue « embed » (carte + repère, sans panneau latéral type recherche),
     * comme la capture 2. Priorité aux coordonnées extraites du lien ou stockées en base.
     */
    private static String resolveMapDisplayUrl(Event e) {
        if (e == null) {
            return null;
        }
        String link = e.getLienGoogleMaps();
        String t = link != null ? link.trim() : "";
        boolean hasHttpLink = !t.isEmpty() && (t.startsWith("http://") || t.startsWith("https://"));

        /* 1) Coordonnées dans le lien collé (évite une iframe Street View « sans imagerie »). */
        if (hasHttpLink) {
            Optional<double[]> fromLink = GoogleMapsEmbedUrls.tryExtractLatLng(t);
            if (fromLink.isPresent()) {
                double[] ll = fromLink.get();
                return GoogleMapsEmbedUrls.embedUrlForCoordinates(ll[0], ll[1], 17);
            }
            String queryFromLink = extractGoogleQueryText(t);
            if (queryFromLink != null && !queryFromLink.isBlank()) {
                return buildGoogleEmbedFromQuery(queryFromLink);
            }
        }
        /* 2) Coordonnées en base */
        if (e.getLatitude() != null && e.getLongitude() != null) {
            return GoogleMapsEmbedUrls.embedUrlForCoordinates(e.getLatitude(), e.getLongitude(), 17);
        }
        /* 3) Lien « Intégrer » sans lat/lng extractible : repli carte par adresse si possible. */
        if (hasHttpLink) {
            return GoogleMapsEmbedUrls.forWebViewEmbed(t);
        }
        String lieu = e.getLieu();
        if (lieu != null && !lieu.isBlank()) {
            return buildGoogleEmbedFromQuery(lieu.trim());
        }
        return null;
    }

    private static String extractGoogleQueryText(String mapsUrl) {
        if (mapsUrl == null || mapsUrl.isBlank()) {
            return null;
        }
        String u = mapsUrl.trim();
        int qIdx = u.indexOf("q=");
        if (qIdx >= 0) {
            int start = qIdx + 2;
            int end = u.indexOf('&', start);
            String raw = end > start ? u.substring(start, end) : u.substring(start);
            try {
                return URLDecoder.decode(raw, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return raw;
            }
        }
        String marker = "/place/";
        int p = u.indexOf(marker);
        if (p >= 0) {
            int start = p + marker.length();
            int end = u.indexOf('/', start);
            String raw = end > start ? u.substring(start, end) : u.substring(start);
            raw = raw.replace('+', ' ');
            try {
                return URLDecoder.decode(raw, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return raw;
            }
        }
        return null;
    }

    private static String buildGoogleEmbedFromQuery(String queryText) {
        try {
            return "https://www.google.com/maps?q=" + URLEncoder.encode(queryText, StandardCharsets.UTF_8)
                    + "&z=15&t=m&output=embed&hl=fr";
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String buildMapsSearchUri(Event e) {
        try {
            String link = e.getLienGoogleMaps();
            if (link != null) {
                String t = link.trim();
                if (!t.isEmpty() && (t.startsWith("http://") || t.startsWith("https://"))) {
                    return t;
                }
            }
            if (e.getLatitude() != null && e.getLongitude() != null) {
                return "https://www.google.com/maps/search/?api=1&query=" + e.getLatitude() + "," + e.getLongitude();
            }
            String q = e.getLieu() != null ? e.getLieu() : "";
            return "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "https://www.google.com/maps";
        }
    }

    @FXML
    public void onOpenMapsExternal() {
        if (loaded == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(buildMapsSearchUri(loaded)));
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Carte", "Impossible d'ouvrir le navigateur.");
        }
    }

    private void refreshAuthUi() {
        User u = AppState.getCurrentUser();
        boolean logged = u != null;
        boolean participant = logged && isPublicEventParticipant(u);
        if (guestAuthBox != null) {
            guestAuthBox.setVisible(!logged);
            guestAuthBox.setManaged(!logged);
        }
        if (loggedParticipantSidebarBox != null) {
            loggedParticipantSidebarBox.setVisible(participant);
            loggedParticipantSidebarBox.setManaged(participant);
        }
        if (loggedStaffSidebarBox != null) {
            boolean staff = logged && !participant;
            loggedStaffSidebarBox.setVisible(staff);
            loggedStaffSidebarBox.setManaged(staff);
        }
        if (participant) {
            reloadDiscussionThread();
            refreshRegisterButtonState(u);
        }
    }

    /**
     * Interface « famille » (discussion + inscription) pour tout compte connecté
     * <strong>sauf</strong> les rôles équipe ({@link Role#ADMIN}, {@link Role#MEDECIN}).
     * <p>
     * Évite d’afficher « Compte équipe… » pour des utilisateurs normaux dont le rôle en base
     * ne serait pas exactement PATIENT/PARENT/USER (ex. variante Symfony, compte test).
     * </p>
     */
    private static boolean isPublicEventParticipant(User u) {
        if (u == null) {
            return false;
        }
        Role r = u.getRole();
        if (r == null) {
            return true;
        }
        return r != Role.ADMIN && r != Role.MEDECIN;
    }

    private void refreshRegisterButtonState(User u) {
        if (u == null || loaded == null) {
            return;
        }
        try {
            Optional<EventRegistration> regOpt = registrationService.findByEventAndUser(eventId, u.getId());
            boolean already = regOpt.isPresent();
            boolean accepted = regOpt.map(r -> r.getStatut() == RegistrationStatus.ACCEPTE).orElse(false);
            if (registerHint != null) {
                registerHint.setText(already ? "Vous êtes déjà inscrit·e à cet événement." : "");
            }
            if (registerBtn != null) {
                registerBtn.setDisable(already);
            }
            if (joinOnlineBtn != null) {
                boolean showJoin = accepted && isOnlineOrHybridMode(loaded);
                joinOnlineBtn.setVisible(showJoin);
                joinOnlineBtn.setManaged(showJoin);
            }
        } catch (SQLException ex) {
            if (registerHint != null) {
                registerHint.setText("");
            }
            if (joinOnlineBtn != null) {
                joinOnlineBtn.setVisible(false);
                joinOnlineBtn.setManaged(false);
            }
        }
    }

    private static boolean isOnlineOrHybridMode(Event e) {
        if (e == null || e.getModeEvenement() == null) {
            return false;
        }
        String x = Normalizer.normalize(e.getModeEvenement(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return x.contains("en ligne") || x.contains("hybride") || x.contains("online") || x.contains("hybrid");
    }

    private void reloadDiscussionThread() {
        if (discussionThreadBox == null || loaded == null) {
            return;
        }
        discussionThreadBox.getChildren().clear();
        User u = AppState.getCurrentUser();
        if (u == null || !isPublicEventParticipant(u)) {
            return;
        }
        try {
            List<EventMessage> msgs = eventMessageService.listForUserAndEvent(eventId, u.getId());
            if (msgs.isEmpty()) {
                Label empty = new Label("Aucun message pour l’instant — écrivez votre première question ci-dessous.");
                empty.getStyleClass().add("event-detail-auth-muted");
                empty.setWrapText(true);
                discussionThreadBox.getChildren().add(empty);
                return;
            }
            for (EventMessage m : msgs) {
                VBox bubble = new VBox(4);
                bubble.getStyleClass().add("event-detail-discussion-bubble");
                String when = m.getDateEnvoi() != null ? MSG_TIME_FMT.format(m.getDateEnvoi()) : "";
                Label meta = new Label(when);
                meta.getStyleClass().add("event-detail-discussion-bubble-meta");
                Label body = new Label(m.getCorps());
                body.setWrapText(true);
                body.getStyleClass().add("event-detail-discussion-body");
                bubble.getChildren().addAll(meta, body);
                if (m.getExpediteurUserId() == u.getId()) {
                    Button deleteBtn = new Button("Supprimer");
                    deleteBtn.getStyleClass().add("event-detail-discussion-delete-btn");
                    deleteBtn.setOnAction(evt -> onDeleteOwnMessage(m));
                    bubble.getChildren().add(deleteBtn);
                }
                discussionThreadBox.getChildren().add(bubble);
            }
        } catch (SQLException ex) {
            Label empty = new Label("Aucun message pour l’instant — écrivez votre première question ci-dessous.");
            empty.getStyleClass().add("event-detail-auth-muted");
            empty.setWrapText(true);
            discussionThreadBox.getChildren().add(empty);
        }
    }

    private void onDeleteOwnMessage(EventMessage msg) {
        User u = AppState.getCurrentUser();
        if (u == null || msg == null) {
            return;
        }
        if (msg.getExpediteurUserId() != u.getId()) {
            alert(Alert.AlertType.WARNING, "Suppression", "Vous ne pouvez supprimer que vos propres messages.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le message");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer ce message définitivement ?");
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.OK) {
            return;
        }
        try {
            boolean deleted = eventMessageService.deleteOwnMessage(msg.getId(), u.getId());
            if (!deleted) {
                alert(Alert.AlertType.INFORMATION, "Suppression", "Ce message n'est plus disponible.");
            }
            reloadDiscussionThread();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Suppression", "Impossible de supprimer le message.");
        }
    }

    @FXML
    public void onSendOrganizerMessage() {
        User u = AppState.getCurrentUser();
        if (u == null || loaded == null || !isPublicEventParticipant(u)) {
            return;
        }
        String text = organizerMessageArea != null && organizerMessageArea.getText() != null
                ? organizerMessageArea.getText().trim()
                : "";
        if (text.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Message", "Saisissez votre message.");
            return;
        }
        try {
            eventMessageService.addMessage(eventId, u.getId(), text);
            String preview = text.length() > 120 ? text.substring(0, 120) + "…" : text;
            adminNotificationService.addNotification(
                    AdminNotificationService.TYPE_MESSAGE_EVENEMENT,
                    eventId,
                    u.getId(),
                    "Message événement : " + preview);
            if (organizerMessageArea != null) {
                organizerMessageArea.clear();
            }
            reloadDiscussionThread();
            alert(Alert.AlertType.INFORMATION, "Message", "Votre message a été envoyé à l’équipe.");
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR, "Message",
                    "Envoi impossible : " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
                            + "\n(Exécutez migration_evenement_messages_notifications.sql si les tables manquent.)");
        }
    }

    @FXML
    public void onLogin() {
        AppState.setPendingPublicEventDetailId(eventId);
        try {
            if (shell != null) {
                shell.loadPage("login");
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    @FXML
    public void onSignup() {
        AppState.setPendingPublicEventDetailId(eventId);
        try {
            if (shell != null) {
                shell.loadPage("signup");
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    @FXML
    public void onRegisterForEvent() {
        User u = AppState.getCurrentUser();
        if (u == null || loaded == null) {
            return;
        }
        try {
            if (registrationService.isUserRegistered(eventId, u.getId())) {
                alert(Alert.AlertType.INFORMATION, "Inscription", "Vous êtes déjà inscrit·e.");
                return;
            }
            EventRegistration r = new EventRegistration();
            r.setEvenementId(eventId);
            r.setUtilisateurId(u.getId());
            r.setStatut(RegistrationStatus.EN_ATTENTE);
            registrationService.add(r);
            String titre = loaded.getTitre() != null ? loaded.getTitre() : "Événement #" + eventId;
            try {
                adminNotificationService.addNotification(
                        AdminNotificationService.TYPE_INSCRIPTION_DEMANDE,
                        eventId,
                        u.getId(),
                        "Inscription demandée : « " + titre + " » — en attente de validation.");
            } catch (SQLException ignored) {
                /* notification secondaire : l’inscription est déjà enregistrée */
            }
            alert(Alert.AlertType.INFORMATION, "Inscription", "Demande d'inscription enregistrée (en attente de validation).");
            refreshAuthUi();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062) {
                alert(Alert.AlertType.INFORMATION, "Inscription", "Vous êtes déjà inscrit·e.");
            } else {
                alert(Alert.AlertType.ERROR, "Inscription", ex.getMessage());
            }
        }
    }

    @FXML
    public void onJoinOnlineMeeting() {
        if (loaded == null) {
            return;
        }
        String link = loaded.getLienZoomVisio();
        if (link == null || link.isBlank()) {
            alert(Alert.AlertType.INFORMATION, "Réunion en ligne", "Le lien Zoom n'est pas encore disponible.");
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(link.trim()));
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Réunion en ligne", "Impossible d'ouvrir le lien de réunion.");
        }
    }

    @FXML
    public void onBackToEvents() {
        try {
            if (shell != null) {
                shell.loadPage("events");
            }
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Navigation", e.getMessage());
        }
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
