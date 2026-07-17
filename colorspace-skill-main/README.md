# ColorSpace

基于世界顶级品牌设计规范的网页生成 Skill。

内置 58 个知名品牌（Stripe、Notion、Apple、Tesla、Vercel 等）的完整设计系统，根据网页类型智能推荐配色风格，使用品牌级设计规范生成高质量网页。

## 工作原理

```
说需求 → 推荐品牌风格 → 选择品牌 → 读取设计规范 → 生成网页
```

1. 你告诉 AI 要做什么类型的网页
2. Skill 根据网页类型推荐 3-5 个适合的品牌风格
3. 你选一个品牌（也可以从 58 个里自选）
4. AI 读取该品牌的完整设计规范（配色、字体、组件等）
5. 按规范生成网页代码

## 支持的品牌（58 个）

按 6 大类整理：

| 分类 | 适合场景 | 品牌 |
|------|---------|------|
| SaaS & 开发者工具 | 产品官网、工具落地页 | Stripe、Notion、Linear、Figma、Vercel、Supabase、Sentry... |
| AI & 前沿科技 | AI 产品页、科技展示页 | Claude、Cursor、x.ai、Mistral、Replicate、ElevenLabs... |
| 金融 & 交易平台 | 金融产品页、预订系统 | Coinbase、Kraken、Revolut、Airbnb、Uber、SpaceX |
| 内容 & 社交媒体 | 内容平台、媒体网站 | Spotify、Pinterest、Intercom、Zapier、Miro |
| 高端 & 汽车品牌 | 奢侈品展示、品牌官网 | Tesla、Apple、BMW、Ferrari、Lamborghini、Nvidia |
| 创意 & 设计平台 | 作品集、艺术类网站 | Clay、Expo、IBM |

## 文件结构

```
colorspace/
├── SKILL.md                  # Skill 定义文件（工作流 + 规则）
├── design-md/                # 58 个品牌设计系统
│   ├── stripe/
│   │   ├── DESIGN.md         # 9 章节完整设计规范
│   │   ├── preview.html      # 亮色预览
│   │   ├── preview-dark.html # 暗色预览
│   │   └── README.md
│   ├── notion/
│   ├── vercel/
│   └── ...
└── references/
    └── brand-index.md        # 品牌分类索引
```

每个品牌的 DESIGN.md 包含 9 个章节：

1. Visual Theme & Atmosphere — 视觉主题与氛围
2. Color Palette & Roles — 配色方案（精确 HEX/RGBA 值）
3. Typography Rules — 排版规则（字体、字号、字重、行高、字距）
4. Component Stylings — 组件样式（按钮、卡片、输入框、导航）
5. Layout Principles — 布局原则（间距、网格、圆角）
6. Depth & Elevation — 阴影与层级系统
7. Do's and Don'ts — 设计最佳实践与禁忌
8. Responsive Behavior — 响应式断点与折叠策略
9. Agent Prompt Guide — AI 生成提示词指南

## 使用方式

安装后在 SOLO 中直接说你要做什么网页即可触发：

- "帮我做一个 SaaS 产品官网"
- "设计一个 AI 工具的落地页"
- "create a portfolio website"
- "帮我做个博客"

## 数据来源

品牌设计系统来自 [VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md) 开源项目，为非官方提取的设计规范。

## 作者

ColorSpace Skill 由汤圆制作。
