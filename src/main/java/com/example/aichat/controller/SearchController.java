package com.example.aichat.controller;

import com.example.aichat.service.SearchService;
import com.example.aichat.service.TavilySearchService;
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

    @Autowired
    private TavilySearchService tavilySearchService;

    @PostMapping("/web")
    public ResponseEntity<Map<String, Object>> webSearch(
            @RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int count = request.get("count") != null ? ((Number) request.get("count")).intValue() : 10;

        List<Map<String, Object>> results = searchService.search(query, count);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("results", results);
        response.put("count", results.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/web")
    public ResponseEntity<Map<String, Object>> webSearchGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int count) {

        List<Map<String, Object>> results = searchService.search(query, count);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("results", results);
        response.put("count", results.size());

        return ResponseEntity.ok(response);
    }

    // ========== Tavily 搜索接口 ==========

    @PostMapping("/tavily")
    public ResponseEntity<Map<String, Object>> tavilySearch(
            @RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int maxResults = request.get("maxResults") != null ? ((Number) request.get("maxResults")).intValue() : 5;
        String searchDepth = (String) request.getOrDefault("searchDepth", "basic");
        boolean includeAnswer = request.get("includeAnswer") != null && (Boolean) request.get("includeAnswer");

        List<Map<String, Object>> results = tavilySearchService.search(query, maxResults, searchDepth, includeAnswer);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("results", results);
        response.put("count", results.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tavily")
    public ResponseEntity<Map<String, Object>> tavilySearchGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int maxResults,
            @RequestParam(defaultValue = "basic") String searchDepth,
            @RequestParam(defaultValue = "false") boolean includeAnswer) {

        List<Map<String, Object>> results = tavilySearchService.search(query, maxResults, searchDepth, includeAnswer);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("results", results);
        response.put("count", results.size());

        return ResponseEntity.ok(response);
    }
}