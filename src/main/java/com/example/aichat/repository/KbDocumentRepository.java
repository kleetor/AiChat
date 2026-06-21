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
}
