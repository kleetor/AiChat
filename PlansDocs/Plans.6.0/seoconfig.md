# HanaChat SEO 审查报告

> 审查站点：https://www.man8out.xyz
> 审查日期：2026-07-31

---

## 总体评估

SEO 基础配置大部分到位（title、description、OG、Twitter Card、canonical 等均已配置），但存在 **robots.txt 被覆盖** 等关键问题需要修复。

---

## ✅ 已正确配置

| 项目 | 内容 |
|------|------|
| `<title>` | `HanaChat - AI动漫角色扮演社区 \| 多角色独立记忆` |
| `<meta description>` | 已配置，清晰描述产品定位 |
| `<meta keywords>` | `AI聊天,AI角色扮演,动漫角色,AI对话,长期记忆,RAG知识库,多模型AI,大语言模型,AI陪伴,开源AI聊天` |
| `<meta robots>` | 主页 `index, follow`；登录/管理后台 `noindex, nofollow` |
| Open Graph | og:title、og:description、og:image、og:url、og:type 全部配置 |
| Twitter Card | twitter:card、twitter:title、twitter:description、twitter:image 已配置 |
| canonical | 指向 `https://www.man8out.xyz/` |
| `lang="zh-CN"` | 正确设置 |
| 百度站长验证 | `codeva-Y3f4EEt1YR` 已配置 |
| favicon | `/HanaChat.png` 可正常访问 |
| OG/Twitter 图片 | `https://www.man8out.xyz/HanaChat.png` 可正常访问 |

---

## ❌ 问题清单

### P0 - robots.txt 被 Cloudflare 覆盖（严重）

**问题**：项目自定义的 `robots.txt`（位于 `src/main/resources/static/robots.txt`）内容如下：

```
User-agent: *
Allow: /
Disallow: /api/
Disallow: /admin
Disallow: /login

Sitemap: https://www.man8out.xyz/sitemap.xml
```

但生产环境实际返回的是 Cloudflare 注入的 Managed robots.txt 规则，**项目自定义的所有规则（Disallow、Sitemap）全部丢失**。

**影响**：
- 搜索引擎爬虫无法通过 robots.txt 发现 sitemap 地址
- `/api/`、`/admin`、`/login` 等路径没有明确的禁止抓取指令
- 虽然 Cloudflare 注入了 AI 爬虫屏蔽规则，但这不能替代项目自身的 robots.txt

**修复方式**：

1. 在 Cloudflare Dashboard → **规则** → **转换规则** 中，找到 "Managed robots.txt" 相关设置并关闭
2. 或添加一条规则，让 `/robots.txt` 请求绕过 Cloudflare 的注入
3. 验证修复后访问 `https://www.man8out.xyz/robots.txt` 应返回项目自定义内容

---

### P1 - sitemap.xml 只收录首页

**问题**：`sitemap.xml` 目前只包含首页一个 URL：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
    <url>
        <loc>https://www.man8out.xyz/</loc>
        <changefreq>weekly</changefreq>
        <priority>1.0</priority>
    </url>
</urlset>
```

**影响**：`/prompt-hub`、`/workshop`、`/kb-manager`、`/memory-manager` 等子页面无法通过 sitemap 提交给搜索引擎，影响索引效率。

**修复**：将子页面加入 sitemap：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
    <url>
        <loc>https://www.man8out.xyz/</loc>
        <changefreq>weekly</changefreq>
        <priority>1.0</priority>
    </url>
    <url>
        <loc>https://www.man8out.xyz/prompt-hub</loc>
        <changefreq>weekly</changefreq>
        <priority>0.8</priority>
    </url>
    <url>
        <loc>https://www.man8out.xyz/workshop</loc>
        <changefreq>weekly</changefreq>
        <priority>0.8</priority>
    </url>
    <url>
        <loc>https://www.man8out.xyz/kb-manager</loc>
        <changefreq>weekly</changefreq>
        <priority>0.7</priority>
    </url>
    <url>
        <loc>https://www.man8out.xyz/memory-manager</loc>
        <changefreq>weekly</changefreq>
        <priority>0.7</priority>
    </url>
</urlset>
```

---

### P1 - 缺少 JSON-LD 结构化数据

**问题**：全站没有任何 `application/ld+json` 结构化数据。

**影响**：错失 Google 富文本摘要（Rich Snippets）机会，搜索结果展示不够丰富。

**修复**：在 `<head>` 中添加 `WebSite` 和 `Organization` 类型的结构化数据：

```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "WebSite",
  "name": "HanaChat",
  "url": "https://www.man8out.xyz/",
  "description": "HanaChat 是一个支持多动漫角色的AI对话社区，每个角色拥有独立人格与记忆空间。",
  "potentialAction": {
    "@type": "SearchAction",
    "target": "https://www.man8out.xyz/search?q={search_term_string}",
    "query-input": "required name=search_term_string"
  }
}
</script>
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "Organization",
  "name": "HanaChat",
  "url": "https://www.man8out.xyz/",
  "logo": "https://www.man8out.xyz/HanaChat.png"
}
</script>
```

---

### P2 - Twitter Card 类型偏小

**问题**：当前使用 `summary`（小图），显示效果为小尺寸正方形缩略图。

**影响**：在以视觉为主的 AI 角色扮演产品中，社交分享预览图过小，不够吸引眼球。

**修复**：改为 `summary_large_image`：

```html
<meta name="twitter:card" content="summary_large_image" />
```

---

### P2 - 缺少 og:site_name

**问题**：OG 标签中缺少 `og:site_name`。

**修复**：添加：

```html
<meta property="og:site_name" content="HanaChat" />
```

---

### P3 - SPA 架构对 SEO 不利 ✅ 已修复

**问题**：项目是纯客户端 SPA（Vite + React），没有 SSR。页面实际内容为 `<div id="root"></div>` 空壳，全靠 JS 渲染。

**影响**：
- 百度等国内搜索引擎对 JS 渲染支持较差，可能无法正确索引
- Google 虽能渲染 JS，但延迟较高，可能影响抓取效率
- 社交平台爬虫（如微信、QQ）可能无法正确提取内容

**修复方案**：使用 Puppeteer 静态预渲染。构建后启动本地服务器，用 Puppeteer 渲染首页并保存完整 HTML。

**实施**：
- 新增 `frontend/scripts/prerender.js` 预渲染脚本
- 新增 `npm run build:prerender` 命令（构建 + 预渲染）
- 输出 `index.html` 中 `<div id="root">` 包含完整渲染内容（Sidebar、WelcomeScreen 等）
- `frontend/index.html` 保留作为 SEO 标签源，预渲染结果覆盖 `static/index.html`

---
### P0 - robots.txt Cloudflare 覆盖 ✅ 已修复

Cloudflare Managed robots.txt 已关闭。Spring Security 白名单新增 `.txt` 扩展名，`robots.txt` 可正常访问。

---
### P1 - sitemap.xml ✅ 已修复

sitemap 现在收录首页 + `/workshop` 两个页面。

---
### P1 - JSON-LD 结构化数据 ✅ 已修复

`<head>` 中新增 `WebSite` + `Organization` 两种 LD+JSON 结构化数据。

---
### P2 - Twitter Card ✅ 已修复

`twitter:card` 从 `summary` 改为 `summary_large_image`，新增 `og:site_name`。

---

## 需修改的文件（已全部完成）

| 文件 | 修改内容 | 状态 |
|------|----------|------|
| `src/main/resources/static/robots.txt` | 无需修改代码，关闭 Cloudflare Managed robots.txt | ✅ |
| `src/main/java/.../SecurityConfig.java` | 静态资源白名单新增 `.txt` 扩展名 | ✅ |
| `src/main/resources/static/sitemap.xml` | 新增 `/workshop` URL | ✅ |
| `frontend/public/sitemap.xml` | 同上 | ✅ |
| `frontend/index.html` | 新增 JSON-LD、og:site_name、twitter:card 改为 summary_large_image | ✅ |
| `src/main/resources/static/index.html` | 同上 + 预渲染覆盖 | ✅ |
| `frontend/scripts/prerender.js` | **新增** Puppeteer 预渲染脚本 | ✅ |
| `frontend/package.json` | **新增** `build:prerender` 命令 | ✅ |

---

## 优先级汇总

| 优先级 | 问题 | 影响 |
|--------|------|------|
| **P0** | robots.txt 被 Cloudflare 覆盖 | Sitemap 无法被发现，爬虫指令失效 |
| **P1** | sitemap.xml 不完整 | 子页面索引困难 |
| **P1** | 缺少 JSON-LD | 无富文本摘要，搜索展示单一 |
| **P2** | Twitter Card 用 summary | 社交分享图片偏小 |
| **P2** | 缺少 og:site_name | 社交分享信息不完整 |
| **P3** | SPA 无 SSR | 百度等引擎抓取困难 |
