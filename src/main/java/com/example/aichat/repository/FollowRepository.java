package com.example.aichat.repository;

import com.example.aichat.model.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    /** 查询关注关系 */
    Optional<Follow> findByFollowerIdAndFollowedId(Long followerId, Long followedId);

    /** 查询用户关注列表 */
    Page<Follow> findByFollowerIdOrderByCreatedAtDesc(Long followerId, Pageable pageable);

    /** 查询用户粉丝列表 */
    Page<Follow> findByFollowedIdOrderByCreatedAtDesc(Long followedId, Pageable pageable);

    /** 检查是否已关注 */
    boolean existsByFollowerIdAndFollowedId(Long followerId, Long followedId);

    /** 取消关注 */
    void deleteByFollowerIdAndFollowedId(Long followerId, Long followedId);

    /** 统计关注数 */
    long countByFollowerId(Long followerId);

    /** 统计粉丝数 */
    long countByFollowedId(Long followedId);
}
