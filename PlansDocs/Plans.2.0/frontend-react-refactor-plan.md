# AI Chat 前端 React + TypeScript 重构方案

## 一、技术选型

| 类别 | 选型 | 理由 |
|------|------|------|
| 框架 | **React 18** | 生态最大，社区资源最丰富 |
| 语言 | **TypeScript** | 类型安全，减少运行时错误 |
| 构建工具 | **Vite 5** | 开发热更新快，打包体积小 |
| UI 样式 | **Tailwind CSS 3** | 原子化 CSS，开发效率高，与组件共存 |
| 组件库 | **Radix UI** (无样式) | 无障碍访问内置，可完全自定义样式 |
| 路由 | **React Router v6** | 嵌套路由，懒加载支持好 |
| 状态管理 | **Zustand** | 轻量（~1KB），API 极简，无 boilerplate |
| HTTP | **Axios** | 拦截器支持，自动 Token 注入 |
| 图标 | **Lucide React** | 轻量，Tree-shakable |
| Toast | **Sonner** | 美观，API 简单 |
| 表单 | **React Hook Form + Zod** | 性能好，类型安全的校验 |

### 选型对比说明

**为什么不用 Next.js / Remix？**
当前后端是 Spring Boot，已承载全部 API 和业务逻辑。引入 SSR 框架会增加复杂度和部署成本。纯 SPA（Vite + React）通过 Nginx 或 Spring Boot 静态资源部署即可。

**为什么 Zustand 而不是 Redux / Jotai？**
- Redux Toolkit 功能强大但 boilerplate 仍偏多
- Jotai 适合原子化状态，此项目状态集中（当前用户、会话、聊天消息）
- Zustand 在简洁性和可维护性之间取得最佳平衡

**为什么 Tailwind + Radix 而不是 Ant Design / MUI？**
- Ant Design / MUI 适合后台系统，但定制化成本高
- 此项目含聊天主界面，需要独特的 UI 设计，不适合统一组件库风格
- Tailwind + Radix（无样式原语）提供最大定制自由度

---

## 二、项目目录结构

```
aichat-frontend/
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.ts
├── postcss.config.js
├── .env                          # 环境变量 (VITE_API_BASE_URL)
├── .env.production
├── public/
│   └── favicon.svg
└── src/
    ├── main.tsx                  # 入口
    ├── App.tsx                   # 根组件 (路由)
    │
    ├── api/                      # API 层
    │   ├── client.ts             # Axios 实例 (拦截器、Token、401处理)
    │   ├── auth.ts               # 认证 API
    │   ├── chat.ts               # 聊天、会话 API
    │   ├── memory.ts             # 记忆管理 API
    │   ├── knowledgeBase.ts      # 知识库 API
    │   ├── prompts.ts            # 提示词 API
    │   ├── hub.ts                # 提示词社区 API
    │   ├── friends.ts            # 好友 API
    │   ├── models.ts             # 模型配置 API
    │   ├── billing.ts            # 赞助、余额 API
    │   ├── notifications.ts      # 通知 API
    │   └── admin/                # 管理后台 API
    │       ├── dashboard.ts
    │       ├── users.ts
    │       ├── sponsors.ts
    │       ├── models.ts
    │       ├── prompts.ts
    │       ├── usage.ts
    │       └── conversations.ts
    │
    ├── stores/                   # Zustand 状态管理
    │   ├── authStore.ts          # 用户认证状态
    │   ├── chatStore.ts          # 聊天消息、流式输出状态
    │   ├── conversationStore.ts  # 会话列表
    │   ├── memoryStore.ts        # 记忆数据
    │   ├── kbStore.ts            # 知识库数据
    │   └── notificationStore.ts  # 未读通知数
    │
    ├── hooks/                    # 自定义 Hooks
    │   ├── useAuth.ts            # 认证相关 (登录/注册/退出)
    │   ├── useSSE.ts             # SSE 流式消息 Hook
    │   ├── useInfiniteScroll.ts  # 无限滚动加载历史
    │   ├── usePolling.ts         # 可暂停轮询 Hook
    │   ├── useAutoScroll.ts      # 消息列表智能滚动
    │   ├── useKeyboard.ts        # 键盘快捷键
    │   └── useDebounce.ts        # 防抖
    │
    ├── types/                    # TypeScript 类型定义
    │   ├── auth.ts
    │   ├── chat.ts
    │   ├── memory.ts
    │   ├── knowledgeBase.ts
    │   ├── prompt.ts
    │   ├── friend.ts
    │   ├── model.ts
    │   ├── billing.ts
    │   └── admin.ts
    │
    ├── lib/                      # 工具函数
    │   ├── utils.ts              # formatSize, timeLabel, escapeHtml 等
    │   ├── constants.ts          # 常量 (API 路径等)
    │   └── storage.ts            # localStorage 封装
    │
    ├── components/               # 通用组件
    │   ├── ui/                   # 基础 UI 组件
    │   │   ├── Button.tsx
    │   │   ├── Input.tsx
    │   │   ├── Modal.tsx
    │   │   ├── Dialog.tsx        # Radix 封装
    │   │   ├── Toast.tsx
    │   │   ├── Badge.tsx
    │   │   ├── Tabs.tsx
    │   │   ├── Spinner.tsx
    │   │   ├── Skeleton.tsx
    │   │   ├── Avatar.tsx
    │   │   ├── Dropdown.tsx
    │   │   ├── ConfirmDialog.tsx # 自定义确认框
    │   │   └── EmptyState.tsx
    │   ├── layout/
    │   │   ├── AppLayout.tsx     # 主聊天页布局
    │   │   ├── AdminLayout.tsx   # 管理后台布局
    │   │   ├── Sidebar.tsx
    │   │   └── Topbar.tsx
    │   └── shared/
    │       ├── MarkdownRenderer.tsx  # Markdown 渲染 (AI回复)
    │       ├── ImageUploader.tsx     # 图片上传 (含预览)
    │       ├── FileUploader.tsx      # 文件上传 (知识库)
    │       ├── Pagination.tsx
    │       ├── SearchBar.tsx
    │       └── StatusBadge.tsx
    │
    ├── features/                 # 业务功能模块
    │   ├── auth/
    │   │   ├── LoginModal.tsx
    │   │   ├── RegisterModal.tsx
    │   │   ├── ResetPasswordModal.tsx
    │   │   └── WelcomeModal.tsx
    │   │
    │   ├── chat/
    │   │   ├── ChatArea.tsx          # 聊天主区域
    │   │   ├── MessageList.tsx       # 消息列表 (含智能滚动)
    │   │   ├── MessageBubble.tsx     # 单条消息气泡
    │   │   ├── MessageInput.tsx      # 输入区域
    │   │   ├── ConversationList.tsx  # 会话列表
    │   │   ├── ConversationItem.tsx  # 单个会话项
    │   │   ├── ChatHeader.tsx        # 顶栏 (模型/提示词指示器、搜索开关)
    │   │   └── StreamingBubble.tsx   # 流式输出气泡
    │   │
    │   ├── models/
    │   │   ├── ModelSelector.tsx
    │   │   └── ModelModal.tsx
    │   │
    │   ├── prompts/
    │   │   ├── PromptSelector.tsx
    │   │   ├── PromptModal.tsx
    │   │   ├── PromptEditor.tsx
    │   │   └── PromptCard.tsx
    │   │
    │   ├── hub/
    │   │   ├── HubPage.tsx
    │   │   ├── HubCard.tsx
    │   │   ├── HubDetail.tsx
    │   │   ├── HubUpload.tsx
    │   │   └── CommentSection.tsx
    │   │
    │   ├── friends/
    │   │   ├── FriendModal.tsx
    │   │   ├── FriendList.tsx
    │   │   ├── FriendChat.tsx
    │   │   ├── AddFriendModal.tsx
    │   │   └── FriendRequestsModal.tsx
    │   │
    │   ├── memory/
    │   │   ├── MemoryPage.tsx
    │   │   ├── MemoryStats.tsx
    │   │   ├── MemoryList.tsx
    │   │   ├── MemoryItem.tsx
    │   │   ├── MemoryEditor.tsx
    │   │   └── MemorySearch.tsx
    │   │
    │   ├── knowledgeBase/
    │   │   ├── KBPage.tsx
    │   │   ├── KBList.tsx
    │   │   ├── KBCard.tsx
    │   │   ├── KBEditor.tsx
    │   │   ├── DocList.tsx
    │   │   ├── DocItem.tsx
    │   │   └── DocUploader.tsx
    │   │
    │   ├── settings/
    │   │   ├── SettingsModal.tsx
    │   │   ├── ProfileTab.tsx
    │   │   ├── AvatarCrop.tsx
    │   │   ├── WalletTab.tsx
    │   │   ├── ChangePasswordModal.tsx
    │   │   └── SponsorModal.tsx
    │   │
    │   ├── notifications/
    │   │   └── NotificationModal.tsx
    │   │
    │   └── admin/
    │       ├── dashboard/
    │       │   └── DashboardPage.tsx
    │       ├── users/
    │       │   ├── UsersPage.tsx
    │       │   ├── BalanceModal.tsx
    │       │   └── RoleModal.tsx
    │       ├── sponsors/
    │       │   ├── SponsorsPage.tsx
    │       │   ├── ApproveModal.tsx
    │       │   └── RejectModal.tsx
    │       ├── models/
    │       │   └── AdminModelsPage.tsx
    │       ├── prompts/
    │       │   └── AdminPromptsPage.tsx
    │       ├── usage/
    │       │   └── UsagePage.tsx
    │       └── conversations/
    │           ├── ConversationsPage.tsx
    │           └── ChatDetailModal.tsx
    │
    └── pages/                    # 页面级组件 (路由入口)
        ├── ChatPage.tsx          # 主聊天页
        ├── AdminPage.tsx         # 管理后台
        ├── MemoryPage.tsx        # 记忆管理 (独立页)
        ├── KBPage.tsx            # 知识库管理 (独立页)
        └── HubPage.tsx           # 提示词社区 (独立页)
```

---

## 三、路由设计

```
/                          → ChatPage         (主聊天页)
/admin                     → AdminPage        (管理后台)
  /admin/dashboard         → DashboardPage
  /admin/users             → UsersPage
  /admin/sponsors          → SponsorsPage
  /admin/models            → AdminModelsPage
  /admin/prompts           → AdminPromptsPage
  /admin/usage             → UsagePage
  /admin/conversations     → ConversationsPage
/memory-manager            → MemoryPage       (记忆管理)
/kb-manager                → KBPage           (知识库管理)
/prompt-hub                → HubPage          (提示词社区)
```

**懒加载策略**：管理后台和独立页使用 `React.lazy()` + `Suspense` 实现按需加载。

---

## 四、核心状态设计 (Zustand)

### authStore
```typescript
interface AuthState {
  token: string | null;
  username: string | null;
  userInfo: UserInfo | null;        // PID, avatar, email, signature, balance
  isLoggedIn: boolean;

  login: (token, username) => void;
  logout: () => void;
  fetchUserInfo: () => Promise<void>;
  updateBalance: (amount: number) => void;
}
```

### chatStore
```typescript
interface ChatState {
  currentConvId: number | null;
  messages: Message[];               // 当前会话消息
  isStreaming: boolean;
  abortController: AbortController | null;

  currentPromptId: number | null;    // 选中的提示词
  currentModelId: number | null;     // 选中的模型
  webSearchEnabled: boolean;
  currentKbId: number | null;        // 选中的知识库
  imageDescription: string | null;   // 图片识别结果

  selectConversation: (id) => void;
  addMessage: (msg) => void;
  updateLastMessage: (chunk) => void;  // 流式追加
  sendMessage: (content) => Promise<void>;
  stopGeneration: () => void;
  clearImage: () => void;
}
```

### conversationStore
```typescript
interface ConvState {
  conversations: Conversation[];
  load: () => Promise<void>;
  create: () => Promise<Conversation>;
  remove: (id) => Promise<void>;
}
```

### notificationStore
```typescript
interface NotifState {
  unreadCount: number;
  fetchUnread: () => Promise<void>;
}
```

---

## 五、API 层设计

### client.ts — 统一 Axios 实例
```typescript
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
});

// 请求拦截器：自动附加 Token
apiClient.interceptors.request.use(config => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 响应拦截器：统一处理 401 和错误
apiClient.interceptors.response.use(
  res => res,
  error => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/';
    }
    return Promise.reject(error);
  }
);
```

### SSE 流式请求 — useSSE Hook
```typescript
function useSSE() {
  const sendSSE = async (url: string, body: object, onChunk: (text: string) => void) => {
    const token = useAuthStore.getState().token;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify(body),
    });

    const reader = response.body!.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const text = decoder.decode(value, { stream: true });
      // SSE 解析: data: {...}\n\n
      for (const line of text.split('\n')) {
        if (line.startsWith('data: ')) {
          const chunk = line.slice(6);
          if (chunk === '[DONE]') return;
          onChunk(chunk);
        }
      }
    }
  };

  return { sendSSE };
}
```

---

## 六、核心组件设计

### 6.1 主聊天页 — ChatPage

```
┌──────────────────────────────────────────────────┐
│ ChatHeader                                        │
│ [模型指示器] [提示词指示器] [🌐联网] [知识库]  [🔔通知] [👤用户] │
├──────────────┬───────────────────────────────────┤
│ Sidebar      │ ChatArea                          │
│              │ ┌───────────────────────────────┐ │
│ [＋新建会话]  │ │ MessageList                  │ │
│ ───────────  │ │  [AI] 你好！...               │ │
│ 📝 会话1     │ │  [User] 帮我写代码...         │ │
│ 📝 会话2     │ │  [AI] 当然... (流式输出)      │ │
│ 📝 会话3     │ │                               │ │
│              │ │            ↓ 回到底部          │ │
│              │ └───────────────────────────────┘ │
│              │ MessageInput                      │
│ [⚙️ 设置]    │ [🖼] [输入消息...] [发送] [⏹停止]│
└──────────────┴───────────────────────────────────┘
```

**关键交互：**
- 消息气泡长按/悬停显示删除按钮
- AI 消息使用 MarkdownRenderer 渲染（代码高亮用 `react-syntax-highlighter` 或 `shiki`）
- 流式输出时自动滚动 + 用户上滑暂停滚动 + "↓ 回到底部"浮动按钮
- `Enter` 发送 / `Shift+Enter` 换行 / `Ctrl+Enter` 发送

### 6.2 模态框设计原则

全部模态框使用 Radix `@radix-ui/react-dialog` 封装，统一提供：
- 打开/关闭动画（CSS transition）
- 背景遮罩（点击关闭可选）
- `Esc` 关闭
- 焦点陷阱（Tab 循环）
- `aria-modal` / `role="dialog"` 无障碍属性

```typescript
// components/ui/Dialog.tsx
<Dialog.Root open={open} onOpenChange={setOpen}>
  <Dialog.Portal>
    <Dialog.Overlay className="fixed inset-0 bg-black/40 z-50 animate-fade-in" />
    <Dialog.Content className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2
      bg-white rounded-2xl p-6 shadow-xl z-50 w-full max-w-md animate-scale-in">
      <Dialog.Title className="text-lg font-semibold mb-4">{title}</Dialog.Title>
      {children}
    </Dialog.Content>
  </Dialog.Portal>
</Dialog.Root>
```

### 6.3 消息输入组件

```typescript
// features/chat/MessageInput.tsx
// 核心逻辑：
// 1. textarea 自适应高度 (max 3 行)
// 2. Enter 发送 / Shift+Enter 换行
// 3. 发送中按钮变为 Stop 按钮
// 4. 图片上传预览条
// 5. 输入框聚焦状态管理
```

### 6.4 智能滚动 Hook — useAutoScroll

```typescript
function useAutoScroll(containerRef: RefObject<HTMLDivElement>, dependency: any) {
  const [isAtBottom, setIsAtBottom] = useState(true);

  // 监听滚动：用户在底部时跟随，否则不滚动
  // 显示/隐藏 "↓ 回到底部" 浮动按钮
  // 用户点击按钮后强制滚到底部
}
```

---

## 七、与 Spring Boot 后端的集成

### 7.1 开发环境

Vite 开发服务器端口 `5173`，通过 `vite.config.ts` 配置代理：

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',       // 后端 API
      '/uploads': 'http://localhost:8080',   // 上传文件
    },
  },
  build: {
    outDir: '../src/main/resources/static',  // 打包输出到 Spring Boot static
    emptyOutDir: true,
  },
});
```

### 7.2 生产部署

```
方案 A：Nginx 反向代理（推荐）
  location /api { proxy_pass http://springboot:8080; }
  location /    { root /app/dist; try_files $uri /index.html; }

方案 B：Spring Boot 直接托管
  Vite build → src/main/resources/static/
  Spring Boot 添加 SPA fallback 配置
```

### 7.3 环境变量

```env
# .env (开发)
VITE_API_BASE_URL=

# .env.production (生产)
VITE_API_BASE_URL=/api
```

---

## 八、分步迁移计划

### 第一阶段：基础设施搭建（2-3天）

1. 使用 Vite 脚手架创建项目：`npm create vite@latest aichat-frontend -- --template react-ts`
2. 安装依赖：Tailwind CSS、Radix UI、React Router、Zustand、Axios、Lucide、Sonner
3. 配置 Tailwind（主题色、字体、圆角等）
4. 编写基础 UI 组件：`Button`、`Input`、`Modal`、`Dialog`、`Toast`、`Spinner`
5. 搭建 Axios 客户端（拦截器、Token 管理）
6. 实现 `authStore`（登录/注册/退出逻辑）
7. 实现路由骨架（所有页面占位）

### 第二阶段：核心聊天功能（3-4天）

1. 实现 `features/auth/` — 登录/注册/重置密码模态框
2. 实现 `features/chat/ConversationList` — 会话列表
3. 实现 `features/chat/ChatArea` — 聊天主区域
4. 实现 `features/chat/MessageBubble` — 消息气泡（含 Markdown 渲染）
5. 实现 `features/chat/StreamingBubble` + `useSSE` — SSE 流式输出
6. 实现 `features/chat/MessageInput` — 输入区域
7. 实现 `useAutoScroll` — 智能滚动
8. 实现图片上传识别功能
9. 实现联网搜索 Toggle

### 第三阶段：辅助功能（2-3天）

1. 实现 `features/models/` — 模型选择
2. 实现 `features/prompts/` — 提示词管理
3. 实现 `features/settings/` — 设置（个人信息、钱包、修改密码、赞助、头像裁剪）
4. 实现 `features/friends/` — 好友系统
5. 实现 `features/notifications/` — 消息通知

### 第四阶段：独立页面（1-2天）

1. 实现 `features/memory/` — 记忆管理页
2. 实现 `features/knowledgeBase/` — 知识库管理页（含轮询优化）
3. 实现 `features/hub/` — 提示词社区页（含评论区）

### 第五阶段：管理后台（3-4天）

1. 实现管理后台布局 + 管理员登录
2. Dashboard 仪表盘
3. 用户管理（含余额编辑、角色变更、启用/禁用）
4. 赞助审核（含图片预览、通过/拒绝）
5. 模型配置管理
6. 社区管理
7. 消费记录查询
8. 聊天记录查看

### 第六阶段：收尾优化（1-2天）

1. 添加页面级 Loading / Error 边界
2. 添加 404 页面
3. 响应式适配（移动端）
4. 键盘快捷键
5. 打包优化（代码分割、gzip）
6. 删除旧的 Thymeleaf 模板和 static 文件
7. 更新 Spring Boot 静态资源配置

---

## 九、代码拆分与打包优化

### 代码分割 (Code Splitting)

```typescript
// App.tsx
const AdminPage     = lazy(() => import('./pages/AdminPage'));
const HubPage       = lazy(() => import('./pages/HubPage'));
const MemoryPage    = lazy(() => import('./pages/MemoryPage'));
const KBPage        = lazy(() => import('./pages/KBPage'));
```

### 预期打包体积

| 模块 | 预估大小 (gzip) |
|------|----------------|
| React + ReactDOM | ~45 KB |
| React Router | ~10 KB |
| Zustand | ~2 KB |
| Axios | ~12 KB |
| Tailwind CSS | ~10 KB |
| Radix UI (Dialog, Tabs, etc) | ~15 KB |
| Markdown 渲染 | ~30 KB |
| **总计 (首屏)** | **~120 KB** |
| 管理后台 (懒加载) | ~40 KB |
| 其他独立页 (懒加载) | ~20 KB each |

---

## 十、与原方案的对比

| 维度 | 现状 (Thymeleaf + 原生 JS) | 重构后 (React + TS) |
|------|---------------------------|---------------------|
| 代码行数 | ~3000 行 (HTML+JS+CSS 混杂) | ~8000 行 TSX (组件化, 可维护) |
| 类型安全 | 无 | 完整 TypeScript 类型 |
| 构建 | 无 (浏览器直接加载) | Vite 打包 + Tree-shaking |
| 状态管理 | localStorage + 全局变量 | Zustand 响应式状态 |
| 组件复用 | toast/escapeHtml 等内联复制 5 遍 | 统一组件库 |
| 流式输出 | 手动 fetch + ReadableStream | useSSE Hook 封装 |
| 路由 | 独立 HTML 页面跳转 | SPA 路由，无刷新切换 |
| 加载体验 | 文字 "加载中..." | 骨架屏 + Spinner |
| 错误处理 | alert / toast 简短提示 | 统一错误拦截 + 重试 |
| 无障碍 | 无 | Radix 内置 ARIA |
| 开发体验 | 修改 → 重启 → 刷新 | HMR 热更新 (秒级) |
| 首屏速度 | 全量加载 | 路由懒加载，按需加载 |

---

## 十一、风险评估与缓解

| 风险 | 缓解措施 |
|------|---------|
| SSE 流式输出兼容性 | 使用 ReadableStream API (主流浏览器均支持)，降级方案用 fetch 分段轮询 |
| Tailwind 学习成本 | 团队统一安装 Tailwind CSS IntelliSense VSCode 插件，提供常用 class 速查表 |
| 迁移期间功能不可用 | 新旧前端并行运行：在前端项目中逐步迁移，完成后切换入口 |
| 打包后 Spring Boot 路径问题 | Vite `base` 配置 + Spring 静态资源配置 + SPA fallback 一起调通后再上线 |

---

*文档生成日期：2026-06-20*
*完整梳理了 5 个 HTML 模板、3 个 JS 文件、2 个 CSS 文件中所有功能的迁移方案*
