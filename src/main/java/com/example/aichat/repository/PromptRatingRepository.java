package com.example.aichat.repository;

import com.example.aichat.model.PromptRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromptRatingRepository extends JpaRepository<PromptRating, Long> {

    Optional<PromptRating> findByUserIdAndPromptId(Long userId, Long promptId);

    boolean existsByUserIdAndPromptId(Long userId, Long promptId);

    /** 重算某提示词的平均评分 */
    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM PromptRating r WHERE r.promptId = :promptId")
    double calcAvgRating(@Param("promptId") Long promptId);
}
