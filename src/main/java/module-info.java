module org.example.pi {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;
    requires mysql.connector.j;

    requires bcrypt;
    requires jakarta.mail;
    requires jdk.httpserver;
    requires java.net.http;
    requires java.desktop;
    requires java.prefs;
    requires org.apache.pdfbox;

    opens org.example to javafx.graphics;
    opens org.example.controllers to javafx.fxml;
}
