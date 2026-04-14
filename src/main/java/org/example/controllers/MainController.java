package org.example.controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.MainApp;
import org.example.models.*;
import org.example.services.*;
import org.example.utils.AppState;
import org.example.utils.PasswordUtil;

import java.time.LocalDateTime;

public class MainController {
    @FXML private Label connectedLabel;
    @FXML private TabPane mainTabPane;

    @FXML private TextField userIdField;
    @FXML private TextField userNomField;
    @FXML private TextField userPrenomField;
    @FXML private TextField userEmailField;
    @FXML private TextField userTelephoneField;
    @FXML private PasswordField userPasswordField;
    @FXML private ComboBox<Role> userRoleBox;
    @FXML private CheckBox userActifBox;
    @FXML private TextField userSearchField;
    @FXML private ComboBox<Role> userRoleFilterBox;
    @FXML private ListView<User> usersList;

    @FXML private TextField productIdField;
    @FXML private TextField productNomField;
    @FXML private TextArea productDescriptionField;
    @FXML private TextField productPrixField;
    @FXML private TextField productCategorieField;
    @FXML private TextField productStockField;
    @FXML private CheckBox productPublieBox;
    @FXML private ListView<Product> productsList;

    @FXML private TextField apptIdField;
    @FXML private TextField apptMedecinIdField;
    @FXML private TextField apptPatientIdField;
    @FXML private TextField apptDateTimeField;
    @FXML private TextField apptMotifField;
    @FXML private ComboBox<AppointmentStatus> apptStatusBox;
    @FXML private TextArea apptNotesField;
    @FXML private ListView<Appointment> apptsList;
    @FXML private TextField availIdField;
    @FXML private TextField availMedecinIdField;
    @FXML private TextField availStartField;
    @FXML private TextField availEndField;
    @FXML private ListView<Availability> availList;

    @FXML private TextField eventIdField;
    @FXML private TextField eventTitreField;
    @FXML private TextArea eventDescriptionField;
    @FXML private TextField eventStartField;
    @FXML private TextField eventEndField;
    @FXML private TextField eventLieuField;
    @FXML private TextField eventPlacesField;
    @FXML private ComboBox<EventStatus> eventStatusBox;
    @FXML private ListView<Event> eventsList;
    @FXML private TextField regIdField;
    @FXML private TextField regEventIdField;
    @FXML private TextField regUserIdField;
    @FXML private ComboBox<RegistrationStatus> regStatusBox;
    @FXML private ListView<EventRegistration> regsList;

    @FXML private TextField moduleIdField;
    @FXML private TextField moduleTitreField;
    @FXML private TextArea moduleDescriptionField;
    @FXML private TextField moduleCategorieField;
    @FXML private TextField moduleLienField;
    @FXML private ListView<ModuleContent> modulesList;

    @FXML private TextField articleIdField;
    @FXML private TextField articleTitreField;
    @FXML private TextArea articleContenuField;
    @FXML private TextField articleAuteurIdField;
    @FXML private TextField articleCategorieField;
    @FXML private TextField articleSlugField;
    @FXML private ComboBox<ModuleContent> articleModuleBox;
    @FXML private ListView<BlogArticle> articlesList;

    private final UserService userService = new UserService();
    private final ProductService productService = new ProductService();
    private final AppointmentService appointmentService = new AppointmentService();
    private final AvailabilityService availabilityService = new AvailabilityService();
    private final EventService eventService = new EventService();
    private final EventRegistrationService registrationService = new EventRegistrationService();
    private final ModuleService moduleService = new ModuleService();
    private final BlogService blogService = new BlogService();

    @FXML
    public void initialize() {
        connectedLabel.setText("Connecte: " + AppState.getCurrentUser());
        userRoleBox.setItems(FXCollections.observableArrayList(Role.values()));
        userRoleFilterBox.setItems(FXCollections.observableArrayList(Role.values()));
        apptStatusBox.setItems(FXCollections.observableArrayList(AppointmentStatus.values()));
        eventStatusBox.setItems(FXCollections.observableArrayList(EventStatus.values()));
        regStatusBox.setItems(FXCollections.observableArrayList(RegistrationStatus.values()));
        refreshAll();

        if (mainTabPane != null) {
            int idx = MainApp.getPendingDashboardTabIndex();
            int n = mainTabPane.getTabs().size();
            if (n > 0) {
                mainTabPane.getSelectionModel().select(Math.min(Math.max(0, idx), n - 1));
            }
        }
    }

    @FXML
    public void onLogout() throws Exception {
        AppState.clear();
        MainApp.showLogin();
    }

    @FXML
    public void onSaveUser() {
        try {
            User u = new User();
            if (!userIdField.getText().isBlank()) u.setId(Integer.parseInt(userIdField.getText()));
            u.setNom(userNomField.getText());
            u.setPrenom(userPrenomField.getText());
            u.setEmail(userEmailField.getText());
            u.setTelephone(userTelephoneField.getText());
            if (!userPasswordField.getText().isBlank()) {
                u.setMotDePasseHash(PasswordUtil.hash(userPasswordField.getText()));
            } else if (u.getId() > 0) {
                u.setMotDePasseHash(userService.findById(u.getId()).orElseThrow().getMotDePasseHash());
            }
            u.setRole(userRoleBox.getValue() == null ? Role.USER : userRoleBox.getValue());
            u.setActif(userActifBox.isSelected());
            if (u.getId() > 0) userService.update(u); else userService.add(u);
            refreshUsers();
            clearUserForm();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onDeleteUser() {
        try {
            User selected = usersList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            if (!confirmDelete()) return;
            userService.delete(selected.getId());
            refreshUsers();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onSearchUsers() {
        try {
            if (!userSearchField.getText().isBlank()) {
                usersList.setItems(FXCollections.observableArrayList(userService.search(userSearchField.getText().trim())));
                return;
            }
            if (userRoleFilterBox.getValue() != null) {
                usersList.setItems(FXCollections.observableArrayList(userService.findByRole(userRoleFilterBox.getValue())));
                return;
            }
            refreshUsers();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onUserSelected() {
        User u = usersList.getSelectionModel().getSelectedItem();
        if (u == null) return;
        userIdField.setText(String.valueOf(u.getId()));
        userNomField.setText(u.getNom());
        userPrenomField.setText(u.getPrenom());
        userEmailField.setText(u.getEmail());
        userTelephoneField.setText(u.getTelephone());
        userPasswordField.clear();
        userRoleBox.setValue(u.getRole());
        userActifBox.setSelected(u.isActif());
    }

    @FXML
    public void onSaveProduct() {
        try {
            Product p = new Product();
            if (!productIdField.getText().isBlank()) p.setId(Integer.parseInt(productIdField.getText()));
            p.setNom(productNomField.getText());
            p.setDescription(productDescriptionField.getText());
            p.setPrix(Double.parseDouble(productPrixField.getText()));
            p.setCategorie(productCategorieField.getText());
            p.setStock(Integer.parseInt(productStockField.getText()));
            p.setPublie(productPublieBox.isSelected());
            if (p.getId() > 0) productService.update(p); else productService.add(p);
            refreshProducts();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onDeleteProduct() {
        try {
            Product selected = productsList.getSelectionModel().getSelectedItem();
            if (selected == null || !confirmDelete()) return;
            productService.delete(selected.getId());
            refreshProducts();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onProductSelected() {
        Product p = productsList.getSelectionModel().getSelectedItem();
        if (p == null) return;
        productIdField.setText(String.valueOf(p.getId()));
        productNomField.setText(p.getNom());
        productDescriptionField.setText(p.getDescription());
        productPrixField.setText(String.valueOf(p.getPrix()));
        productCategorieField.setText(p.getCategorie());
        productStockField.setText(String.valueOf(p.getStock()));
        productPublieBox.setSelected(p.isPublie());
    }

    @FXML
    public void onSaveAppointment() {
        try {
            Appointment a = new Appointment();
            if (!apptIdField.getText().isBlank()) a.setId(Integer.parseInt(apptIdField.getText()));
            a.setMedecinId(Integer.parseInt(apptMedecinIdField.getText()));
            a.setPatientId(Integer.parseInt(apptPatientIdField.getText()));
            a.setDateHeure(LocalDateTime.parse(apptDateTimeField.getText()));
            a.setMotif(apptMotifField.getText());
            a.setStatus(apptStatusBox.getValue() == null ? AppointmentStatus.PLANIFIE : apptStatusBox.getValue());
            a.setNotes(apptNotesField.getText());
            if (a.getId() > 0) appointmentService.update(a); else appointmentService.add(a);
            refreshAppointments();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onSaveAvailability() {
        try {
            Availability a = new Availability();
            if (!availIdField.getText().isBlank()) a.setId(Integer.parseInt(availIdField.getText()));
            a.setMedecinId(Integer.parseInt(availMedecinIdField.getText()));
            a.setDebut(LocalDateTime.parse(availStartField.getText()));
            a.setFin(LocalDateTime.parse(availEndField.getText()));
            if (a.getId() > 0) availabilityService.update(a); else availabilityService.add(a);
            refreshAvailabilities();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onDeleteAvailability() {
        try {
            Availability selected = availList.getSelectionModel().getSelectedItem();
            if (selected == null || !confirmDelete()) return;
            availabilityService.delete(selected.getId());
            refreshAvailabilities();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onAvailabilitySelected() {
        Availability a = availList.getSelectionModel().getSelectedItem();
        if (a == null) return;
        availIdField.setText(String.valueOf(a.getId()));
        availMedecinIdField.setText(String.valueOf(a.getMedecinId()));
        availStartField.setText(a.getDebut().toString());
        availEndField.setText(a.getFin().toString());
    }

    @FXML
    public void onDeleteAppointment() {
        try {
            Appointment selected = apptsList.getSelectionModel().getSelectedItem();
            if (selected == null || !confirmDelete()) return;
            appointmentService.delete(selected.getId());
            refreshAppointments();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onAppointmentSelected() {
        Appointment a = apptsList.getSelectionModel().getSelectedItem();
        if (a == null) return;
        apptIdField.setText(String.valueOf(a.getId()));
        apptMedecinIdField.setText(String.valueOf(a.getMedecinId()));
        apptPatientIdField.setText(String.valueOf(a.getPatientId()));
        apptDateTimeField.setText(a.getDateHeure().toString());
        apptMotifField.setText(a.getMotif());
        apptStatusBox.setValue(a.getStatus());
        apptNotesField.setText(a.getNotes());
    }

    @FXML
    public void onSaveEvent() {
        try {
            Event e = new Event();
            if (!eventIdField.getText().isBlank()) e.setId(Integer.parseInt(eventIdField.getText()));
            e.setTitre(eventTitreField.getText());
            e.setDescription(eventDescriptionField.getText());
            e.setDateDebut(LocalDateTime.parse(eventStartField.getText()));
            e.setDateFin(LocalDateTime.parse(eventEndField.getText()));
            e.setLieu(eventLieuField.getText());
            e.setPlacesMax(Integer.parseInt(eventPlacesField.getText()));
            e.setStatut(eventStatusBox.getValue() == null ? EventStatus.BROUILLON : eventStatusBox.getValue());
            if (e.getId() > 0) eventService.update(e); else eventService.add(e);
            refreshEvents();
        } catch (Exception ex) { showError(ex); }
    }

    @FXML
    public void onSaveRegistration() {
        try {
            EventRegistration r = new EventRegistration();
            if (!regIdField.getText().isBlank()) r.setId(Integer.parseInt(regIdField.getText()));
            r.setEvenementId(Integer.parseInt(regEventIdField.getText()));
            r.setUtilisateurId(Integer.parseInt(regUserIdField.getText()));
            r.setStatut(regStatusBox.getValue() == null ? RegistrationStatus.EN_ATTENTE : regStatusBox.getValue());
            if (r.getId() > 0) registrationService.update(r); else registrationService.add(r);
            refreshRegistrations();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onDeleteRegistration() {
        try {
            EventRegistration selected = regsList.getSelectionModel().getSelectedItem();
            if (selected == null || !confirmDelete()) return;
            registrationService.delete(selected.getId());
            refreshRegistrations();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onRegistrationSelected() {
        EventRegistration r = regsList.getSelectionModel().getSelectedItem();
        if (r == null) return;
        regIdField.setText(String.valueOf(r.getId()));
        regEventIdField.setText(String.valueOf(r.getEvenementId()));
        regUserIdField.setText(String.valueOf(r.getUtilisateurId()));
        regStatusBox.setValue(r.getStatut());
    }

    @FXML
    public void onDeleteEvent() {
        try {
            Event selected = eventsList.getSelectionModel().getSelectedItem();
            if (selected == null || !confirmDelete()) return;
            eventService.delete(selected.getId());
            refreshEvents();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onEventSelected() {
        Event e = eventsList.getSelectionModel().getSelectedItem();
        if (e == null) return;
        eventIdField.setText(String.valueOf(e.getId()));
        eventTitreField.setText(e.getTitre());
        eventDescriptionField.setText(e.getDescription());
        eventStartField.setText(e.getDateDebut().toString());
        eventEndField.setText(e.getDateFin().toString());
        eventLieuField.setText(e.getLieu());
        eventPlacesField.setText(String.valueOf(e.getPlacesMax()));
        eventStatusBox.setValue(e.getStatut());
    }

    @FXML
    public void onSaveModule() {
        try {
            ModuleContent m = new ModuleContent();
            if (!moduleIdField.getText().isBlank()) m.setId(Integer.parseInt(moduleIdField.getText()));
            m.setTitre(moduleTitreField.getText());
            m.setDescription(moduleDescriptionField.getText());
            m.setCategorie(moduleCategorieField.getText());
            m.setRessourcesLien(moduleLienField.getText());
            if (m.getId() > 0) moduleService.update(m); else moduleService.add(m);
            refreshModules();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onDeleteModule() {
        try {
            ModuleContent selected = modulesList.getSelectionModel().getSelectedItem();
            if (selected == null || !confirmDelete()) return;
            moduleService.delete(selected.getId());
            refreshModules();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onModuleSelected() {
        ModuleContent m = modulesList.getSelectionModel().getSelectedItem();
        if (m == null) return;
        moduleIdField.setText(String.valueOf(m.getId()));
        moduleTitreField.setText(m.getTitre());
        moduleDescriptionField.setText(m.getDescription());
        moduleCategorieField.setText(m.getCategorie());
        moduleLienField.setText(m.getRessourcesLien());
    }

    @FXML
    public void onSaveArticle() {
        try {
            BlogArticle a = new BlogArticle();
            if (!articleIdField.getText().isBlank()) a.setId(Integer.parseInt(articleIdField.getText()));
            a.setTitre(articleTitreField.getText());
            a.setContenu(articleContenuField.getText());
            a.setAuteurId(Integer.parseInt(articleAuteurIdField.getText()));
            a.setCategorie(articleCategorieField.getText());
            a.setSlug(articleSlugField.getText());
            if (articleModuleBox.getValue() != null) a.setModuleId(articleModuleBox.getValue().getId());
            if (a.getId() > 0) blogService.update(a); else blogService.add(a);
            refreshArticles();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onDeleteArticle() {
        try {
            BlogArticle selected = articlesList.getSelectionModel().getSelectedItem();
            if (selected == null || !confirmDelete()) return;
            blogService.delete(selected.getId());
            refreshArticles();
        } catch (Exception e) { showError(e); }
    }

    @FXML
    public void onArticleSelected() {
        BlogArticle a = articlesList.getSelectionModel().getSelectedItem();
        if (a == null) return;
        articleIdField.setText(String.valueOf(a.getId()));
        articleTitreField.setText(a.getTitre());
        articleContenuField.setText(a.getContenu());
        articleAuteurIdField.setText(String.valueOf(a.getAuteurId()));
        articleCategorieField.setText(a.getCategorie());
        articleSlugField.setText(a.getSlug());
    }

    @FXML
    public void refreshAll() {
        refreshUsers();
        refreshProducts();
        refreshAppointments();
        refreshAvailabilities();
        refreshEvents();
        refreshRegistrations();
        refreshModules();
        refreshArticles();
    }

    private void refreshUsers() {
        try { usersList.setItems(FXCollections.observableArrayList(userService.findAll())); } catch (Exception e) { showError(e); }
    }
    private void refreshProducts() {
        try { productsList.setItems(FXCollections.observableArrayList(productService.findAll())); } catch (Exception e) { showError(e); }
    }
    private void refreshAppointments() {
        try { apptsList.setItems(FXCollections.observableArrayList(appointmentService.findAll())); } catch (Exception e) { showError(e); }
    }
    private void refreshAvailabilities() {
        try { availList.setItems(FXCollections.observableArrayList(availabilityService.findAll())); } catch (Exception e) { showError(e); }
    }
    private void refreshEvents() {
        try { eventsList.setItems(FXCollections.observableArrayList(eventService.findAll())); } catch (Exception e) { showError(e); }
    }
    private void refreshRegistrations() {
        try { regsList.setItems(FXCollections.observableArrayList(registrationService.findAll())); } catch (Exception e) { showError(e); }
    }
    private void refreshModules() {
        try {
            var modules = FXCollections.observableArrayList(moduleService.findAll());
            modulesList.setItems(modules);
            articleModuleBox.setItems(modules);
        } catch (Exception e) { showError(e); }
    }
    private void refreshArticles() {
        try { articlesList.setItems(FXCollections.observableArrayList(blogService.findAll())); } catch (Exception e) { showError(e); }
    }

    private void clearUserForm() {
        userIdField.clear();
        userNomField.clear();
        userPrenomField.clear();
        userEmailField.clear();
        userTelephoneField.clear();
        userPasswordField.clear();
        userRoleBox.setValue(null);
        userActifBox.setSelected(true);
    }

    private boolean confirmDelete() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Supprimer l'element ?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void showError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText("Operation impossible");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}
