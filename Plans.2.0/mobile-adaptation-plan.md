# AI Chat 前端移动端适配计划

## 一、现状分析

### 1.1 项目前端概况

| 类型 | 文件 | 说明 |
|------|------|------|
| HTML 模板 | `index.html` (主聊天), `admin.html`, `memoryManager.html`, `kbManager.html`, `promptHub.html` | Thymeleaf 模板，5 个页面 |
| CSS | `app.css` (~2044行), `admin.css` (~304行), `memory.css` (~192行), `kbManager.css` (~163行), `theme.css` (~76行) | 新拟物化风格 |
| JS | `app.js`, `admin.js`, `promptHub.js`, `memory.js`, `kbManager.js`, `api-paths.js`, `common.js` | 纯原生 JavaScript |

### 1.2 当前移动端适配状况：极差

全项目仅有 **3 处媒体查询**，且覆盖面非常有限：

1. **`app.css` L1935** — `@media (max-width: 700px)`：仅调整侧边栏宽度和弹窗宽度
   ```css
   @media (max-width: 700px) {
     .sidebar { width: 200px; }
     .modal { width: 92%; }
   }
   ```

2. **`memory.css` L158** — `@media (max-width: 640px)`：仅调整头部和记忆列表项方向
   ```css
   @media (max-width: 640px) {
     .header { flex-direction: column; gap: 8px; padding: 12px 16px; }
     .mem-item { flex-direction: column; }
     .mem-item .mem-actions { width: 100%; justify-content: flex-end; }
   }
   ```

3. **`admin.css` L300** — `@media (max-width: 1024px)`：仅调整统计卡片和侧边栏
   ```css
   @media (max-width: 1024px) {
     .stats-grid { grid-template-columns: repeat(2, 1fr); }
     .sidebar { width: 180px; }
   }
   ```

### 1.3 核心适配问题清单

| # | 问题 | 影响页面 | 严重程度 |
|---|------|----------|----------|
| 1 | 侧边栏固定宽度 268px，无折叠/隐藏机制 | index.html | 严重 |
| 2 | 头部按钮过多(~12个)，小屏下溢出/挤压 | index.html | 严重 |
| 3 | 管理员侧边栏 220px 固定定位，无折叠 | admin.html | 严重 |
| 4 | 好友布局固定 900px 宽度 | index.html (好友弹窗) | 严重 |
| 5 | 弹窗内容未针对小屏优化（设置、好友、裁剪等） | 全部 | 高 |
| 6 | 头部用户信息区域元素过多，无响应式隐藏 | index.html | 高 |
| 7 | 消息气泡 max-width: 75%，小屏可用空间更小 | index.html | 中 |
| 8 | 表格横向溢出（仅靠 overflow-x: auto） | admin.html | 中 |
| 9 | 输入区域按钮布局，小屏可能拥挤 | index.html | 中 |
| 10 | 提示词社区卡片网格 minmax(300px, 1fr)，小屏溢出 | promptHub.html | 中 |
| 11 | 触摸目标尺寸不达标（建议 ≥ 44px） | 全部 | 中 |
| 12 | 无 iOS 安全区域 (safe-area-inset-*) 适配 | 全部 | 低 |
| 13 | kbManager / memoryManager 无任何响应式 | kbManager.html, memoryManager.html | 中 |
| 14 | 模态框 max-width: 92% 但内部固定宽度元素未适配 | 全部 | 中 |

---

## 二、适配总策略

### 2.1 断点定义

| 断点 | 名称 | 宽度 | 覆盖设备 |
|------|------|------|----------|
| `--bp-sm` | 小屏手机 | ≤ 480px | iPhone SE, 小屏 Android |
| `--bp-md` | 大屏手机/小平板 | ≤ 768px | iPhone Pro, iPad Mini |
| `--bp-lg` | 平板 | ≤ 1024px | iPad, Android 平板 |
| `--bp-xl` | 桌面 | > 1024px | PC |

### 2.2 适配原则

1. **移动优先 (Mobile First)**：基础样式为移动端设计，通过 `min-width` 媒体查询叠加桌面端增强
2. **渐进增强**：核心功能在移动端必须可用，复杂交互（如拖拽排序）可在桌面端额外提供
3. **触摸友好**：所有可点击元素最小触摸区域 44x44px
4. **内容优先**：聊天消息、输入框等核心内容区域优先显示

---

## 三、逐页面适配方案

### 3.1 主聊天页 (`index.html` + `app.css` + `app.js`)

#### 3.1.1 布局重构

**当前**：`body > .app-container`（flex column） > `.header` + `.main-area`（flex row: `.sidebar`(268px) + `.chat-area`）

**目标**：

```
移动端 (≤ 768px):
┌─────────────────┐
│  Header (精简)   │  ← 仅显示标题 + 汉堡菜单按钮
├─────────────────┤
│                 │
│   Chat Area     │  ← 全宽
│                 │
├─────────────────┤
│   Input Area    │
└─────────────────┘

侧边栏变为滑出式抽屉 (drawer)，通过汉堡菜单按钮打开/关闭。

桌面端 (> 768px):
┌────┬───────────────────┐
│ SB │     Header        │
│    ├───────────────────┤
│ 会 │                   │
│ 话 │    Chat Area      │
│ 列 │                   │
│ 表 ├───────────────────┤
│    │    Input Area     │
└────┴───────────────────┘
```

#### 3.1.2 具体改动

**(A) 头部响应式重构**

移动端 (≤ 768px)：
- 标题保留，字体缩小至 16px
- 用户信息区域折叠为「...」更多菜单（汉堡图标），点击弹出下拉或底部抽屉
- 更多菜单包含：余额、知识库选择器、提示词按钮、模型按钮、记忆按钮、消息按钮、好友按钮、设置、登录/退出
- 联网搜索开关移入更多菜单

CSS 新增：
```css
/* 移动端头部精简 */
@media (max-width: 768px) {
  .header { padding: 10px 14px; }
  .header h1 { font-size: 16px; }
  .header .user-info { display: none; }           /* 默认隐藏按钮行 */
  .header .user-info.show { display: flex; }       /* JS 控制 */

  /* 汉堡菜单按钮 */
  .mobile-menu-btn { display: inline-flex; }       /* 桌面端隐藏 */
  .mobile-menu-btn .lucide { width: 24px; height: 24px; }

  /* 更多菜单面板 (底部抽屉) */
  .mobile-menu-panel {
    position: fixed; bottom: 0; left: 0; right: 0;
    background: linear-gradient(135deg, #eef1f5, #e3e8ef);
    border-radius: 20px 20px 0 0;
    padding: 24px 20px;
    z-index: 200;
    box-shadow: 0 -8px 30px rgba(0,0,0,0.2);
    transform: translateY(100%);
    transition: transform 0.3s ease;
    display: flex; flex-direction: column; gap: 12px;
  }
  .mobile-menu-panel.open { transform: translateY(0); }
  .mobile-menu-mask {
    position: fixed; inset: 0; background: rgba(0,0,0,0.4);
    z-index: 199; display: none;
  }
  .mobile-menu-mask.show { display: block; }
}
```

**(B) 侧边栏 → 滑出式抽屉**

移动端 (≤ 768px)：
- `.sidebar` 默认隐藏 (`transform: translateX(-100%)`)
- 通过汉堡按钮或会话按钮打开，覆盖在聊天区上方
- 覆盖全屏高度，宽度 85vw (max 300px)
- 选择会话后自动关闭抽屉
- 新建会话按钮放在抽屉内顶部

CSS 新增：
```css
@media (max-width: 768px) {
  .sidebar {
    position: fixed; top: 0; left: 0; bottom: 0;
    width: 85vw; max-width: 300px;
    z-index: 150;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }
  .sidebar.open { transform: translateX(0); }
  .sidebar-mask {
    position: fixed; inset: 0; background: rgba(0,0,0,0.4);
    z-index: 149; display: none;
  }
  .sidebar-mask.show { display: block; }

  .chat-area { width: 100%; }
}
```

**(C) 输入区域适配**

移动端 (≤ 480px)：
- textarea 和按钮堆叠排列
- 上传按钮和发送按钮同行显示
- 减小 padding，增大触摸区域

```css
@media (max-width: 480px) {
  .input-area { padding: 10px 12px; gap: 8px; flex-wrap: wrap; }
  .input-area textarea { flex: 1 1 100%; min-height: 40px; }
  .input-area button { padding: 10px 16px; font-size: 14px; }
}
```

**(D) 消息气泡适配**

```css
@media (max-width: 480px) {
  .message .bubble { max-width: 88%; font-size: 14px; padding: 12px 14px; }
  .messages { padding: 16px 12px; }
}
```

**(E) 好友弹窗适配 (friend-layout)**

当前固定 900px 宽 + 33.33% 侧边栏，移动端完全不可用。

```css
@media (max-width: 768px) {
  .friend-layout {
    width: 100vw; height: 100vh; max-width: 100vw; max-height: 100vh;
    border-radius: 0; flex-direction: column;
  }
  .friend-sidebar { width: 100%; height: 45%; }
  .friend-chat-area { height: 55%; }
}
```

**(F) 设置弹窗适配**

```css
@media (max-width: 480px) {
  .settings-tabs { flex-wrap: wrap; }
  .settings-tabs .tab-btn { flex: 1 1 auto; font-size: 12px; padding: 8px 10px; }
  .modal { padding: 20px; border-radius: 16px; }
}
```

**(G) 头像裁剪弹窗适配**

```css
@media (max-width: 480px) {
  .crop-viewport { width: 280px; height: 280px; }
  .crop-modal-box { padding: 18px; }
}
```

---

### 3.2 管理后台 (`admin.html` + `admin.css` + `admin.js`)

#### 3.2.1 整体布局重构

**当前**：固定侧边栏 (220px) + 右侧内容区

**移动端 (≤ 768px)**：
- 侧边栏隐藏，顶部横条导航
- 页面标题 + 汉堡按钮 → 展开侧边栏菜单
- 统计卡片 2 列 → 1 列

```css
@media (max-width: 768px) {
  .sidebar {
    position: fixed; top: 0; left: 0; bottom: 0;
    z-index: 200;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }
  .sidebar.open { transform: translateX(0); }

  .main-area { margin-left: 0; }

  .topbar { padding: 0 14px; position: sticky; top: 0; }
  .topbar .page-title { font-size: 16px; }

  .stats-grid { grid-template-columns: 1fr 1fr; gap: 10px; }
  .stat-card { padding: 14px; }
  .stat-card .stat-value { font-size: 22px; }

  .content { padding: 12px; }
}
```

#### 3.2.2 桌面端进一步优化

```css
/* 平板横屏: 减小侧边栏 */
@media (min-width: 769px) and (max-width: 1024px) {
  :root { --sidebar-width: 180px; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}
```

#### 3.2.3 表格移动端优化

表格在移动端使用 `overflow-x: auto` 是必要的，但可以增加提示：

```css
@media (max-width: 768px) {
  .table-wrap {
    position: relative;
  }
  .table-wrap::after {
    content: "← 左右滑动查看 →";
    display: block;
    text-align: center;
    font-size: 11px;
    color: #9ca3af;
    padding: 6px;
  }
  table th, table td { padding: 8px 10px; font-size: 13px; }
}
```

#### 3.2.4 搜索栏适配

```css
@media (max-width: 480px) {
  .search-bar { flex-direction: column; gap: 8px; }
  .search-bar .search-row { flex-direction: column; align-items: stretch; }
  .search-bar input { width: 100%; }
}
```

---

### 3.3 记忆管理 (`memoryManager.html` + `memory.css`)

当前仅有 640px 断点的基础适配，需要扩展。

```css
/* 加强现有适配 */
@media (max-width: 480px) {
  .container { padding: 12px; }

  /* 统计卡片 2列 */
  .stats-row > div { min-width: calc(50% - 6px); }

  /* 添加栏 */
  .add-bar { flex-direction: column; gap: 8px; }
  .add-bar input { width: 100%; }
  .add-bar button { width: 100%; }

  /* Tab 标签 */
  .tabs { width: 100%; }
  .tabs button { flex: 1; padding: 8px 12px; font-size: 13px; }

  /* 记忆项 */
  .mem-item { flex-direction: column; gap: 10px; }
  .mem-item .mem-actions { width: 100%; justify-content: flex-end; }
}
```

---

### 3.4 知识库管理 (`kbManager.html` + `kbManager.css`)

当前无任何媒体查询，需要完全新增。

```css
@media (max-width: 768px) {
  .header { flex-direction: column; gap: 8px; padding: 12px 16px; }
  .container { padding: 12px; }
  .section-title { flex-direction: column; gap: 8px; align-items: flex-start; }
}

@media (max-width: 480px) {
  .kb-card { flex-direction: column; gap: 12px; }
  .kb-card .actions { width: 100%; justify-content: flex-end; }

  .doc-item { flex-direction: column; gap: 8px; }
  .doc-item .doc-actions { width: 100%; text-align: right; }

  .upload-area { padding: 20px 16px; }
  .modal { padding: 18px; }
}
```

---

### 3.5 提示词社区 (`promptHub.html` + 复用 `app.css`)

```css
@media (max-width: 768px) {
  .hub-container { padding: 12px; }
  .hub-header { flex-direction: column; gap: 12px; padding: 14px 18px; }
  .hub-header h1 { font-size: 18px; }
  .header-actions { width: 100%; justify-content: space-between; }

  .prompts-grid { grid-template-columns: 1fr; gap: 14px; }
  .prompt-card-hub .card-image { height: 140px; }
}

@media (max-width: 480px) {
  .prompts-grid { grid-template-columns: 1fr; }
  .detail-modal .modal { max-height: 90vh; }
  .detail-actions { flex-direction: column; }
}
```

---

## 四、通用组件适配

### 4.1 模态框 (Modal)

所有模态框统一适配：

```css
@media (max-width: 480px) {
  .modal-overlay { align-items: flex-end; }  /* 底部弹出 */
  .modal {
    width: 100%; max-width: 100%;
    border-radius: 20px 20px 0 0;
    padding: 22px 18px;
    max-height: 90vh;
  }
}
```

**例外**：登录/注册弹窗、设置弹窗居中对齐，不做底部弹出。

### 4.2 Toast 通知

```css
@media (max-width: 480px) {
  .toast {
    left: 12px; right: 12px; top: auto; bottom: 80px;
    text-align: center;
  }
}
```

### 4.3 按钮 / 触摸目标

全局确保最小触摸区域：

```css
@media (max-width: 768px) {
  button, .auth-btn, .btn-primary, .btn-secondary, .btn-danger,
  .tab-btn, .close-btn, .settings-btn, .friend-add-btn,
  select, .conv-item, .prompt-card, .mem-item {
    min-height: 44px;   /* iOS HIG 标准 */
  }
}
```

---

## 五、JS 交互层改动

### 5.1 新增移动端 JS 逻辑

需要新增或修改的 JS 功能：

| 功能 | 文件 | 说明 |
|------|------|------|
| 汉堡菜单切换 | `app.js` | 点击汉堡图标展开/收起移动端菜单面板 |
| 侧边栏抽屉 | `app.js` | 移动端点击「会话」按钮或汉堡菜单→会话，滑出侧边栏抽屉 |
| 遮罩层管理 | `app.js` | 点击遮罩关闭抽屉/菜单，需防重复创建 |
| 窗口 resize 监听 | `app.js` | 从移动端切换到桌面端时自动关闭移动端 UI 元素 |
| 管理后台移动端 | `admin.js` | 汉堡按钮切换侧边栏 + 遮罩层 |

### 5.2 关键 JS 改动示例

```js
// app.js 中新增
(function() {
  const MOBILE_BP = 768;
  let isMobile = window.innerWidth <= MOBILE_BP;

  // 窗口大小改变时
  window.addEventListener('resize', () => {
    const wasMobile = isMobile;
    isMobile = window.innerWidth <= MOBILE_BP;
    if (wasMobile && !isMobile) {
      // 从移动端切回桌面端：关闭所有移动端UI
      closeDrawer();
      closeMobileMenu();
    }
  });

  // 汉堡菜单
  function toggleMobileMenu() {
    const panel = document.getElementById('mobileMenuPanel');
    const mask = document.getElementById('mobileMenuMask');
    panel.classList.toggle('open');
    mask.classList.toggle('show');
  }

  // 侧边栏抽屉
  function toggleDrawer() {
    const sidebar = document.querySelector('.sidebar');
    const mask = document.getElementById('sidebarMask');
    sidebar.classList.toggle('open');
    mask.classList.toggle('show');
  }

  // 点击遮罩关闭
  document.getElementById('sidebarMask').addEventListener('click', closeDrawer);
  document.getElementById('mobileMenuMask').addEventListener('click', closeMobileMenu);

  // 暴露到全局
  window.toggleMobileMenu = toggleMobileMenu;
  window.toggleDrawer = toggleDrawer;
})();
```

### 5.3 HTML 模板改动

在 `index.html` 中新增移动端 UI 元素：

```html
<!-- 汉堡菜单按钮 (仅移动端显示) -->
<button class="mobile-menu-btn" onclick="toggleMobileMenu()" style="display:none;">
  <i data-lucide="menu"></i>
</button>

<!-- 移动端更多菜单面板 -->
<div class="mobile-menu-panel" id="mobileMenuPanel">
  <!-- 将所有头部按钮的移动端版本放在此处 -->
</div>
<div class="mobile-menu-mask" id="mobileMenuMask" onclick="closeMobileMenu()"></div>

<!-- 侧边栏遮罩 (仅移动端显示) -->
<div class="sidebar-mask" id="sidebarMask" onclick="closeDrawer()"></div>
```

---

## 六、实施计划

### 第一阶段：基础设施 (优先级：高)

| # | 任务 | 涉及文件 | 工时估计 |
|---|------|----------|----------|
| 1 | 在 `theme.css` 中定义 CSS 断点变量 | `theme.css` | 小 |
| 2 | 新增全局移动端基础样式（触摸目标、safe-area 等） | `theme.css` | 小 |
| 3 | 主聊天页汉堡菜单 + 侧边栏抽屉 HTML/CSS/JS | `index.html`, `app.css`, `app.js` | 中 |
| 4 | 主聊天页头部响应式重构 | `index.html`, `app.css` | 中 |
| 5 | 主聊天页消息气泡 + 输入区域适配 | `app.css` | 小 |

### 第二阶段：核心页面 (优先级：高)

| # | 任务 | 涉及文件 | 工时估计 |
|---|------|----------|----------|
| 6 | 管理后台移动端布局重构（汉堡菜单+横条导航） | `admin.html`, `admin.css`, `admin.js` | 中 |
| 7 | 管理后台表格移动端优化 | `admin.css` | 小 |
| 8 | 记忆管理页全面适配 | `memory.css` | 小 |
| 9 | 知识库管理页全面适配 | `kbManager.css` | 小 |
| 10 | 提示词社区页适配 | `app.css` (hub部分) | 小 |

### 第三阶段：弹窗与细节 (优先级：中)

| # | 任务 | 涉及文件 | 工时估计 |
|---|------|----------|----------|
| 11 | 模态框移动端底部弹出适配 | `app.css`, `admin.css` | 小 |
| 12 | 好友弹窗移动端布局 | `app.css` | 中 |
| 13 | 设置弹窗、裁剪弹窗适配 | `app.css` | 小 |
| 14 | Toast 通知位置适配 | `app.css`, `admin.css`, `memory.css`, `kbManager.css` | 小 |
| 15 | iOS 安全区域适配 (safe-area-inset) | `theme.css` | 小 |

### 第四阶段：测试与优化 (优先级：中)

| # | 任务 | 涉及文件 | 工时估计 |
|---|------|----------|----------|
| 16 | 真机测试 (iOS Safari + Android Chrome) | 全部 | 中 |
| 17 | 横屏适配 (landscape) | 全部 CSS | 小 |
| 18 | 320px 极小屏兼容验证 | 全部 CSS | 小 |
| 19 | 性能检查 (移动端避免 heavy shadows、backdrop-filter) | 全部 CSS | 小 |
| 20 | 登录页/注册页适配 | `index.html`, `app.css` | 小 |

---

## 七、技术注意事项

### 7.1 性能

- 移动端建议禁用 `backdrop-filter: blur()`（在 GPU 较弱的设备上性能开销大），改用 `rgba()` 半透明背景
- 新拟物化的多层 `box-shadow` 在移动端可精简（3层→1层），减少 GPU 合成开销
- 移动端考虑减少渐变色使用，改用纯色替代以降低渲染负担

```css
@media (max-width: 768px) {
  .modal-overlay { backdrop-filter: none; background: rgba(30,35,45,0.6); }
  /* 简化 box-shadow */
  .header, .sidebar, .modal { box-shadow: 0 2px 10px rgba(0,0,0,0.15); }
}
```

### 7.2 iOS Safari 特殊处理

```css
/* 安全区域 */
@supports (padding: env(safe-area-inset-bottom)) {
  .input-area {
    padding-bottom: calc(16px + env(safe-area-inset-bottom));
  }
  .mobile-menu-panel {
    padding-bottom: calc(24px + env(safe-area-inset-bottom));
  }
}

/* 防止 iOS 橡皮筋效果影响抽屉 */
body.drawer-open { overflow: hidden; position: fixed; width: 100%; }
```

### 7.3 触摸滚动

- 抽屉内会话列表需支持触摸滚动：`-webkit-overflow-scrolling: touch`
- 消息列表需保留原生滚动惯性

---

## 八、CSS 架构建议

为避免 CSS 文件膨胀，建议采用以下模式组织响应式代码：

**方式一：分散式（推荐，改动最小）**
在每个 CSS 文件末尾追加对应的 `@media` 块，保持样式就近原则。

**方式二：集中式**
创建 `responsive.css` 统一管理所有媒体查询，减少分散度，但会增加查找成本。

建议选择 **方式一**，因为当前项目 CSS 已按页面拆分，就近管理更清晰。

---

## 九、验收标准

| 标准 | 描述 |
|------|------|
| 布局完整性 | 375px 宽度下所有页面内容可见、可交互，无横向滚动条 |
| 触摸友好 | 所有按钮/链接可在 375px 屏幕上轻松点击，无误触 |
| 功能完整 | 聊天、会话切换、登录注册、设置修改等核心流程在移动端完整可用 |
| 管理后台 | 管理后台核心操作（查看用户、审核赞助）在手机端可用 |
| 弹窗可用 | 所有模态框在移动端正常显示，内容不超出、按钮可点击 |
| 键盘适配 | 移动端输入框聚焦时不被虚拟键盘遮挡 |
| iOS 兼容 | Safari 和微信内置浏览器中布局正常、安全区域正确处理 |
