package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.KbDocument;
import com.example.aichat.model.KnowledgeBase;
import com.example.aichat.repository.KbDocumentRepository;
import com.example.aichat.repository.KnowledgeBaseRepository;
import com.example.aichat.service.parser.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepo;
    private final KbDocumentRepository docRepo;
    private final ChromaDBService chromaDBService;
    private final ChunkingService chunkingService;
    private final List<DocumentParser> parsers;
    private final TransactionTemplate transactionTemplate;
    private final CacheManager cacheManager;
    private final ThreadPoolTaskExecutor processExecutor;

    private static final Path UPLOAD_DIR = Paths.get("./uploads/kb");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final int MAX_CHUNKS_PER_DOC = 500;
    /** 单知识库最大文档数 */
    private static final int MAX_DOCS_PER_KB = 200;
    /** 单用户最大文档总数 */
    private static final int MAX_DOCS_PER_USER = 1000;
    /** 单用户最大存储占用（500MB） */
    private static final long MAX_STORAGE_PER_USER = 500L * 1024 * 1024;


    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                 KbDocumentRepository docRepo,
                                 ChromaDBService chromaDBService,
                                 ChunkingService chunkingService,
                                 List<DocumentParser> parsers,
                                 PlatformTransactionManager transactionManager,
                                 CacheManager cacheManager,
                                 @Qualifier("kbProcessExecutor") ThreadPoolTaskExecutor processExecutor) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chromaDBService = chromaDBService;
        this.chunkingService = chunkingService;
        this.parsers = parsers;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.cacheManager = cacheManager;
        this.processExecutor = processExecutor;
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
    @CacheEvict(value = "kbList", key = "#userId")
    public void delete(Long kbId, Long userId) {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权删除该知识库");
        }
        chromaDBService.deleteCollection(kbId);
        kbRepo.delete(kb);
    }

    /** 上传文档 */
    @CacheEvict(value = "kbList", key = "#userId", beforeInvocation = true)
    public KbDocument uploadDocument(Long kbId, Long userId, MultipartFile file) throws IOException {
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
            throw BusinessException.badRequest("文件大小不能超过 20MB");
        }
        // 配额检查
        checkQuotas(kbId, userId, file.getSize());

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
        processExecutor.submit(() -> processDocument(docId, targetPath));
        return doc;
    }

    /** 文档列表（不缓存，确保上传后状态实时可见） */
    public List<KbDocument> listDocuments(Long kbId, Long userId) {
        KnowledgeBase kb = kbRepo.findById(kbId)
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权查看该知识库文档");
        }
        List<KbDocument> docs = docRepo.findByKbId(kbId);
        log.debug("[缓存] listDocuments: kbId={}, 文档数={}", kbId, docs.size());
        return docs;
    }

    /** 删除文档 */
    @Transactional
    @CacheEvict(value = "kbList", key = "#userId")
    public void deleteDocument(Long docId, Long userId) {
        KbDocument doc = docRepo.findById(docId)
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
        KnowledgeBase kb = kbRepo.findById(doc.getKbId())
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权删除该文档");
        }
        chromaDBService.deleteByDocument(doc.getKbId(), doc.getId());
        docRepo.deleteById(doc.getId());
        kbRepo.decrementCounts(doc.getKbId(), 1, doc.getChunkCount(), doc.getFileSize());

        // 删除本地文件
        try { Files.deleteIfExists(UPLOAD_DIR.resolve(doc.getS3Key())); } catch (IOException ignored) {}
    }

    /** 重新索引文档 */
    @CacheEvict(value = "kbList", key = "#userId", beforeInvocation = true)
    public void reindex(Long docId, Long userId) {
        transactionTemplate.executeWithoutResult(status -> {
            KbDocument doc = docRepo.findById(docId)
                    .orElseThrow(() -> BusinessException.notFound("文档不存在"));
            KnowledgeBase kb = kbRepo.findById(doc.getKbId())
                    .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
            if (!kb.getUserId().equals(userId)) {
                throw BusinessException.forbidden("无权操作该文档");
            }

            // 删除旧向量索引
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
        processExecutor.submit(() -> processDocument(reDocId, filePath));
    }

    // ---------- 内部 ----------

    /**
     * 异步文档处理 — 使用 TransactionTemplate 确保每次 DB 操作在新事务中执行，
     * 避免游离实体导致的乐观锁冲突。
     */
    void processDocument(Long docId, Path filePath) {
        final Long[] kbIdHolder = new Long[1];
        final boolean[] success = {false};

        transactionTemplate.executeWithoutResult(status -> {
            KbDocument doc = docRepo.findById(docId).orElse(null);
            if (doc == null) {
                log.error("文档不存在: docId={}", docId);
                return;
            }
            kbIdHolder[0] = doc.getKbId();

            try {
                String text = parseDocument(filePath, doc.getFileType());
                // 读取知识库级别的分块配置
                KnowledgeBase kb = kbRepo.findById(doc.getKbId()).orElse(null);
                Integer chunkSize = kb != null ? kb.getChunkSize() : null;
                Integer chunkOverlap = kb != null ? kb.getChunkOverlap() : null;
                List<String> chunks = chunkingService.split(text, chunkSize, chunkOverlap);
                // 释放原始文本，后续只保留分块
                text = null;
                System.gc();

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
                int chunkCount = chunks.size();
                // 释放分块列表，只保留 dataList
                chunks = null;

                chromaDBService.addChunks(doc.getKbId(), dataList);
                // 索引完成后释放 dataList
                dataList = null;
                System.gc();

                doc.setStatus("READY");
                doc.setErrorMsg(null);
                doc.setChunkCount(chunkCount);
                docRepo.save(doc);
                kbRepo.incrementCounts(doc.getKbId(), 1, chunkCount, doc.getFileSize());
                success[0] = true;
            } catch (Exception e) {
                log.error("文档处理失败: docId={}", docId, e);
                doc.setStatus("ERROR");
                doc.setErrorMsg("文档处理失败，请重新上传");
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

    /** 异步任务中手动清除 kbList 缓存 */
    private void evictKbCache(Long kbId) {
        if (cacheManager == null) {
            log.warn("[缓存] cacheManager 为 null，无法清除缓存: kbId={}", kbId);
            return;
        }
        var listCache = cacheManager.getCache("kbList");
        if (listCache != null) {
            listCache.clear();
            log.info("[缓存] kbList 缓存已清除: kbId={}", kbId);
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

    private String getFileType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".csv")) return "txt"; // CSV 按文本处理
        if (lower.endsWith(".md")) return "md";
        return "txt";
    }

    /** 检查用户和知识库的存储配额 */
    private void checkQuotas(Long kbId, Long userId, long fileSize) {
        long kbCount = docRepo.countByKbId(kbId);
        if (kbCount >= MAX_DOCS_PER_KB) {
            throw BusinessException.badRequest(
                    "该知识库文档数已达上限 " + MAX_DOCS_PER_KB);
        }

        long userCount = docRepo.countByUserId(userId);
        if (userCount >= MAX_DOCS_PER_USER) {
            throw BusinessException.badRequest(
                    "您的文档总数已达上限 " + MAX_DOCS_PER_USER);
        }

        long userSize = docRepo.sumFileSizeByUserId(userId);
        if (userSize + fileSize > MAX_STORAGE_PER_USER) {
            throw BusinessException.badRequest(
                    "您的存储空间不足（已用 " + (userSize / 1024 / 1024) + "MB，上限 " +
                    (MAX_STORAGE_PER_USER / 1024 / 1024) + "MB）");
        }
    }
}
