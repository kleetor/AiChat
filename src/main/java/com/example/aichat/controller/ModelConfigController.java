package com.example.aichat.controller;

import com.example.aichat.dto.ModelConfigResponse;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.repository.ModelConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/model-configs")
public class ModelConfigController {

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @GetMapping
    @Cacheable(value = "modelConfigs", key = "'publicAll'")
    public ResponseEntity<List<ModelConfigResponse>> getAllConfigs() {
        List<ModelConfigResponse> configs = modelConfigRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/{id}")
    @Cacheable(value = "modelConfigs", key = "#id")
    public ResponseEntity<ModelConfigResponse> getConfigById(@PathVariable Long id) {
        return modelConfigRepository.findById(id)
                .map(this::convertToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private ModelConfigResponse convertToResponse(ModelConfig config) {
        return ModelConfigResponse.builder()
                .id(config.getId())
                .apiUrl(config.getApiUrl())
                .modelName(config.getModelName())
                .build();
    }
}