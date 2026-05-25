// controller/PromptController.java
package com.example.aichat.controller;

import com.example.aichat.model.Prompt;
import com.example.aichat.service.PromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    @Autowired
    private PromptService promptService;

    @GetMapping
    public ResponseEntity<List<Prompt>> getUserPrompts(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(promptService.getUserPrompts(userId));
    }

    @PostMapping
    public ResponseEntity<Prompt> createPrompt(@RequestBody Map<String, String> body,
                                               Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String name = body.get("name");
        String content = body.get("content");
        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Prompt prompt = promptService.createPrompt(userId, name, content);
        return ResponseEntity.ok(prompt);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Prompt> updatePrompt(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String name = body.get("name");
        String content = body.get("content");
        try {
            Prompt prompt = promptService.updatePrompt(id, userId, name, content);
            return ResponseEntity.ok(prompt);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePrompt(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            promptService.deletePrompt(id, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
