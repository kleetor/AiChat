package com.example.aichat.repository;

import com.example.aichat.model.UsageHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsageHistoryRepository extends JpaRepository<UsageHistory, Long> {

    /** 查询用户使用历史，按时间倒序 */
    Page<UsageHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 删除用户所有使用历史 */
    void deleteByUserId(Long userId);
}
