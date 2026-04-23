package org.example.utils;

import javafx.util.StringConverter;
import org.example.models.ModuleCategorie;

/** Affiche les libellés français dans les {@link javafx.scene.control.ComboBox} de catégorie module. */
public class ModuleCategorieStringConverter extends StringConverter<ModuleCategorie> {

    public static final ModuleCategorieStringConverter INSTANCE = new ModuleCategorieStringConverter();

    private ModuleCategorieStringConverter() {
    }

    @Override
    public String toString(ModuleCategorie c) {
        return c == null ? "" : c.getLibelle();
    }

    @Override
    public ModuleCategorie fromString(String string) {
        if (string == null || string.isBlank()) {
            return null;
        }
        for (ModuleCategorie c : ModuleCategorie.values()) {
            if (c.getLibelle().equals(string)) {
                return c;
            }
        }
        return null;
    }
}
