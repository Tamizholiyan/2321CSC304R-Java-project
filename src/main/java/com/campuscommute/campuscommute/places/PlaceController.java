package com.campuscommute.campuscommute.places;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<PlaceSuggestion>> searchPlaces(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng
    ) {
        List<PlaceSuggestion> suggestions = placeService.searchPlaces(query, lat, lng);
        return ResponseEntity.ok(suggestions);
    }
}
