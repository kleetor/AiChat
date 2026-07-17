// controller/PromptController.java
package com.example.aichat.controller;

import com.example.aichat.dto.PromptCreateRequest;
import com.example.aichat.model.Prompt;
import com.example.aichat.service.PromptService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<Prompt> createPrompt(@Valid @RequestBody PromptCreateRequest body,
                                               Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Prompt prompt = promptService.createPrompt(userId, body.getName(), body.getContent());
        return ResponseEntity.ok(prompt);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Prompt> updatePrompt(@PathVariable Long id,
                                               @Valid @RequestBody PromptCreateRequest body,
                                               Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Prompt prompt = promptService.updatePrompt(id, userId, body.getName(), body.getContent());
        return ResponseEntity.ok(prompt);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePrompt(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        promptService.deletePrompt(id, userId);
        return ResponseEntity.ok().build();
    }
}
