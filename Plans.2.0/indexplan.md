# Index 页面 2.0 迭代计划

## 一、概述

基于 `index.html2.0/` 中的 Figma 原型代码分析，当前 1.0 版 index 页面采用 **Thymeleaf 模板 + 原生 JS + 自定义 Neumorphism CSS** 架构。2.0 版原型采用 **React 18 + TypeScript + Vite + Tailwind CSS v4 + shadcn/ui (Radix)** 现代技术栈，全面提升设计品质和可维护性。

### 1.0 vs 2.0 核心差异

| 维度 | 1.0 (当前) | 2.0 (目标) |
|------|-----------|-----------|
| 框架 | Thymeleaf + 原生 JS | React 18 + TypeScript |
| 样式 | 手写 CSS (Neumorphism) | Tailwind CSS v3 + CSS 变量 Design Token |
| UI 组件 | 手写模态框、按钮 | shadcn/ui (Radix 无头组件) |
| 构建 | Maven + 静态资源 | Vite |
| 状态管理 | 全局变量 + DOM 操作 | React useState/useEffect |
| 图标 | Lucide (CDN) | lucide-react |
| 动画 | CSS transition | Motion (ex Framer Motion) |
| 响应式 | 基础 media query | Tailwind 响应式 + use-mobile hook |
| 主题 | 单一 Neumorphism | CSS 变量体系，支持 Light/Dark |

---

## 二、原型分析 (index.html2.0)

### 2.1 技术栈
- **React 18** + **TypeScript**
- **Vite** 构建工具
- **Tailwind CSS v3** + PostCSS
- **shadcn/ui** 组件库 (40+ Radix 组件)
- **lucide-react** 图标
- **Motion** (动画库)
- **MUI** (Material UI，原型中引入但未深度使用)
- **Emotion** (CSS-in-JS)
- **date-fns**, **embla-carousel**, **canvas-confetti**, **cmdk**

### 2.2 页面布局结构

```
┌────────────────────────────────────────────────┐
│  ┌──────────┐  ┌────────────────────────────┐  │
│  │ Sidebar  │  │  Header (Top Nav)          │  │
│  │          │  │  - Sidebar Toggle          │  │
│  │ - Logo   │  │  - Model Selector (Popover)│  │
│  │ - New    │  │  - Nav Tabs (首页/对比/    │  │
│  │   Chat   │  │    费用/设置/高级)         │  │
│  │          │  │  - Balance + User Info     │  │
│  │ - Conv   │  ├────────────────────────────┤  │
│  │   List   │  │  Chat Body (Welcome State) │  │
│  │          │  │  - Gradient Logo           │  │
│  │          │  │  - Welcome Text            │  │
│  │ - Settings│  │  - Quick Action Cards     │  │
│  └──────────┘  │    (4 cards in 2x2 grid)   │  │
│                ├────────────────────────────┤  │
│                │  Input Bar                 │  │
│                │  - Textarea (auto-resize)  │  │
│                │  - Char Counter (x/4000)   │  │
│                │  - Send Button             │  │
│                └────────────────────────────┘  │
└────────────────────────────────────────────────┘
```

### 2.3 关键 UI 特性

1. **可折叠侧边栏** - 动画过渡，`PanelLeftClose`/`PanelLeftOpen` 切换图标
2. **模型选择器** - Popover 下拉菜单，彩色圆点 + 标签徽章
3. **顶部导航标签** - 首页/对比/费用/设置/高级
4. **会话列表** - 悬浮显示 More 按钮，active 高亮
5. **欢迎页** - 渐变 Logo + 4 张快捷操作卡片 (创意写作/代码助手/知识问答/数据分析)
6. **输入区域** - 自动伸缩 textarea，字符计数 (颜色警告)，Shift+Enter 换行提示
7. **余额显示** - 等宽数字字体 (Geist Mono)
8. **Design Token** - 完整的 CSS 变量体系，42 个语义变量

### 2.4 Design Token 体系

```css
/* 语义色 */
--background: #f7f7f9    /* 页面底色 */
--foreground: #111118    /* 主文字 */
--card: #ffffff          /* 卡片背景 */
--primary: #4f46e5       /* 主色 (Indigo) */
--muted-foreground: #8888a0  /* 次要文字 */
--accent: #f0f0f6        /* 悬浮态 */
--border: rgba(0,0,0,0.07)  /* 边框 */
--sidebar: #ffffff

/* 组件专用 */
--sidebar-accent: #f5f5fb
--input-background: #f5f5fa
--ring: rgba(79,70,229,0.3)
--radius: 0.5rem
```

---

## 三、迭代方案：分阶段迁移

### 阶段 1：前端工程化基础搭建

**目标：** 在现有 Spring Boot 项目中集成 React + Vite 构建

#### 任务清单
1. **创建 React 前端子项目**
   - 在项目根目录创建 `frontend/` 目录
   - 初始化 Vite + React + TypeScript 项目
   - 配置 `package.json` 依赖 (参考 index.html2.0)

2. **集成 Tailwind CSS v3 + shadcn/ui**
   - 安装 `tailwindcss@3` + `postcss` + `autoprefixer`
   - 生成 `tailwind.config.js` 和 `postcss.config.js`
   - 初始化 shadcn/ui (`npx shadcn@canary init`)
   - 配置 `tsconfig.json` 路径别名 `@/*` → `./src/*`
   - 配置 `vite.config.ts` resolve alias `@` → `./src`
   - 按需添加组件 (button, input, textarea, dropdown-menu, popover, sidebar, card, etc.)

3. **配置 Design Token**
   - 迁移 `index.html2.0/src/styles/theme.css` 中的 CSS 变量到 `src/index.css`
   - 保留 Tailwind v3 `@tailwind base/components/utilities` 指令
   - 集成 Geist 字体 (Geist Sans + Geist Mono)

4. **Vite 构建输出配置**
   - 配置 `vite build` 输出到 `../src/main/resources/static/`
   - 配置开发代理：`/api` → `http://localhost:8080`
   - 配置路径别名 `@` → `./src`（resolve.alias）

#### 依赖清单
```json
{
  "react": "^19.2",
  "react-dom": "^19.2",
  "lucide-react": "latest",
  "motion": "latest",
  "clsx": "latest",
  "tailwind-merge": "latest",
  "class-variance-authority": "latest"
}
```

#### 开发依赖
```json
{
  "tailwindcss": "^3.4.0",
  "postcss": "latest",
  "autoprefixer": "latest",
  "@types/node": "latest"
}
```

---

### 阶段 2：核心布局与导航

**目标：** 实现 App 主框架布局和导航系统

#### 任务清单
1. **App.tsx 主布局**
   - 侧边栏 + Header + 内容区 + 输入栏 四区布局
   - Flexbox + Tailwind 响应式类

2. **Sidebar 组件**
   - Logo + 品牌名
   - "新建对话" 按钮
   - 会话列表 (从 API 加载)
   - 可折叠/展开动画
   - "偏好设置" 底部链接
   - 移动端 Drawer 模式

3. **Header 组件**
   - 侧边栏 Toggle 按钮
   - Model Selector (Popover 下拉)
   - 导航标签 (首页/对比/费用/设置/高级)
   - 余额显示 + 用户头像

4. **响应式适配**
   - `use-mobile` hook 检测移动端
   - 移动端：侧边栏变为 Sheet/Drawer
   - 移动端：隐藏部分导航标签

#### 组件拆分
```
src/
├── components/
│   ├── layout/
│   │   ├── Sidebar.tsx
│   │   ├── Header.tsx
│   │   ├── InputBar.tsx
│   │   └── ChatLayout.tsx
│   ├── chat/
│   │   ├── ConversationList.tsx
│   │   ├── WelcomeScreen.tsx
│   │   ├── QuickActionCards.tsx
│   │   └── MessageBubble.tsx
│   └── shared/
│       ├── ModelSelector.tsx
│       ├── UserMenu.tsx
│       └── BalanceDisplay.tsx
```

---

### 阶段 3：核心功能对接

**目标：** 将 1.0 的 API 功能对接到 React 前端

#### 任务清单
1. **认证模块**
   - 登录状态检测 (读取 localStorage token)
   - 未登录 → 重定向 `/login`
   - Token 过期 → 自动登出

2. **会话管理**
   - 加载会话列表 (`GET /api/conversations`)
   - 切换/新建/删除会话
   - 会话标题自动更新

3. **聊天功能**
   - SSE 流式响应 (`POST /api/chat/send`)
   - 消息气泡渲染 (Markdown 解析)
   - 停止生成功能
   - 历史消息加载

4. **模型选择**
   - 加载可用模型列表 (`GET /api/model-configs`)
   - 模型切换 persisted 到 `localStorage`
   - 当前模型指示器

5. **联网搜索**
   - Toggle 开关组件
   - 参数带入聊天请求

6. **知识库选择**
   - Select 下拉加载用户知识库
   - 参数带入聊天请求

---

### 阶段 4：辅助功能与细节

**目标：** 完善周边功能和交互细节

#### 任务清单
1. **快捷操作卡片**
   - 4 张卡片 (创意写作/代码助手/知识问答/数据分析)
   - 点击自动填入提示文字到输入框
   - Hover 边框高亮效果

2. **图片上传**
   - 按钮触发 file input
   - 图片预览条
   - 与聊天消息联动

3. **提示词集成**
   - 提示词选择器
   - 当前提示词指示器

4. **其他 1.0 功能迁移**
   - 消息通知铃铛
   - 好友系统入口
   - 记忆管理入口
   - 设置弹窗 (个人信息/钱包)

5. **动效与微交互**
   - 侧边栏展开/折叠平滑过渡
   - 按钮 Hover/Active 状态
   - 发送按钮状态变化
   - Loading 骨架屏

---

### 阶段 5：登录页同步改造

**目标：** 将刚创建的 Thymeleaf `login.html` 同步为 React 登录页

#### 任务清单
1. 创建 `LoginPage.tsx` React 组件
2. 登录/注册/重置密码 面板切换
3. 登录成功 → 跳转 `/`
4. 与 1.0 保持 API 兼容
5. Vite 多入口配置 (login 独立页)

---

## 四、技术决策与风险

### 关键决策

| 决策 | 方案 | 理由 |
|------|------|------|
| React 子项目位置 | `frontend/` 独立目录 | 清晰分离前后端代码 |
| 是否使用路由 | 暂不需要 react-router | index 是单页，login 独立入口 |
| CSS 方案 | Tailwind v3 + CSS 变量 | shadcn canary 与 v4 初始化兼容性不足，v3 生态成熟 |
| 状态管理 | React Context + hooks | 规模适中，无需 Redux |
| API 封装 | 复用 `common.js` 中的 createApi 逻辑 | 保持与 1.0 兼容 |

### 风险与应对

| 风险 | 应对 |
|------|------|
| Thymeleaf 与 React 并行期维护成本 | 新旧代码完全分离，`frontend/` 独立构建输出到 `static/` |
| Vite SPA 刷新后 404 | 配置 Spring Boot 将非 API 请求 fallback 到 index.html |
| 移动端适配差异 | 2.0 原型已包含响应式设计，微调即可 |
| shadcn/ui 组件版本兼容 | 锁定版本，参考原型中的 Radix 版本 |

---

## 五、文件映射 (1.0 → 2.0)

| 1.0 文件 | 2.0 替代 |
|----------|---------|
| `templates/index.html` | `frontend/src/App.tsx` |
| `static/app.js` (~2000行) | 拆分为 10+ 个组件文件 |
| `static/app.css` (Neumorphism) | `styles/theme.css` (CSS 变量) |
| `static/common.js` | `src/lib/api.ts` + `src/lib/auth.ts` |
| `static/theme.css` | 迁移到 Tailwind CSS 变量 |
| `controller/PageController.java` | 仅需 fallback 路由 |

---

## 六、预估工作量

| 阶段 | 内容 | 人天 |
|------|------|------|
| 阶段 1 | 工程化基础搭建 | 1-2 |
| 阶段 2 | 核心布局与导航 | 2-3 |
| 阶段 3 | 核心功能对接 | 3-4 |
| 阶段 4 | 辅助功能与细节 | 2-3 |
| 阶段 5 | 登录页同步改造 | 1 |
| **合计** | | **9-13** |

---

## 七、启动建议

**推荐从阶段 1 开始**，在 `frontend/` 目录创建 Vite 项目，以 `index.html2.0/src/app/App.tsx` 为蓝本搭建主布局，逐步替换现有 Thymeleaf 模板。开发期间通过 Vite 代理同时运行前后端，生产构建时将 Vite 输出到 Spring Boot 的 `static/` 目录。

构建命令示例：
```bash
# 开发
cd frontend && npm run dev  # Vite proxy → Spring Boot :8080

# 生产构建
cd frontend && npm run build  # → ../src/main/resources/static/
```
