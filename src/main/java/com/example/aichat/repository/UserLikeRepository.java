package com.example.aichat.repository;

import com.example.aichat.model.UserLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface UserLikeRepository extends JpaRepository<UserLike, Long> {

    /** 某用户在某天的点赞数（按类型） */
    @Query("SELECT COUNT(l) FROM UserLike l WHERE l.userId = :userId AND l.targetType = :type AND l.createdAt >= :since")
    long countTodayByUserAndType(@Param("userId") Long userId,
                                  @Param("type") String type,
                                  @Param("since") LocalDateTime since);

    /** 检查是否已点赞同一目标 */
    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);
}
