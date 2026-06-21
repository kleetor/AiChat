package com.example.aichat.service;

import com.example.aichat.model.KbDocument;
import com.example.aichat.model.KnowledgeBase;
import com.example.aichat.repository.KbDocumentRepository;
import com.example.aichat.repository.KnowledgeBaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepo;
    private final KbDocumentRepository docRepo;
    private final ChromaDBService chromaDBService;
    private final ChunkingService chunkingService;
    private final PdfParser pdfParser;
    private final TransactionTemplate transactionTemplate;

    private static final Path UPLOAD_DIR = Paths.get("./uploads/kb");

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                 KbDocumentRepository docRepo,
                                 ChromaDBService chromaDBService,
                                 ChunkingService chunkingService,
                                 PdfParser pdfParser,
                                 PlatformTransactionManager transactionManager) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chromaDBService = chromaDBService;
        this.chunkingService = chunkingService;
        this.pdfParser = pdfParser;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        try { Files.createDirectories(UPLOAD_DIR); } catch (IOException ignored) {}
    }

    /** 创建知识库 + ChromaDB Collection */
    @Transactional
    public KnowledgeBase create(String name, String description, Long userId) {
        KnowledgeBase kb = kbRepo.save(KnowledgeBase.builder()
                .name(name).description(description)
                .userId(userId).visibility("PRIVATE")
                .build());
        try {
            chromaDBService.createCollection(kb.getId());
        } catch (Exception e) {
            log.error("创建 ChromaDB Collection 失败: kbId={}", kb.getId(), e);
        }
        return kb;
    }

    /** 用户知识库列表（用 kb_documents 表实时统计，避免计数不同步） */
    public List<KnowledgeBase> listByUser(Long userId) {
        List<KnowledgeBase> kbs = kbRepo.findByUserId(userId);
        for (KnowledgeBase kb : kbs) {
            kb.setDocCount((int) docRepo.countByKbId(kb.getId()));
            kb.setChunkCount((int) docRepo.sumChunkCountByKbId(kb.getId()));
            kb.setTotalSize(docRepo.sumFileSizeByKbId(kb.getId()));
        }
        return kbs;
    }

    /** 编辑知识库 */
    @Transactional
    public KnowledgeBase update(Long kbId, Long userId, String name, String description) {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改该知识库");
        }
        if (name != null) kb.setName(name);
        if (description != null) kb.setDescription(description);
        kb.setUpdatedAt(LocalDateTime.now());
        return kbRepo.save(kb);
    }

    /** 删除知识库 */
    @Transactional
    public void delete(Long kbId, Long userId) {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该知识库");
        }
        chromaDBService.deleteCollection(kbId);
        kbRepo.delete(kb);
    }

    /** 上传文档 */
    public KbDocument uploadDocument(Long kbId, Long userId, MultipartFile file) throws IOException {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw new RuntimeException("无权上传文档到该知识库");
        }

        // 保存文件到本地
        final String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";
        final String fileType = getFileType(fileName);
        final String storedName = UUID.randomUUID() + "_" + fileName;
        Path targetPath = UPLOAD_DIR.resolve(storedName);
        file.transferTo(targetPath);

        // 用 TransactionTemplate 确保 doc 先 COMMIT，异步任务才能读到
        KbDocument doc = transactionTemplate.execute(status ->
                docRepo.save(KbDocument.builder()
                        .kbId(kbId).fileName(fileName).fileType(fileType)
                        .fileSize(file.getSize()).s3Key(storedName)
                        .status("PROCESSING").build())
        );

        // 异步处理（只传 docId，避免游离实体冲突）
        final Long docId = doc.getId();
        CompletableFuture.runAsync(() -> processDocument(docId, targetPath));
        return doc;
    }

    /** 文档列表 */
    public List<KbDocument> listDocuments(Long kbId, Long userId) {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看该知识库文档");
        }
        return docRepo.findByKbId(kbId);
    }

    /** 删除文档 */
    @Transactional
    public void deleteDocument(Long docId, Long userId) {
        KbDocument doc = docRepo.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        KnowledgeBase kb = kbRepo.findById(doc.getKbId())
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该文档");
        }
        chromaDBService.deleteByDocument(doc.getKbId(), doc.getId());
        docRepo.deleteById(doc.getId());
        kbRepo.decrementCounts(doc.getKbId(), 1, doc.getChunkCount(), doc.getFileSize());

        // 删除本地文件
        try { Files.deleteIfExists(UPLOAD_DIR.resolve(doc.getS3Key())); } catch (IOException ignored) {}
    }

    /** 重新索引文档 */
    public void reindex(Long docId, Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            KbDocument doc = docRepo.findById(docId)
                    .orElseThrow(() -> new RuntimeException("文档不存在"));
            KnowledgeBase kb = kbRepo.findById(doc.getKbId())
                    .orElseThrow(() -> new RuntimeException("知识库不存在"));
            if (!kb.getUserId().equals(userId)) {
                throw new RuntimeException("无权操作该文档");
            }

            // 删除旧向量
            chromaDBService.deleteByDocument(doc.getKbId(), doc.getId());
            kbRepo.decrementCounts(doc.getKbId(), 0, doc.getChunkCount(), 0);

            // 重新处理
            doc.setStatus("PROCESSING");
            doc.setChunkCount(0);
            doc.setErrorMsg(null);
            docRepo.save(doc);
        });

        // 事务提交后再启动异步任务
        final Long reDocId = docId;
        KbDocument doc = docRepo.findById(docId).orElse(null);
        if (doc == null) return;
        Path filePath = UPLOAD_DIR.resolve(doc.getS3Key());
        CompletableFuture.runAsync(() -> processDocument(reDocId, filePath));
    }

    // ---------- 内部 ----------

    /**
     * 异步文档处理 — 使用 TransactionTemplate 确保每次 DB 操作在新事务中执行，
     * 避免游离实体导致的乐观锁冲突。
     */
    void processDocument(Long docId, Path filePath) {
        transactionTemplate.executeWithoutResult(status -> {
            KbDocument doc = docRepo.findById(docId).orElse(null);
            if (doc == null) {
                log.error("文档不存在: docId={}", docId);
                return;
            }

            try {
                String text = parseDocument(filePath, doc.getFileType());
                List<String> chunks = chunkingService.split(text);
                if (chunks.isEmpty()) {
                    doc.setStatus("ERROR");
                    doc.setErrorMsg("文档无有效文本内容");
                    doc.setChunkCount(0);
                    docRepo.save(doc);
                    return;
                }

                List<ChromaDBService.ChunkData> dataList = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    dataList.add(new ChromaDBService.ChunkData(
                            docId, i, doc.getFileName(), chunks.get(i)));
                }
                chromaDBService.addChunks(doc.getKbId(), dataList);

                doc.setStatus("READY");
                doc.setErrorMsg(null);
                doc.setChunkCount(chunks.size());
                docRepo.save(doc);
                kbRepo.incrementCounts(doc.getKbId(), 1, chunks.size(), doc.getFileSize());
            } catch (Exception e) {
                log.error("文档处理失败: docId={}", docId, e);
                doc.setStatus("ERROR");
                doc.setErrorMsg(e.getMessage());
                doc.setChunkCount(0);
                docRepo.save(doc);
            }
        });
    }

    private String parseDocument(Path filePath, String fileType) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        return switch (fileType) {
            case "txt", "md" -> new String(bytes, StandardCharsets.UTF_8);
            case "pdf" -> pdfParser.parse(bytes);
            default -> throw new RuntimeException("不支持的文件类型: " + fileType);
        };
    }

    private String getFileType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".md")) return "md";
        return "txt";
    }
}
