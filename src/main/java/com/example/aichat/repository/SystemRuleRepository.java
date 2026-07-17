package com.example.aichat.repository;

import com.example.aichat.model.SystemRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemRuleRepository extends JpaRepository<SystemRule, Long> {

    /** 查询所有启用的规则，按 sort_order 升序 */
    List<SystemRule> findByIsActiveTrueOrderBySortOrderAsc();
}
