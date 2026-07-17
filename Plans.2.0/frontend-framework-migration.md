# 前端框架选型与重构方案

## 一、现状分析

| 项目 | 状态 |
|------|------|
| 后端框架 | Spring Boot 4.0.6 + Java 17 |
| 模板引擎 | Thymeleaf（服务端渲染） |
| 前端技术 | 原生 JS + CSS，无构建工具，无模块化 |
| 页面数量 | 5 个 HTML 页面 |
| JS 总量 | app.js (~1500行) + admin.js (~800行) + promptHub.js (~400行) |
| 构建工具 | 无，完全无 Node 依赖 |

---

## 二、框架选型对比

### 2.1 候选框架

| 维度 | **Vue 3 + Vite** | **React + Vite** | **Alpine.js + HTMX** |
|------|------------------|-------------------|----------------------|
| 学习曲线 | 低（API 简洁、中文文档好） | 中（JSX、Hooks、状态管理较多概念） | 极低（HTML 属性式编程） |
| 与 Thymeleaf 共存 | ✅ 可渐进迁移，现有页面逐页替换 | ⚠️ JSX 与 Thymeleaf 语法冲突严重 | ✅ 天然契合，增强现有模板 |
| 构建依赖 | 需要 Node.js + Vite | 需要 Node.js + Vite | 无需构建工具（CDN 引入） |
| 状态管理 | Pinia（官方推荐，简洁） | Redux / Zustand / Context | 无需（状态在 DOM 中） |
| TypeScript 支持 | 优秀 | 优秀 | 不支持 |
| 生态活跃度 | 非常高 | 最高 | 中等 |
| 打包体积 | 小（~30KB gzip） | 中等（~40KB gzip） | 极小（~15KB gzip） |
| 适合场景 | SPA + 渐进迁移 | SPA | SSR 增强、后端模板项目 |
| 国内生态 | Vant/TDesign/Ant Design Vue | Ant Design（最成熟） | 较弱 |

### 2.2 推荐方案：Vue 3 + Vite + Pinia

**推荐理由**：

1. **渐进迁移** — 现有 Thymeleaf 页面可以保留不动的部分，Vue 组件在页面中通过 `createApp().mount()` 挂载到某个容器，与 Thymeleaf 共存。5 个页面可以逐页替换，不会一次性阻塞全部功能。

2. **学习成本低** — 模板语法 `v-if`、`v-for` 与 Thymeleaf 的 `th:if`、`th:each` 概念相似，团队迁移思维负担小。

3. **中文生态好** — 文档、社区、UI 组件库（Element Plus、TDesign）均有完善的中文支持。

4. **Vite 开发体验** — HMR 极快，配置简洁，天然支持 TypeScript、CSS Modules、PostCSS。

---

## 三、目标架构

```
src/main/frontend/               ← 前端工程目录（与后端 Java 平级）
├── public/
│   └── favicon.ico
├── src/
│   ├── api/                     ← API 请求封装层
│   │   ├── index.js             ← axios 实例 + 拦截器
│   │   ├── auth.js              ← 认证相关 API
│   │   ├── chat.js              ← 聊天相关 API
│   │   ├── memory.js            ← 记忆相关 API
│   │   ├── kb.js                ← 知识库相关 API
│   │   └── admin.js             ← 管理后台 API
│   ├── assets/                  ← 静态资源
│   │   ├── styles/
│   │   │   ├── variables.css    ← CSS 变量
│   │   │   └── global.css       ← 全局样式
│   │   └── images/
│   ├── components/              ← 通用组件
│   │   ├── Toast.vue
│   │   ├── Modal.vue
│   │   ├── ConfirmDialog.vue
│   │   ├── Skeleton.vue
│   │   ├── EmptyState.vue
│   │   ├── Pagination.vue
│   │   └── ChatBubble.vue
│   ├── composables/             ← 组合式 API（状态逻辑复用）
│   │   ├── useAuth.js           ← 登录态管理
│   │   ├── useChat.js           ← 聊天核心逻辑
│   │   ├── useStreamChat.js     ← SSE 流式响应处理
│   │   ├── useMemory.js         ← 记忆管理
│   │   ├── useKB.js             ← 知识库管理
│   │   └── useWebSocket.js      ← WebSocket 连接
│   ├── layouts/                 ← 布局组件
│   │   ├── AppLayout.vue        ← 主聊天页布局
│   │   └── AdminLayout.vue      ← 管理后台布局
│   ├── pages/                   ← 页面（对应现有 5 个页面）
│   │   ├── chat/
│   │   │   ├── ChatPage.vue     ← 主聊天页（原 index.html）
│   │   │   ├── ConversationList.vue
│   │   │   ├── MessageList.vue
│   │   │   └── ChatInput.vue
│   │   ├── memory/
│   │   │   └── MemoryPage.vue   ← 记忆管理（原 memoryManager.html）
│   │   ├── kb/
│   │   │   └── KBPage.vue       ← 知识库管理（原 kbManager.html）
│   │   ├── promptHub/
│   │   │   └── PromptHubPage.vue ← 提示词社区（原 promptHub.html）
│   │   ├── admin/
│   │   │   ├── AdminDashboard.vue
│   │   │   ├── AdminUsers.vue
│   │   │   ├── AdminSponsors.vue
│   │   │   ├── AdminModels.vue
│   │   │   ├── AdminPrompts.vue
│   │   │   ├── AdminUsage.vue
│   │   │   └── AdminConversations.vue
│   │   ├── settings/
│   │   │   └── SettingsPage.vue
│   │   └── friend/
│   │       └── FriendPage.vue
│   ├── stores/                  ← Pinia 状态管理
│   │   ├── auth.js
│   │   ├── chat.js
│   │   └── global.js
│   ├── router/                  ← Vue Router 路由
│   │   └── index.js
│   ├── utils/                   ← 工具函数
│   │   ├── format.js
│   │   ├── token.js
│   │   └── escape.js
│   ├── App.vue
│   └── main.js
├── vite.config.js               ← Vite 配置（含代理到后端）
├── package.json
└── index.html                   ← SPA 入口
```

后端 Spring Boot 配置：
```
后端负责：所有 /api/* 接口  ← Vue 通过代理调用
          /admin 管理页面 → 重定向到前端
          静态资源 /assets → 前端构建产物
          用户头像 /uploads → 后端直接提供
```

---

## 四、关键技术选型

| 用途 | 选型 | 说明 |
|------|------|------|
| 框架 | Vue 3.4+ (Composition API) | `<script setup>` 语法，极致简洁 |
| 构建 | Vite 5 | 开发秒启动，HMR 极快 |
| 路由 | Vue Router 4 | SPA 多页面切换 |
| 状态管理 | Pinia | Vue 官方推荐，TypeScript 友好 |
| HTTP 请求 | Axios | 拦截器支持 Token 自动注入 |
| UI 组件库 | Element Plus | 国内最成熟的 Vue 3 组件库，适合管理后台 |
| CSS 方案 | CSS Variables + Scoped CSS | 无需额外方案，Vue SFC 自带 |
| 图标 | `@element-plus/icons-vue` | 配套 Element Plus |
| 表单验证 | Element Plus 内置 | 无需额外引入 |

---

## 五、渐进迁移策略（分阶段）

### Phase 1：基础设施搭建（不碰现有功能）
```
目标：Vite 项目跑起来，代理到后端
内容：
  1. 在项目根目录创建 src/main/frontend，用 Vite 初始化 Vue 3 项目
  2. 配置 vite.config.js 代理 /api → http://localhost:8080
  3. 搭建目录结构、路由、Axios 封装、Pinia store
  4. 创建通用组件（Toast、Modal、Loading 等）
  5. 验证开发环境：npm run dev 能正常代理后端 API
风险：无，完全不修改现有代码
```

### Phase 2：逐页面迁移（优先级从低到高）
```
顺序：
  ① memoryManager.html → MemoryPage.vue（最简单，功能独立）
  ② kbManager.html → KBPage.vue（功能独立，无聊天核心依赖）
  ③ promptHub.html → PromptHubPage.vue（社区独立功能）
  ④ admin.html → AdminLayout + 各个子页面（页面最多，但逻辑清晰）
  ⑤ index.html → ChatPage.vue（最复杂，聊天核心逻辑，最后迁移）

每个页面迁移后：
  - 旧页面保留不动
  - 通过 Vue Router 访问新页面
  - 验证功能正常后，替换导航链接指向新路由
```

### Phase 3：清理与优化
```
  1. 删除所有旧 Thymeleaf 模板和旧 CSS/JS 文件
  2. 配置 Maven frontend-maven-plugin 自动构建前端到 target/classes/static/
  3. 后端 PageController 改为返回 index.html 或 API 响应
  4. 生产构建优化（代码分割、Tree Shaking、Gzip）
```

---

## 六、Vite 关键配置

```js
// vite.config.js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': '/src' }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../resources/static',  // 直接输出到 Spring Boot 静态资源目录
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'vue-vendor': ['vue', 'vue-router', 'pinia']
        }
      }
    }
  }
})
```

---

## 七、关键代码示例

### 7.1 Axios 封装 (src/api/index.js)
```js
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use(config => {
  const token = localStorage.getItem('chat_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('chat_token')
      window.location.href = '/'
    }
    ElMessage.error(error.response?.data?.message || '请求失败')
    return Promise.reject(error)
  }
)

export default http
```

### 7.2 Pinia 认证 Store (src/stores/auth.js)
```js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import http from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('chat_token') || '')
  const user = ref(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(credentials) {
    const { data } = await http.post('/auth/login', credentials)
    token.value = data.token
    localStorage.setItem('chat_token', data.token)
    await fetchUser()
  }

  async function fetchUser() {
    if (!token.value) return
    const { data } = await http.get('/auth/me')
    user.value = data
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('chat_token')
  }

  return { token, user, isLoggedIn, login, fetchUser, logout }
})
```

### 7.3 聊天 Composable (src/composables/useStreamChat.js)
```js
import { ref } from 'vue'

export function useStreamChat() {
  const messages = ref([])
  const isStreaming = ref(false)
  let abortCtrl = null

  async function sendMessage(content, convId, opts = {}) {
    messages.value.push({ role: 'user', content })
    messages.value.push({ role: 'ai', content: '' })
    isStreaming.value = true

    abortCtrl = new AbortController()
    const response = await fetch('/api/chat/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('chat_token')}`
      },
      body: JSON.stringify({ conversationId: convId, message: content, ...opts }),
      signal: abortCtrl.signal
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    const aiIndex = messages.value.length - 1

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      messages.value[aiIndex].content += decoder.decode(value, { stream: true })
    }

    isStreaming.value = false
  }

  function stopGenerate() { abortCtrl?.abort(); isStreaming.value = false }

  return { messages, isStreaming, sendMessage, stopGenerate }
}
```

### 7.4 聊天消息组件 (src/components/ChatBubble.vue)
```vue
<template>
  <div class="chat-bubble" :class="role">
    <div class="bubble-content">
      <div v-if="role === 'ai'" class="markdown-body" v-html="rendered"></div>
      <div v-else>{{ content }}</div>
    </div>
    <button
      v-if="deletable"
      class="delete-btn"
      @click="$emit('delete')"
    >&times;</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  content: String,
  role: { type: String, default: 'user' },
  deletable: { type: Boolean, default: true }
})

defineEmits(['delete'])

const rendered = computed(() => marked(props.content || ''))
</script>

<style scoped>
.chat-bubble {
  padding: 12px 16px;
  margin-bottom: 12px;
  border-radius: 8px;
  max-width: 75%;
  position: relative;
}
.chat-bubble.user {
  background: var(--primary);
  color: #fff;
  margin-left: auto;
}
.chat-bubble.ai {
  background: var(--bg-secondary);
  color: var(--text-primary);
}
.delete-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  background: var(--danger);
  color: white;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}
.chat-bubble:hover .delete-btn { opacity: 1; }
</style>
```

---

## 八、与后端协作方式

### 8.1 开发模式
```
终端1: ./mvnw spring-boot:run         → localhost:8080（后端）
终端2: npm run dev                     → localhost:5173（前端，代理 /api 到 8080）
```

### 8.2 生产部署
```
npm run build                           → 产物输出到 src/main/resources/static/
./mvnw package -DskipTests              → 打包成单 jar，前后端合一
```

### 8.3 需要后端配合的改动（极小）
- 后端 `PageController` 中原来返回 Thymeleaf 视图的方法，改为重定向到前端路由或返回 `index.html`
- Spring Boot 配置 `spring.web.resources.static-locations=classpath:/static/` 让前端构建产物被正确托管
- 后端 API 保持不变，无需任何接口改动

---

## 九、与上一份优化方案的对比

| 上一份方案（原地优化） | 本方案（Vue 3 重构） |
|----------------------|---------------------|
| 抽取 common.js 解决重复代码 | 组件化从根本上消除重复 |
| 内联 CSS/JS 外部化 | 页面本身就是 .vue SFC |
| 手动 DOM 操作 | 声明式渲染，数据驱动视图 |
| 全局变量污染 | 模块化，作用域隔离 |
| 无状态管理 | Pinia 统一管理登录态/聊天状态 |
| 确认框用 browser confirm() | Element Plus 统一组件 |
| 无 TypeScript | 可选，Vue 3 原生支持 |

---

## 十、推荐 UI 组件库：Element Plus

Element Plus 是 Element UI 的 Vue 3 版本，特别适合本项目：
- `el-table` — 替代 admin 页手写 `<table>`
- `el-dialog` — 替代手写模态框
- `el-message` — 替代手写 Toast
- `el-popconfirm` — 替代 `confirm()` 弹窗
- `el-pagination` — 替代手写分页
- `el-skeleton` — 骨架屏加载
- `el-upload` — 替代手写文件上传
- `el-tabs` / `el-select` / `el-input` — 大量现成表单组件

---

## 十一、预估工作量

| 阶段 | 内容 | 难度 |
|------|------|------|
| Phase 1 | 项目初始化、路由、Axios、通用组件 | 1 天 |
| Phase 2-① | 记忆管理页迁移 | 0.5 天 |
| Phase 2-② | 知识库管理页迁移 | 0.5 天 |
| Phase 2-③ | 提示词社区页迁移 | 0.5 天 |
| Phase 2-④ | 管理后台迁移（7 个子页） | 2 天 |
| Phase 2-⑤ | 主聊天页迁移（最复杂） | 2 天 |
| Phase 3 | 清理旧文件、Maven 构建集成、联调 | 1 天 |

*以上为纯前端工作量估算，后端 API 保持不变基本无需改动。*

---

*文档生成日期：2026-06-20*
