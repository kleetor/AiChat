# 持久登录改造计划（修订版）

## 问题诊断

根因：Token 存储在 **`sessionStorage`** 中，`sessionStorage` 是标签页级别的存储，关闭浏览器窗口后数据即被清除。

后端 JWT 过期时间是 **24 小时**（`jwt.expiration=86400000`），关闭窗口后再打开时 Token 本身并未过期，只是前端丢失了它。

---

## 影响范围分析（审计结果）

项目存在 **两套前端体系**，都使用 `sessionStorage` 存储 Token（key: `chat_token`），且共享同一个存储键名：

### React SPA（主要用户端）

| 文件 | 说明 |
|---|---|
| `frontend/src/lib/api.ts` | API 层：`getToken()` / `setToken()` / `clearToken()` 全部使用 `sessionStorage` |
| `frontend/src/lib/auth.tsx` | 认证上下文：`logout()` 调用 `clearToken()`，`refreshUser()` 检查 `getToken()` |

> 开发模式通过 Vite dev server (port 5173) 代理到 Spring Boot (8080)。生产构建 `npm run build` 输出到 `src/main/resources/static/`。

### Thymeleaf 传统页面

| 文件 | 说明 |
|---|---|
| `src/main/resources/static/common.js` | **`ChatCommon.Auth` 模块**：`getToken()` / `setToken()` / `clear()` 全部使用 `sessionStorage`，是所有 Thymeleaf 页面的认证基础 |
| `src/main/resources/static/login.js` | 登录页面：登录成功后调用 `ChatCommon.Auth.setToken()`，检查 `ChatCommon.Auth.isLoggedIn()` |
| `src/main/resources/static/app.js` | 旧版聊天页（可能已被 React 替代但仍存在）：直接读写 `sessionStorage.getItem('chat_token')` |
| `src/main/resources/static/promptHub.js` | Prompt 广场：直接读写 `sessionStorage.getItem('chat_token')` |
| `src/main/resources/static/workshop.js` | 创作工坊：直接读写 `sessionStorage.getItem('chat_token')` |

### 不受影响的页面

| 文件 | 说明 |
|---|---|
| `src/main/resources/static/admin.js` | 管理员页面使用独立的内存变量 `let token = ''`，不使用 `sessionStorage` |
| `src/main/resources/static/memory.js` | 记忆管理页通过 `ChatCommon.Auth` 间接使用 |
| `src/main/resources/static/kbManager.js` | 知识库管理页通过 `ChatCommon.Auth` 间接使用 |

---

## 改造方案

### 核心思路

将所有 Token 存储从 `sessionStorage` 迁移到 `localStorage`，同时引入"记住我"开关让用户选择。

```
sessionStorage（当前）  →  localStorage（改造后）
关闭窗口 Token 丢失    →  Token 持久保留，JWT 有效期内自动登录
```

---

## 改造项一：`common.js` — ChatCommon.Auth 模块升级（P0 必须）

**文件**：`src/main/resources/static/common.js`

**这是最关键的改动**，因为 login.js 登录成功后通过 `ChatCommon.Auth.setToken()` 写入 Token，React 端也从同一 key 读取。统一在这里做存储策略决策。

```javascript
var Auth = {
    TOKEN_KEY: 'chat_token',
    USERNAME_KEY: 'chat_username',
    REMEMBER_KEY: 'chat_remember',

    // 判断当前是否使用 localStorage
    _useLocal: function () {
        return localStorage.getItem(this.REMEMBER_KEY) === 'true';
    },

    // 获取当前应使用的 Storage
    _storage: function () {
        return this._useLocal() ? localStorage : sessionStorage;
    },

    getToken: function () {
        return this._storage().getItem(this.TOKEN_KEY) || '';
    },

    setToken: function (token) {
        this._storage().setItem(this.TOKEN_KEY, token);
        // 同时写入另一个 Storage，保证登录后两种模式都能读到
        var other = this._useLocal() ? sessionStorage : localStorage;
        other.setItem(this.TOKEN_KEY, token);
    },

    setRemember: function (remember) {
        if (remember) {
            localStorage.setItem(this.REMEMBER_KEY, 'true');
        } else {
            localStorage.removeItem(this.REMEMBER_KEY);
        }
    },

    getUsername: function () {
        return this._storage().getItem(this.USERNAME_KEY) || '';
    },

    setUsername: function (name) {
        this._storage().setItem(this.USERNAME_KEY, name);
    },

    clear: function () {
        // 登出时同时清理两种存储，确保无残留
        localStorage.removeItem(this.TOKEN_KEY);
        localStorage.removeItem(this.USERNAME_KEY);
        localStorage.removeItem(this.REMEMBER_KEY);
        sessionStorage.removeItem(this.TOKEN_KEY);
        sessionStorage.removeItem(this.USERNAME_KEY);
    },

    isLoggedIn: function () {
        return !!this.getToken();
    }
};
```

同样，`createApi()` 函数中的 Token 读取也需改为通过 `Auth.getToken()` 或统一使用 `_storage()` 逻辑。

---

## 改造项二：`api.ts` — React API 层 Token 存储升级（P0 必须）

**文件**：`frontend/src/lib/api.ts`

```typescript
const TOKEN_KEY = "chat_token";
const REMEMBER_KEY = "chat_remember";

function isRememberMe(): boolean {
  return localStorage.getItem(REMEMBER_KEY) === "true";
}

function getStorage(): Storage {
  return isRememberMe() ? localStorage : sessionStorage;
}

export function getToken(): string | null {
  return getStorage().getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  getStorage().setItem(TOKEN_KEY, token);
  // 同步写入另一个 Storage，保证不同模式间兼容
  const other = isRememberMe() ? sessionStorage : localStorage;
  other.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REMEMBER_KEY);
}
```

**注意**：改完后需要执行 `cd frontend && npm run build` 重新构建到 `src/main/resources/static/`。

---

## 改造项三：`auth.tsx` — React 登出逻辑微调（P0 必须）

**文件**：`frontend/src/lib/auth.tsx`

`logout()` 已调用 `clearToken()`，无需额外改动。但需确认 `clearToken()` 如改造项二所示同时清理两种存储。

---

## 改造项四：`login.html` — 登录页增加"记住我"复选框（P1 推荐）

**文件**：`src/main/resources/templates/login.html`

在登录表单中，密码输入框下方增加复选框：

```html
<div class="form-group remember-me">
    <label class="checkbox-label">
        <input type="checkbox" id="loginRemember">
        <span>保持登录状态</span>
    </label>
</div>
```

---

## 改造项五：`login.js` — 登录时写入记住我标记（P1 推荐）

**文件**：`src/main/resources/static/login.js`

登录成功回调中增加：

```javascript
// 登录成功
var remember = document.getElementById('loginRemember').checked;
ChatCommon.Auth.setRemember(remember);
ChatCommon.Auth.setToken(data.token);
ChatCommon.Auth.setUsername(data.username);
window.location.href = '/';
```

---

## 改造项六：`app.js` / `promptHub.js` / `workshop.js` — 直接 sessionStorage 引用修正（P1 推荐）

这些文件直接读写 `sessionStorage.getItem('chat_token')`，应改为通过 `ChatCommon.Auth.getToken()`：

**`app.js`** 第 4-5 行：
```javascript
// 改前
let token = sessionStorage.getItem('chat_token') || '';
let username = sessionStorage.getItem('chat_username') || '';

// 改后
let token = ChatCommon.Auth.getToken();
let username = ChatCommon.Auth.getUsername();
```

**`promptHub.js`** 第 2-3 行 / 第 359-360 行：同理改为 `ChatCommon.Auth`

**`workshop.js`** 第 2-3 行 / 第 838-839 行：同理改为 `ChatCommon.Auth`

---

## 改造项七：后端 — JWT 区分有效期（P2 可选）

为"记住我"用户签发更长有效期的 Token。

| 文件 | 改动 |
|---|---|
| `application.properties` | 新增 `jwt.remember-expiration=604800000`（7天） |
| `JwtProperties.java` | 新增 `private long rememberExpiration;` |
| `JwtUtil.java` | 新增 `generateToken(userId, username, role, expiration)` 重载方法 |
| `LoginRequest.java` | 新增 `private Boolean rememberMe;` |
| `AuthController.java` | `login()` 传递 `rememberMe` 给 Service |
| `UserService.java` | `login()` 根据 `rememberMe` 选择不同过期时间 |

```java
// UserService.login() 中
long expiration = Boolean.TRUE.equals(request.getRememberMe())
    ? jwtProperties.getRememberExpiration()
    : jwtProperties.getExpiration();
String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole(), expiration);
```

---

## 安全性分析

| 风险 | 评估 | 缓解措施 |
|---|---|---|
| **XSS 窃取 Token** | localStorage 比 sessionStorage 更易被 XSS 读取 | 已有 CSP 头（`SecurityConfig`），`default-src 'self'`，`script-src` 白名单 |
| **CSRF** | 无状态 JWT 本身不受 CSRF 影响 | 不涉及 Cookie，无需额外 CSRF 防护 |
| **Token 泄漏** | 关闭窗口后 Token 仍保留在 localStorage | 用户可主动登出清除；Token 24h/7d 自动过期；登出时加入黑名单 |
| **公用电脑风险** | 勾选"记住我"后他人可访问 | "记住我"默认为 false（sessionStorage），用户需主动勾选 |
| **TokenBlacklist 一致性** | 登出时 Token 被加入黑名单，Caffeine 缓存过期时间与 JWT 过期时间一致 | 若引入 7 天 Token，Blacklist 缓存也需同步调整 |

### Blacklist 适配（P2 附带）

如果实现了改造项七（区分有效期），`TokenBlacklist` 中 Caffeine 缓存的 `expireAfterWrite` 需从固定的 `jwtProperties.getExpiration()` 改为自适应过期时间：

```java
// TokenBlacklist.java - 将缓存过期设为最长的有效期
this.blacklist = Caffeine.newBuilder()
    .expireAfterWrite(Math.max(
        jwtProperties.getExpiration(),
        jwtProperties.getRememberExpiration()), TimeUnit.MILLISECONDS)
    .build();
```

---

## 实施优先级

| 优先级 | 改造项 | 影响文件 | 工作量 |
|---|---|---|---|
| **P0 必须** | 改造项一：`common.js` Auth 模块 | `common.js` | 中 |
| **P0 必须** | 改造项二：React `api.ts` | `api.ts` + `npm run build` | 小 |
| **P0 必须** | 改造项三：React `auth.tsx` | `auth.tsx` | 极小 |
| **P1 推荐** | 改造项四：登录页 UI | `login.html` | 小 |
| **P1 推荐** | 改造项五：登录逻辑 | `login.js` | 小 |
| **P1 推荐** | 改造项六：传统页面直接引用修正 | `app.js`, `promptHub.js`, `workshop.js` | 小 |
| **P2 可选** | 改造项七：后端区分 Token 有效期 | 5 个后端文件 | 中 |

---

## 构建注意事项

1. React 代码改动后需执行 `cd frontend && npm run build`，输出到 `src/main/resources/static/`
2. Vite 配置 `emptyOutDir: false`，旧 JS 不会自动清理，建议 build 前手动清理无关文件
3. 传统 JS（common.js / login.js 等）直接修改即可，无需构建
4. 验证方式：登录后关闭浏览器 → 重新打开 → 应自动进入已登录状态（无需重新登录）
