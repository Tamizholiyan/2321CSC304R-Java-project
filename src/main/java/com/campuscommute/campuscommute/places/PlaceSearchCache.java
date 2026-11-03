package com.campuscommute.campuscommute.places;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PlaceSearchCache {

    private static final int MAX_ENTRIES = 500;
    private static final long TTL_MS = 10 * 60 * 1000L; // 10 minutes

    private record CacheEntry(List<PlaceSuggestion> suggestions, long timestamp) {}

    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(
            new LinkedHashMap<String, CacheEntry>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            }
    );

    public List<PlaceSuggestion> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.timestamp() > TTL_MS) {
            cache.remove(key);
            return null;
        }
        return entry.suggestions();
    }

    public void put(String key, List<PlaceSuggestion> suggestions) {
        cache.put(key, new CacheEntry(suggestions, System.currentTimeMillis()));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
