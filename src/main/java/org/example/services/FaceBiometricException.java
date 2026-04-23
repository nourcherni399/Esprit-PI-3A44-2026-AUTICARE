package org.example.services;

public class FaceBiometricException extends Exception {
    private final String code;

    public FaceBiometricException(String code, String message) {
        super(message);
        this.code = code;
    }

    public FaceBiometricException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
