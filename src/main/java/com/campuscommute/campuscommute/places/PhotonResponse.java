package com.campuscommute.campuscommute.places;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PhotonResponse(
        List<Feature> features
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(
            Geometry geometry,
            Properties properties
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(
            List<Double> coordinates
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            String name,
            String street,
            String housenumber,
            String suburb,
            String district,
            String city,
            String locality,
            String state,
            String postcode,
            String country
    ) {}
}
