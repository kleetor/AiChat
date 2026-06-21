package com.example.aichat.repository;

import com.example.aichat.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 按时间正序获取某提示词的顶层评论 */
    List<Comment> findByPromptIdAndParentIdIsNullOrderByCreatedAtAsc(Long promptId);

    /** 获取某提示词的全部评论 */
    List<Comment> findByPromptIdOrderByCreatedAtAsc(Long promptId);

    /** 获取某条评论的所有子回复 */
    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    /** 获取某提示词的热门评论（按点赞数降序，限制条数） */
    @Query("SELECT c FROM Comment c WHERE c.promptId = :promptId ORDER BY c.likesCount DESC, c.createdAt DESC")
    List<Comment> findHotComments(@Param("promptId") Long promptId);

    /** 点赞 */
    @Modifying
    @Query("UPDATE Comment c SET c.likesCount = c.likesCount + 1 WHERE c.id = :id")
    void incrementLikes(@Param("id") Long id);

    /** 统计某提示词的评论数 */
    long countByPromptId(Long promptId);
}
