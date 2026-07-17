package com.example.aichat.service;

import com.example.aichat.model.PromptsHub;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * PromptsHub 动态查询 Specification 构建器
 * 用于社区浏览的多条件组合筛选，防 SQL 注入（参数绑定）
 */
public class PromptsHubSpecification {

    public static Specification<PromptsHub> browse(String category, String sort) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 只展示已发布
            predicates.add(cb.equal(root.get("status"), "published"));

            // 分类筛选
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            // 排序
            if ("newest".equals(sort)) {
                query.orderBy(cb.desc(root.get("createdAt")));
            } else if ("popular".equals(sort)) {
                query.orderBy(cb.desc(root.get("saveCount")));
            } else if ("rating".equals(sort)) {
                query.orderBy(cb.desc(root.get("avgRating")));
            } else {
                // 默认：点赞数降序
                query.orderBy(cb.desc(root.get("likesCount")), cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
