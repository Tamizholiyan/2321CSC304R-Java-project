package com.campuscommute.campuscommute.places;

public record PlaceSuggestion(
        String name,
        String address,
        Double lat,
        Double lng
) {}
