# 知识图谱优化计划

> 日期：2026-07-22
> 前置依赖：V9 知识图谱 + 时态管理
> 状态：**全部完成** (2026-07-22)

## 概览

V9 实现了基础的三元组提取和 1 跳图扩展。本计划涵盖六项渐进优化，提升图质量、遍历准确性和搜索召回率。

---

## 1. 关系去重与合并 ✅

**现状**：每次 LLM 提取到"张三-[工作于]->阿里"就在 `memory_relations` 插入一行。用户多次提到同一关系会生成重复边。

**目标**：插入前检查是否已有 `(subject_id, predicate, object_id)` 三元组，存在则仅更新 `valid_until` 和 `source_item_id`，不新增行。

**改动**：
- `MemoryRelationRepository` 新增 `findBySubjectIdAndPredicateAndObjectId()`
- `GraphMemoryService.saveRelation()`：插入前查重，存在则重新激活（`validUntil=null`）并更新 `sourceItemId`

**影响**：图更干净，减少冗余边，`valid_from`/`valid_until` 语义生效。

---

## 2. 关系层的时态管理 ✅

**现状**：`memory_relations` 有 `valid_from`/`valid_until` 字段但从未标记失效。旧关系"张三-[工作地点]->北京"和新关系"张三-[工作地点]->上海"同时活跃，图遍历产出矛盾结果。

**目标**：当 `memory_item` 被标记 `status=SUPERSEDED` 时，级联使其关联的关系失效。

**改动**：
- `MemoryRelationRepository` 新增 `expireBySourceItemId(itemId, now)`：批量设 `valid_until=now`
- `GraphMemoryService.expireRelations(Long itemId)`：封装调用
- `MemoryService.detectAndResolveTemporalConflict()`：标记 SUPERSEDED 后级联调用 `expireRelations()`

**影响**：图遍历只走当前有效的边。

---

## 3. 图遍历用于模式3搜索 ✅

**现状**：`EntityRetrievalService.searchByEntities()` 只做实体名匹配，不走图遍历。查询"我同事的项目"时，即使图中有 `用户-[同事]->李四-[负责]->项目X` 的路径，也无法召回。

**目标**：匹配到实体后，沿 `memory_relations` 扩展 1 跳，找邻接实体的关联记忆。

**改动**：
- `EntityRetrievalService` 注入 `MemoryRelationRepository`
- `searchByEntities()`：实体精确匹配后，沿出边/入边做 1 跳扩展，邻接实体的记忆以 0.5 权重参与打分

**影响**：模式3搜索召回率提升，关联信息不再遗漏。

---

## 4. LLM 三元组提取质量 ✅

**现状**：`extractTriples()` 的 prompt 较简单，无 few-shot 示例。类型推断用硬编码规则（名短 → PERSON，含"公司"→ ORG）。

**目标**：增强 prompt 加 few-shot 示例，实体类型交由 LLM 输出。

**改动**：
- `GraphMemoryService.extractTriples()`：prompt 增加 3 个中文三元组示例，输出格式扩展为 `主语|谓词|宾语|主语类型|宾语类型`
- 类型 LLM 优先，`inferType()` 作为 fallback
- 加校验：主语≠宾语、谓词长度 ≤ 50
- `Triple` record 扩展为 `(subject, predicate, object, subjectType, objectType)`

**影响**：实体分类更准确，减少格式错误的三元组。

---

## 5. 反向关系推断 ✅

**现状**：只存储正向关系。"张三-[工作于]->阿里"允许从张三遍历到阿里，但无法从阿里遍历到员工。

**目标**：维护高频谓词的反向映射表，插入正向边时自动追加反向边。

**改动**：
- `GraphMemoryService`：新增 `PREDICATE_INVERSES` 映射（6 对：工作于↔拥有员工、位于↔包含、毕业于↔培养、属于↔包含、任职于↔拥有员工、调往↔接收）
- `saveRelation()`：保存正向边后自动追加反向边，同样带去重逻辑
- 未加 `inferred` 列（计划标注为可选），反向边与正向边无区分标记

**影响**：图遍历方向完整，无需全表扫描 `object_id`。

---

## 6. 实体消歧 ✅

**现状**："张三"和"小张"是两个独立实体，导致子图不连通。

**目标**：LLM 定期扫描，判定可合并的实体对。

**改动**：
- `MemoryRelationRepository` 新增 `updateSubjectId(fromId, toId)` 和 `updateObjectId(fromId, toId)`
- `MemoryItemEntityRepository` 新增 `deleteConflicting(fromId, toId)`（删除唯一约束冲突行）和 `updateEntityId(fromId, toId)`
- `GraphMemoryService.suggestMerges(userId)`：获取用户所有实体，LLM 识别同人异名/简称全称候选对，返回 `List<MergeCandidate>`
- `GraphMemoryService.mergeEntities(fromId, toId)`：`@Transactional` 事务内原子执行：删冲突关联 → 转移剩余关联 → 更新关系 → 删源实体
- 可通过管理端点或定时任务触发

**影响**：图连通性提升。建议实体数超过 5000 后再启用自动合并。

---

## 实施状态

| # | 优化项 | 影响 | 复杂度 | 阶段 | 状态 |
|---|--------|------|--------|------|------|
| 1 | 关系去重与合并 | 减少冗余 | 低 | Phase 1 | ✅ 已完成 |
| 2 | 关系层时态管理 | 正确性 | 中 | Phase 1 | ✅ 已完成 |
| 4 | LLM 提取质量 | 准确率 | 低 | Phase 1 | ✅ 已完成 |
| 3 | 图遍历用于模式3 | 搜索召回 | 中 | Phase 2 | ✅ 已完成 |
| 5 | 反向关系推断 | 遍历完整性 | 中 | Phase 3 | ✅ 已完成 |
| 6 | 实体消歧 | 图连通性 | 高 | 延后 | ✅ 已完成 |

## 涉及文件

| 文件 | 改动 |
|------|------|
| `MemoryEntityRepository.java` | `findByUserId(userId)` |
| `MemoryRelationRepository.java` | `findBySubjectIdAndPredicateAndObjectId()`, `expireBySourceItemId()`, `updateSubjectId()`, `updateObjectId()` |
| `MemoryItemEntityRepository.java` | `deleteConflicting()`, `updateEntityId()` |
| `GraphMemoryService.java` | `saveRelation()` 去重+反向边, `extractTriples()` 增强 prompt+类型, `expireRelations()`, `suggestMerges()`, `mergeEntities()`, `PREDICATE_INVERSES`, `MergeCandidate` record |
| `EntityRetrievalService.java` | 注入 `MemoryRelationRepository`, `searchByEntities()` 图扩展 |
| `MemoryService.java` | `detectAndResolveTemporalConflict()` 级联 `expireRelations()` |
