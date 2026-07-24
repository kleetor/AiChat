package com.example.aichat.controller;

import com.example.aichat.config.BusinessException;
import com.example.aichat.dto.KnowledgeBaseRequest;
import com.example.aichat.model.KbDocument;
import com.example.aichat.model.KnowledgeBase;
import com.example.aichat.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService kbService;

    public KnowledgeBaseController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    /** 创建知识库 */
    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody KnowledgeBaseRequest req,
                                     Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        KnowledgeBase kb = kbService.create(req.getName(), req.getDescription(),
                req.getPromptTemplate(), req.getChunkSize(), req.getChunkOverlap(), userId);
        return ResponseEntity.ok(kb);
    }

    /** 用户知识库列表 */
    @GetMapping("/list")
    public ResponseEntity<?> list(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(kbService.listByUser(userId));
    }

    /** 编辑知识库 */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                     @Valid @RequestBody KnowledgeBaseRequest req,
                                     Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        KnowledgeBase kb = kbService.update(id, userId, req.getName(), req.getDescription(),
                req.getPromptTemplate(), req.getChunkSize(), req.getChunkOverlap());
        return ResponseEntity.ok(kb);
    }

    /** 删除知识库 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        kbService.delete(id, userId);
        return ResponseEntity.ok(Map.of("message", "知识库已删除"));
    }

    /** 上传文档 */
    @PostMapping("/{kbId}/docs/upload")
    public ResponseEntity<?> uploadDocument(@PathVariable Long kbId,
                                             @RequestParam("file") MultipartFile file,
                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            KbDocument doc = kbService.uploadDocument(kbId, userId, file);
            return ResponseEntity.ok(doc);
        } catch (IOException e) {
            logger.error("文件上传失败", e);
            throw BusinessException.badRequest("文件上传失败，请稍后重试");
        }
    }

    /** 文档列表 */
    @GetMapping("/{kbId}/docs")
    public ResponseEntity<?> listDocuments(@PathVariable Long kbId,
                                            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(kbService.listDocuments(kbId, userId));
    }

    /** 删除文档 */
    @DeleteMapping("/docs/{docId}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long docId,
                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        kbService.deleteDocument(docId, userId);
        return ResponseEntity.ok(Map.of("message", "文档已删除"));
    }

    /** 重新索引 */
    @PostMapping("/docs/{docId}/reindex")
    public ResponseEntity<?> reindex(@PathVariable Long docId,
                                      Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        kbService.reindex(docId, userId);
        return ResponseEntity.ok(Map.of("message", "重新索引已启动"));
    }
}
