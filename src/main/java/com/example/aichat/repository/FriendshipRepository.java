package com.example.aichat.repository;

import com.example.aichat.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** 查找两个用户之间的好友关系（不限状态） */
    Optional<Friendship> findByUserIdAndFriendId(Long userId, Long friendId);

    /** 查找所有已接受的好友关系（查双方） */
    @Query("SELECT f FROM Friendship f WHERE f.status = 'ACCEPTED' AND (f.userId = :userId OR f.friendId = :userId)")
    List<Friendship> findAcceptedByUserId(@Param("userId") Long userId);

    /** 查找发给某用户的待处理好友申请 */
    @Query("SELECT f FROM Friendship f WHERE f.friendId = :userId AND f.status = 'PENDING'")
    List<Friendship> findPendingRequests(@Param("userId") Long userId);

    /** 检查是否已存在好友关系（不限状态） */
    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE f.status <> 'REJECTED' AND " +
           "((f.userId = :u1 AND f.friendId = :u2) OR (f.userId = :u2 AND f.friendId = :u1))")
    boolean existsActiveRelation(@Param("u1") Long u1, @Param("u2") Long u2);
}
