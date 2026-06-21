package com.example.aichat.repository;

import com.example.aichat.model.PromptsHub;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PromptsHubRepository extends JpaRepository<PromptsHub, Long> {
    List<PromptsHub> findAllByOrderByLikesCountDescCreatedAtDesc();

    List<PromptsHub> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE PromptsHub p SET p.likesCount = p.likesCount + 1 WHERE p.id = :id")
    void incrementLikes(@Param("id") Long id);

    @Query("SELECT p FROM PromptsHub p WHERE p.name LIKE %:keyword% OR p.content LIKE %:keyword%")
    Page<PromptsHub> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    long countByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(p.likesCount), 0) FROM PromptsHub p WHERE p.userId = :userId")
    int sumLikesByUserId(@Param("userId") Long userId);
}