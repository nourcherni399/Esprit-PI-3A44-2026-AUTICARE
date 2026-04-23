package org.example.models;

public class AdminUser extends User {
    public AdminUser() {
        setRole(Role.ADMIN);
    }
}
