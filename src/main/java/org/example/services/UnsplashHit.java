package org.example.services;

/**
 * Une photo renvoyée par l’API de recherche Unsplash (aperçu + page pour attribution).
 */
public record UnsplashHit(String pageUrl, String thumbUrl, String caption) {
}
