package com.example.aichat.controller;

import com.example.aichat.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = "*")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @PostMapping("/web")
    public ResponseEntity<Map<String, Object>> webSearch(
            @RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        boolean summary = request.get("summary") != null ? (Boolean) request.get("summary") : true;
        String freshness = (String) request.getOrDefault("freshness", "noLimit");
        int count = request.get("count") != null ? ((Number) request.get("count")).intValue() : 10;

        List<Map<String, Object>> results = searchService.search(query, summary, freshness, count);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("results", results);
        response.put("count", results.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/web")
    public ResponseEntity<Map<String, Object>> webSearchGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "true") boolean summary,
            @RequestParam(defaultValue = "noLimit") String freshness,
            @RequestParam(defaultValue = "10") int count) {

        List<Map<String, Object>> results = searchService.search(query, summary, freshness, count);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("results", results);
        response.put("count", results.size());

        return ResponseEntity.ok(response);
    }
}