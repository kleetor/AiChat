# Frontend 代码修复计划

## 概述

基于代码审查结果，制定以下修复计划。按严重度排序，修复范围覆盖 8 个可修复问题。

---

## 修复清单

| # | 严重度 | 文件 | 问题 | 修复方案 |
|---|--------|------|------|---------|
| 1 | Critical | ModelSelector.tsx:25 | `options.find()` 非空断言崩溃 | 添加 fallback 到 `options[0]`，无结果时返回 null |
| 2 | High | App.tsx | 关键操作静默吞错 | 添加全局 Toast 通知机制，发送消息/创建会话失败时提示 |
| 3 | Medium | api.ts:140 | SSE `[DONE]` 解析大小写敏感 | `trim().toUpperCase()` 处理 |
| 4 | Medium | ChatMessages.tsx:51 | 魔数 `1000000` 判断临时消息 | 引入 `isLocalMessage` 标记字段 |
| 5 | Medium | InputBar.tsx:39 | 字数超限阈值 3800 vs maxLength 4000 | 将阈值改为 `maxLength - 200` 或直接使用 maxLength |
| 6 | Medium | SettingsModal.tsx:278-282 | 密码验证可跳过 | 移除 fallback 逻辑，强制要求 onVerifyPassword |
| 7 | Low | FriendModal.tsx:49 | 搜索无防抖 | 添加 300ms useDebounce |
| 8 | Low | index.html:2,7 | lang="en" / title="frontend" | 改为 lang="zh-CN" / title="AiChat" |

---

## 不修复项及原因

| 问题 | 原因 |
|------|------|
| App.tsx 上帝组件 (561行) | 重构为自定义 hooks 需要大规模改动，风险高，建议后续迭代单独处理 |
| Header 手写 Switch 不一致 | Radix Switch 在该场景下尺寸适配有问题，手写更灵活，保留现状 |

---

## 实施步骤

1. 修复 index.html — 低风险
2. 修复 ModelSelector 空值崩溃 — 关键
3. 修复 SSE 解析 — 简单
4. 修复 ChatMessages 魔数 — 中等
5. 修复 InputBar 字数阈值 — 简单
6. 添加 Toast 通知机制 — 涉及多个文件
7. 修复密码验证逻辑 — 中等
8. 修复 FriendModal 防抖 — 简单

---

## 新增文件

- `src/lib/toast.tsx` — 全局 Toast 通知组件 + Context
- `main.tsx` — 引入 ToastProvider
