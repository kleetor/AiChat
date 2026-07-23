package com.example.aichat.repository;

import com.example.aichat.model.KbDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {
    List<KbDocument> findByKbId(Long kbId);

    @Query("SELECT COUNT(d) FROM KbDocument d WHERE d.kbId = :kbId")
    long countByKbId(@Param("kbId") Long kbId);

    @Query("SELECT COALESCE(SUM(d.chunkCount), 0) FROM KbDocument d WHERE d.kbId = :kbId")
    long sumChunkCountByKbId(@Param("kbId") Long kbId);

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM KbDocument d WHERE d.kbId = :kbId")
    long sumFileSizeByKbId(@Param("kbId") Long kbId);

    /** 用户在所有知识库中的文档总数 */
    @Query("SELECT COUNT(d) FROM KbDocument d JOIN KnowledgeBase k ON d.kbId = k.id WHERE k.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    /** 用户在所有知识库中的总存储占用 */
    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM KbDocument d JOIN KnowledgeBase k ON d.kbId = k.id WHERE k.userId = :userId")
    long sumFileSizeByUserId(@Param("userId") Long userId);

    /**
     * 单次查询返回所有知识库的统计信息（count, chunkCount, fileSize），消除 N+1。
     */
    @Query("SELECT d.kbId AS kbId, COUNT(d) AS docCount, " +
           "COALESCE(SUM(d.chunkCount), 0) AS chunkCount, " +
           "COALESCE(SUM(d.fileSize), 0) AS totalSize " +
           "FROM KbDocument d WHERE d.kbId IN :kbIds GROUP BY d.kbId")
    List<Object[]> aggregateByKbIds(@Param("kbIds") List<Long> kbIds);
}
