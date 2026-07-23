package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.KbDocument;
import com.example.aichat.model.KnowledgeBase;
import com.example.aichat.repository.KbDocumentRepository;
import com.example.aichat.repository.KnowledgeBaseRepository;
import com.example.aichat.service.parser.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepo;
    private final KbDocumentRepository docRepo;
    private final ChromaDBService chromaDBService;
    private final KbBm25IndexService bm25Service;
    private final ChunkingService chunkingService;
    private final List<DocumentParser> parsers;
    private final TransactionTemplate transactionTemplate;
    private final CacheManager cacheManager;

    private static final Path UPLOAD_DIR = Paths.get("./uploads/kb");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final int MAX_CHUNKS_PER_DOC = 500;
    /** 上传级强制 OCR 标志：docId → forceOcr */
    private final ConcurrentHashMap<Long, Boolean> forceOcrFlags = new ConcurrentHashMap<>();

    /** 文件头 Magic Bytes 校验：扩展名 → 预期文件头 */
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "pdf", new byte[]{0x25, 0x50, 0x44, 0x46},               // %PDF
            "docx", new byte[]{0x50, 0x4B, 0x03, 0x04},              // PK..
            "xlsx", new byte[]{0x50, 0x4B, 0x03, 0x04},              // PK..
            "pptx", new byte[]{0x50, 0x4B, 0x03, 0x04},              // PK..
            "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},        // .PNG
            "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF} // ..Ø.
    );

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                 KbDocumentRepository docRepo,
                                 ChromaDBService chromaDBService,
                                 KbBm25IndexService bm25Service,
                                 ChunkingService chunkingService,
                                 List<DocumentParser> parsers,
                                 PlatformTransactionManager transactionManager,
                                 CacheManager cacheManager) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chromaDBService = chromaDBService;
        this.bm25Service = bm25Service;
        this.chunkingService = chunkingService;
        this.parsers = parsers;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.cacheManager = cacheManager;
        try { Files.createDirectories(UPLOAD_DIR); } catch (IOException ignored) {}
    }

    /** 创建知识库 + ChromaDB Collection */
    @Transactional
    @CacheEvict(value = "kbList", key = "#userId")
    public KnowledgeBase create(String name, String description, String promptTemplate,
                                 Integer chunkSize, Integer chunkOverlap, Long userId) {
        KnowledgeBase kb = kbRepo.save(KnowledgeBase.builder()
                .name(name).description(description)
                .promptTemplate(promptTemplate)
                .chunkSize(chunkSize).chunkOverlap(chunkOverlap)
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
    @Cacheable(value = "kbList", key = "#userId")
    public List<KnowledgeBase> listByUser(Long userId) {
        List<KnowledgeBase> kbs = kbRepo.findByUserId(userId);
        if (kbs.isEmpty()) return kbs;

        // 单次 GROUP BY 聚合查询，消除 N+1
        List<Long> kbIds = kbs.stream().map(KnowledgeBase::getId).toList();
        List<Object[]> stats = docRepo.aggregateByKbIds(kbIds);
        Map<Long, Object[]> statsMap = new HashMap<>();
        for (Object[] row : stats) {
            statsMap.put((Long) row[0], row);
        }
        for (KnowledgeBase kb : kbs) {
            Object[] s = statsMap.get(kb.getId());
            if (s != null) {
                kb.setDocCount(((Number) s[1]).intValue());
                kb.setChunkCount(((Number) s[2]).intValue());
                kb.setTotalSize(((Number) s[3]).longValue());
            } else {
                kb.setDocCount(0);
                kb.setChunkCount(0);
                kb.setTotalSize(0L);
            }
        }
        return kbs;
    }

    /** 编辑知识库 */
    @Transactional
    @CacheEvict(value = "kbList", key = "#userId")
    public KnowledgeBase update(Long kbId, Long userId, String name, String description,
                                 String promptTemplate, Integer chunkSize, Integer chunkOverlap) {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权修改该知识库");
        }
        if (name != null) kb.setName(name);
        if (description != null) kb.setDescription(description);
        kb.setPromptTemplate(promptTemplate);
        kb.setChunkSize(chunkSize);
        kb.setChunkOverlap(chunkOverlap);
        kb.setUpdatedAt(LocalDateTime.now());
        return kbRepo.save(kb);
    }

    /** 删除知识库 */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "kbList", key = "#userId"),
        @CacheEvict(value = "kbDocs", allEntries = true)
    })
    public void delete(Long kbId, Long userId) {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权删除该知识库");
        }
        chromaDBService.deleteCollection(kbId);
        bm25Service.deleteIndex(kbId);
        kbRepo.delete(kb);
    }

    /** 上传文档 */
    @Caching(evict = {
        @CacheEvict(value = "kbList", key = "#userId"),
        @CacheEvict(value = "kbDocs", key = "#userId + '_' + #kbId")
    })
    public KbDocument uploadDocument(Long kbId, Long userId, MultipartFile file, boolean forceOcr) throws IOException {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权上传文档到该知识库");
        }

        // 保存文件到本地 (剥离路径，防止路径穿越)
        final String rawName = file.getOriginalFilename();
        final String fileName = rawName != null
                ? java.nio.file.Paths.get(rawName).getFileName().toString()
                : "unknown";
        final String fileType = getFileType(fileName);
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("文件大小不能超过 10MB");
        }
        // Magic Bytes 校验：防止文件类型伪装
        validateFileType(file, fileType);
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

        // 存储上传级 OCR 配置
        if (forceOcr && "pdf".equals(fileType)) {
            forceOcrFlags.put(doc.getId(), true);
            log.info("文档 {} 启用强制 OCR 模式", doc.getId());
        }

        // 异步处理（只传 docId，避免游离实体冲突）
        final Long docId = doc.getId();
        CompletableFuture.runAsync(() -> processDocument(docId, targetPath));
        return doc;
    }

    /** 文档列表 */
    @Cacheable(value = "kbDocs", key = "#userId + '_' + #kbId")
    public List<KbDocument> listDocuments(Long kbId, Long userId) {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权查看该知识库文档");
        }
        return docRepo.findByKbId(kbId);
    }

    /** 删除文档 */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "kbList", key = "#userId"),
        @CacheEvict(value = "kbDocs", allEntries = true)
    })
    public void deleteDocument(Long docId, Long userId) {
        KbDocument doc = docRepo.findById(docId)
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
        KnowledgeBase kb = kbRepo.findById(doc.getKbId())
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权删除该文档");
        }
        chromaDBService.deleteByDocument(doc.getKbId(), doc.getId());
        bm25Service.removeByDocument(doc.getKbId(), doc.getId());
        docRepo.deleteById(doc.getId());
        kbRepo.decrementCounts(doc.getKbId(), 1, doc.getChunkCount(), doc.getFileSize());

        // 删除本地文件
        try { Files.deleteIfExists(UPLOAD_DIR.resolve(doc.getS3Key())); } catch (IOException ignored) {}
    }

    /** 重新索引文档 */
    @Caching(evict = {
        @CacheEvict(value = "kbList", key = "#userId"),
        @CacheEvict(value = "kbDocs", allEntries = true)
    })
    public void reindex(Long docId, Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            KbDocument doc = docRepo.findById(docId)
                    .orElseThrow(() -> BusinessException.notFound("文档不存在"));
            KnowledgeBase kb = kbRepo.findById(doc.getKbId())
                    .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
            if (!kb.getUserId().equals(userId)) {
                throw BusinessException.forbidden("无权操作该文档");
            }

            // 删除旧向量和 BM25 索引
            chromaDBService.deleteByDocument(doc.getKbId(), doc.getId());
            bm25Service.removeByDocument(doc.getKbId(), doc.getId());
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
        final Long[] kbIdHolder = new Long[1];
        final boolean[] success = {false};

        // 读取并清除上传级 OCR 标志
        final Boolean forceOcr = forceOcrFlags.remove(docId);

        transactionTemplate.executeWithoutResult(status -> {
            KbDocument doc = docRepo.findById(docId).orElse(null);
            if (doc == null) {
                log.error("文档不存在: docId={}", docId);
                return;
            }
            kbIdHolder[0] = doc.getKbId();

            try {
                // 对 PDF 设置强制 OCR 标志
                if (Boolean.TRUE.equals(forceOcr) && "pdf".equals(doc.getFileType())) {
                    setPdfForceOcr(true);
                }
                String text = parseDocument(filePath, doc.getFileType());
                // 读取知识库级别的分块配置
                KnowledgeBase kb = kbRepo.findById(doc.getKbId()).orElse(null);
                Integer chunkSize = kb != null ? kb.getChunkSize() : null;
                Integer chunkOverlap = kb != null ? kb.getChunkOverlap() : null;
                List<String> chunks = chunkingService.split(text, chunkSize, chunkOverlap);
                if (chunks.isEmpty()) {
                    doc.setStatus("ERROR");
                    doc.setErrorMsg("文档无有效文本内容");
                    doc.setChunkCount(0);
                    docRepo.save(doc);
                    return;
                }
                if (chunks.size() > MAX_CHUNKS_PER_DOC) {
                    doc.setStatus("ERROR");
                    doc.setErrorMsg("文档分块数 " + chunks.size() + " 超过上限 " + MAX_CHUNKS_PER_DOC);
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
                // 同步写入 BM25 关键词索引
                bm25Service.indexChunks(doc.getKbId(), dataList);

                doc.setStatus("READY");
                doc.setErrorMsg(null);
                doc.setChunkCount(chunks.size());
                docRepo.save(doc);
                kbRepo.incrementCounts(doc.getKbId(), 1, chunks.size(), doc.getFileSize());
                success[0] = true;
            } catch (Exception e) {
                log.error("文档处理失败: docId={}", docId, e);
                doc.setStatus("ERROR");
                doc.setErrorMsg(e.getMessage());
                doc.setChunkCount(0);
                docRepo.save(doc);
            }
        });
        // 事务提交后再清除缓存，避免竞态
        if (kbIdHolder[0] != null) {
            log.info("文档处理完成，清除缓存: docId={}, kbId={}, success={}", docId, kbIdHolder[0], success[0]);
            evictKbCache(kbIdHolder[0]);
        }
    }

    /** 异步任务中手动清除 kbList 和 kbDocs 缓存 */
    private void evictKbCache(Long kbId) {
        try {
            CacheManager cm = cacheManager;
            if (cm != null) {
                // 清除该知识库的文档列表缓存（所有用户的）
                var docsCache = cm.getCache("kbDocs");
                if (docsCache != null) docsCache.clear();
                // 清除知识库列表缓存（所有用户的）
                var listCache = cm.getCache("kbList");
                if (listCache != null) listCache.clear();
            }
        } catch (Exception e) {
            log.warn("缓存清除失败: kbId={}", kbId, e);
        }
    }

    private String parseDocument(Path filePath, String fileType) throws IOException {
        for (var p : parsers) {
            if (p.supports(fileType)) {
                return p.parse(filePath);
            }
        }
        throw new RuntimeException("不支持的文件类型: " + fileType);
    }

    /** 为当前线程的 PdfParser 设置强制 OCR 标志 */
    private void setPdfForceOcr(boolean force) {
        for (var p : parsers) {
            if (p instanceof PdfParser) {
                ((PdfParser) p).setForceOcrForCurrentThread(force);
                return;
            }
        }
    }

    private String getFileType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".xlsx")) return "xlsx";
        if (lower.endsWith(".pptx")) return "pptx";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".md")) return "md";
        if (lower.endsWith(".csv")) return "txt"; // CSV 按文本处理
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpg";
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "tiff";
        if (lower.endsWith(".bmp")) return "bmp";
        return "txt";
    }

    /** 验证文件头 Magic Bytes 是否与扩展名匹配 */
    private void validateFileType(MultipartFile file, String fileType) throws IOException {
        byte[] expected = MAGIC_BYTES.get(fileType);
        if (expected == null) return; // 无校验规则，放行

        byte[] header = new byte[expected.length];
        int read = file.getInputStream().read(header);
        if (read < expected.length) {
            throw BusinessException.badRequest("文件内容过短，无法验证类型");
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) {
                throw BusinessException.badRequest(
                        "文件类型不匹配：扩展名为 ." + fileType + " 但文件头不符合");
            }
        }
        log.debug("Magic Bytes 校验通过: fileType={}", fileType);
    }
}
