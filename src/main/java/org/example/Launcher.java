package org.example;

import javafx.application.Application;

/**
 * Point d’entrée pour {@code mvn javafx:run} sans {@code module-info} : le plugin interdit une classe
 * qui étend {@link Application} comme main sur le classpath ; ce lanceur délègue à {@link MainApp}.
 */
public final class Launcher {

    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
