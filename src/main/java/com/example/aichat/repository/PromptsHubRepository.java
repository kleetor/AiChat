package com.example.aichat.repository;

import com.example.aichat.model.PromptsHub;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PromptsHubRepository extends JpaRepository<PromptsHub, Long>,
        JpaSpecificationExecutor<PromptsHub> {

    List<PromptsHub> findAllByOrderByLikesCountDescCreatedAtDesc();

    List<PromptsHub> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 按用户+状态查询 */
    Page<PromptsHub> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status, Pageable pageable);

    /** 按用户查询（不分状态） */
    Page<PromptsHub> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 精选列表 */
    List<PromptsHub> findByFeaturedTrueOrderByCreatedAtDesc();

    /** 精选分页 */
    Page<PromptsHub> findByFeaturedTrueOrderByCreatedAtDesc(Pageable pageable);

    /** 按状态查询（审核队列） */
    Page<PromptsHub> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    @Modifying
    @Query("UPDATE PromptsHub p SET p.likesCount = p.likesCount + 1 WHERE p.id = :id")
    void incrementLikes(@Param("id") Long id);

    @Query("SELECT p FROM PromptsHub p WHERE (p.name LIKE %:keyword% OR p.content LIKE %:keyword%) AND p.status <> 'removed'")
    Page<PromptsHub> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /** 排除指定状态的提示词（管理员社区管理用，排除已下架的） */
    Page<PromptsHub> findByStatusNot(String status, Pageable pageable);

    /** FULLTEXT 搜索 */
    @Query(value = "SELECT * FROM prompts_hub WHERE MATCH(name, description, content) AGAINST(:q IN BOOLEAN MODE)",
           countQuery = "SELECT COUNT(*) FROM prompts_hub WHERE MATCH(name, description, content) AGAINST(:q IN BOOLEAN MODE)",
           nativeQuery = true)
    Page<PromptsHub> searchFulltext(@Param("q") String query, Pageable pageable);

    /** FULLTEXT 搜索 + 分类筛选 */
    @Query(value = "SELECT * FROM prompts_hub WHERE MATCH(name, description, content) AGAINST(:q IN BOOLEAN MODE) AND category = :category",
           countQuery = "SELECT COUNT(*) FROM prompts_hub WHERE MATCH(name, description, content) AGAINST(:q IN BOOLEAN MODE) AND category = :category",
           nativeQuery = true)
    Page<PromptsHub> searchFulltextByCategory(@Param("q") String query, @Param("category") String category, Pageable pageable);

    long countByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(p.likesCount), 0) FROM PromptsHub p WHERE p.userId = :userId")
    int sumLikesByUserId(@Param("userId") Long userId);

    /** 浏览量 +1 */
    @Modifying
    @Query("UPDATE PromptsHub p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /** 收藏量 +1 */
    @Modifying
    @Query("UPDATE PromptsHub p SET p.saveCount = p.saveCount + 1 WHERE p.id = :id")
    void incrementSaveCount(@Param("id") Long id);

    /** 收藏量 -1 */
    @Modifying
    @Query("UPDATE PromptsHub p SET p.saveCount = p.saveCount - 1 WHERE p.id = :id AND p.saveCount > 0")
    void decrementSaveCount(@Param("id") Long id);

    /** 从 status 改为 removed */
    @Modifying
    @Query("UPDATE PromptsHub p SET p.status = 'removed' WHERE p.id = :id AND p.userId = :userId")
    int removeByUser(@Param("id") Long id, @Param("userId") Long userId);

    /** 获取所有不同的分类 */
    @Query("SELECT DISTINCT p.category FROM PromptsHub p WHERE p.category IS NOT NULL AND p.status = 'published'")
    List<String> findDistinctCategories();
}
