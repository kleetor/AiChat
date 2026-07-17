---
name: colorspace
description: 基于世界顶级品牌设计系统生成网页。内置 67 个知名品牌（Stripe、Notion、Apple、Tesla、Vercel 等）
  的完整设计系统，根据网页类型智能推荐配色风格，使用品牌级设计规范生成高质量网页。
  触发词：做网站、写网页、建站、创建页面、设计网页、做官网、产品落地页、landing page、website、
  webpage、homepage、build a website、create a web page、design a landing page、make a homepage
  Trigger on:：帮我做一个落地页, 做个首页, 产品展示页, 活动页,
  营销页, 官网首页, landing page, product page, promo page, make me a homepage, build a product
  showcase, create a campaign page. Workflow:asks one question about the product, then opens a live
  browser preview of 57 city-inspired visual styles to click-select, lets the user pick
  typography/nav/color tone/hero variant/features variant/page sections interactively, then outputs
  a complete multi-file site (index.html + style.css + main.js + SVG sprite) with real product copy.
---

# Brand Design Studio

基于 67 个世界顶级品牌的完整设计系统，为你的网页注入品牌级视觉品质。

## 品牌设计库

所有品牌设计系统文件位于：
`{skill_dir}\design-md\`

每个品牌子文件夹包含：
- `DESIGN.md` — 完整的 9 章节设计系统文档（配色、字体、组件、布局、阴影、响应式、AI 提示指南等）
- `preview.html` — 亮色模式交互式预览
- `preview-dark.html` — 暗色模式交互式预览

## 工作流程

### Step 1：理解需求

当用户要求创建网页/网站时，首先确认：
- 网页类型（SaaS 产品页、AI 产品页、电商页、博客、企业官网、活动页等）
- 网页用途和目标受众
- 是否有暗色模式需求

如果用户已明确说明网页类型，直接进入 Step 2。如果未明确，使用 AskUserQuestion 工具询问。

### Step 2：推荐品牌风格

1. 读取本 Skill 的 `references/brand-index.md` 获取品牌分类索引
2. 根据用户描述的网页类型，匹配最相关的 1-2 个分类
3. 从匹配分类中精选 **3-5 个品牌**，确保风格多样性：
   - 至少包含一个亮色风格和一个暗色风格
   - 包含不同设计语言的品牌（极简 vs 活泼 vs 奢华等）
4. 使用 AskUserQuestion 工具向用户展示推荐，每个选项包含：品牌名 + 风格关键词 + 主色调
5. 同时提供"自定义选择其他品牌"选项，告知用户可从全部 67 个品牌中选择

### Step 3：用户选择

用户可以：
- **a)** 选择推荐的品牌之一
- **b)** 指定其他品牌（从 67 个中选，可展示对应分类的全部品牌供选择）
- **c)** 描述想要的自定义风格（此时仍可参考品牌设计系统作为灵感来源）

### Step 4：读取设计系统

确认品牌后，读取对应的 DESIGN.md 文件：
`e:\系统文件\桌面\新建文件夹 (2)\design-md\{品牌名}\DESIGN.md`

**章节读取优先级：**

**必读（核心规范）：**
1. **第 9 章 Agent Prompt Guide** — 最重要！包含现成的组件提示词示例，直接指导如何用该品牌风格生成具体组件
2. **第 2 章 Color Palette & Roles** — 完整的配色方案：主色、强调色、中性色阶、交互色、表面色、阴影色，含精确 HEX/RGBA 值
3. **第 3 章 Typography Rules** — 字体族、字号层级表（Role/Size/Weight/Line Height/Letter Spacing）、排版原则

**重要参考：**
4. **第 4 章 Component Stylings** — 按钮、卡片、输入框、导航等组件的具体 CSS 属性值
5. **第 7 章 Do's and Don'ts** — 该品牌的设计最佳实践和禁忌

**补充参考（按需）：**
6. **第 1 章 Visual Theme & Atmosphere** — 整体视觉风格理解
7. **第 5 章 Layout Principles** — 间距系统、网格、留白哲学、圆角刻度
8. **第 6 章 Depth & Elevation** — 阴影层级系统
9. **第 8 章 Responsive Behavior** — 响应式断点、折叠策略

> **注意**：DESIGN.md 文件较大，按优先级读取所需章节，不必一次性读取全部内容。优先读取第 9、2、3、4 章。

### Step 5：生成网页

严格按照 DESIGN.md 中的设计规范生成网页代码。

## 代码生成规则

### 配色规范
- **MUST** 使用 DESIGN.md 中定义的精确色值（HEX/RGBA），不得自行调整或替换
- **MUST** 将品牌核心色值定义为 CSS 自定义属性（`:root` 变量），变量命名参考 DESIGN.md 中的 token 名称
- 示例：`:root { --color-primary: #533afd; --color-heading: #061b31; --color-bg: #ffffff; }`

### 排版规范
- **MUST** 严格按照排版层级表中的字号、字重、行高、字距
- **MUST** 使用指定的字体族和完整 fallback 链
- 如果 DESIGN.md 指定了自定义字体（如 sohne-var、Geist、NotionInter），在 CSS 中使用该字体名并提供系统字体 fallback
- 对于 Google Fonts 可获取的字体（如 Inter、IBM Plex），添加 `@import` 或 `<link>` 引入

### 组件规范
- **MUST** 遵循组件样式中定义的 border-radius、padding、shadow、hover/active 状态
- **MUST** 实现第 4 章中描述的按钮变体（Primary、Ghost、Pill 等）
- **MUST** 遵循第 7 章 Do's and Don'ts 中的所有规则

### 布局与响应式
- **MUST** 遵循第 5 章的间距系统和网格规范
- **MUST** 遵循第 8 章中定义的断点和折叠策略
- **MUST** 确保移动端可用性

### 暗色模式
- 如果用户需要暗色模式，参考 `preview-dark.html` 的配色方案
- 或基于 DESIGN.md 中的深色变体色值推导暗色模式

### 第 9 章 Agent Prompt Guide 的使用
- 第 9 章包含 **Example Component Prompts**，这些是可直接使用的组件生成提示词
- 生成具体组件时，优先参考这些示例提示词的写法和风格
- 第 9 章还包含 **Iteration Guide**，用于迭代优化生成结果

## 品牌分类速查

完整品牌分类索引见 `references/brand-index.md`，共 6 大类：

| 分类 | 适合场景 | 代表品牌 |
|------|---------|---------|
| SaaS & 开发者工具 | 产品官网、工具落地页、API 展示页 | Stripe、Notion、Linear、Figma、Vercel |
| AI & 前沿科技 | AI 产品页、科技展示页 | Claude、Cursor、x.ai、Mistral、Replicate |
| 金融 & 交易平台 | 金融产品页、预订系统 | Coinbase、Kraken、Revolut、Airbnb |
| 内容 & 社交媒体 | 内容平台、媒体网站 | Spotify、Pinterest、Intercom、Zapier |
| 高端 & 汽车品牌 | 奢侈品展示、品牌官网 | Tesla、Apple、BMW、Ferrari、Nvidia |
| 创意 & 设计平台 | 作品集、艺术类网站 | Clay、Expo、IBM |

## 注意事项

- 如果用户明确要求不使用品牌风格，则不强制推荐，按常规方式生成
- 品牌设计系统来自 [VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md) 开源项目，为非官方提取的设计规范
- 该 ColorSpace Skill 由汤圆制作
