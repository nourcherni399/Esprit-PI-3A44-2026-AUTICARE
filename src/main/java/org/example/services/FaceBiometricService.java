package org.example.services;

import java.io.File;

public interface FaceBiometricService {
    String enrollFromImage(File imageFile) throws FaceBiometricException;

    double similarity(String enrolledTemplateJson, File probeImage) throws FaceBiometricException;
}
