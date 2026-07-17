package com.example.aichat.repository;

import com.example.aichat.model.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /** 查询用户是否已收藏某提示词 */
    Optional<Favorite> findByUserIdAndPromptId(Long userId, Long promptId);

    /** 查询用户收藏列表 */
    Page<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 统计某提示词被收藏数 */
    long countByPromptId(Long promptId);

    /** 删除用户收藏 */
    void deleteByUserIdAndPromptId(Long userId, Long promptId);

    /** 检查是否已收藏 */
    boolean existsByUserIdAndPromptId(Long userId, Long promptId);
}
