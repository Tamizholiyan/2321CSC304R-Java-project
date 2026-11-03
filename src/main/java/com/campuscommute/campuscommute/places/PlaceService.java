package com.campuscommute.campuscommute.places;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class PlaceService {

    private final PhotonClient photonClient;
    private final PlaceSearchCache placeSearchCache;

    public PlaceService(PhotonClient photonClient, PlaceSearchCache placeSearchCache) {
        this.photonClient = photonClient;
        this.placeSearchCache = placeSearchCache;
    }

    public List<PlaceSuggestion> searchPlaces(String query, Double lat, Double lng) {
        if (query == null) {
            return Collections.emptyList();
        }
        String trimmedQuery = query.trim();
        // Fewer than 3 characters returns an empty list immediately
        if (trimmedQuery.length() < 3) {
            return Collections.emptyList();
        }

        // Cache key incorporates query and approximate center
        String cacheKey = trimmedQuery.toLowerCase()
                + "|" + (lat != null ? Math.round(lat * 100) : "def")
                + "|" + (lng != null ? Math.round(lng * 100) : "def");

        List<PlaceSuggestion> cached = placeSearchCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<PlaceSuggestion> fresh = photonClient.search(trimmedQuery, lat, lng);
        placeSearchCache.put(cacheKey, fresh);
        return fresh;
    }
}
