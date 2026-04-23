package org.example.models;

public final class UserFactory {
    private UserFactory() {
    }

    public static User createByRole(Role role) {
        if (role == null) {
            return new StandardUser();
        }
        return switch (role) {
            case ADMIN -> new AdminUser();
            case MEDECIN -> new Medecin();
            case PARENT -> new ParentUser();
            case PATIENT -> new Patient();
            case USER -> new StandardUser();
        };
    }
}
