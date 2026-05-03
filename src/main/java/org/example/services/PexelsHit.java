package org.example.services;

/**
 * Une photo renvoyée par l'API Pexels.
 */
public record PexelsHit(String pageUrl, String previewUrl, String largeUrl, String photographer) {
}
