/**
 * SEO 预渲染脚本
 * 构建后启动本地静态服务器，用 Puppeteer 渲染首页并保存 HTML，
 * 替代原本空的 <div id="root"></div>，让搜索引擎能抓取到页面内容。
 */
import puppeteer from "puppeteer";
import http from "http";
import { readFileSync, writeFileSync, existsSync } from "fs";
import { resolve, extname } from "path";

const PORT = 4173;
const STATIC_DIR = resolve(import.meta.dirname, "../../src/main/resources/static");
const OUTPUT_PATH = resolve(STATIC_DIR, "index.html");

const MIME_TYPES = {
  ".html": "text/html",
  ".js": "application/javascript",
  ".css": "text/css",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".json": "application/json",
};

function startServer() {
  return new Promise((resolveServer) => {
    const server = http.createServer((req, res) => {
      let filePath = resolve(STATIC_DIR, req.url.slice(1) || "index.html");
      if (!existsSync(filePath)) {
        // SPA fallback: 非静态资源返回 index.html
        filePath = resolve(STATIC_DIR, "index.html");
      }
      const ext = extname(filePath).toLowerCase();
      const mime = MIME_TYPES[ext] || "application/octet-stream";

      try {
        const content = readFileSync(filePath);
        res.writeHead(200, { "Content-Type": mime });
        res.end(content);
      } catch {
        res.writeHead(404);
        res.end("Not Found");
      }
    });

    server.listen(PORT, () => {
      console.log(`[prerender] 静态服务器: http://localhost:${PORT}`);
      resolveServer(server);
    });
  });
}

async function prerender() {
  const server = await startServer();

  console.log("[prerender] 启动 Puppeteer...");
  const browser = await puppeteer.launch({
    headless: true,
    args: ["--no-sandbox", "--disable-setuid-sandbox"],
  });

  try {
    const page = await browser.newPage();
    await page.goto(`http://localhost:${PORT}/`, {
      waitUntil: "networkidle0",
      timeout: 30000,
    });

    // 等待 React 渲染完成
    await page.waitForSelector("#root > div", { timeout: 10000 });
    // 额外等待异步内容加载完成
    await new Promise((r) => setTimeout(r, 2000));

    const html = await page.content();
    writeFileSync(OUTPUT_PATH, html, "utf-8");

    console.log(`[prerender] 预渲染完成: ${OUTPUT_PATH}`);
  } finally {
    await browser.close();
    server.close();
    console.log("[prerender] 清理完成");
  }
}

prerender().catch((err) => {
  console.error("[prerender] 失败:", err);
  process.exit(1);
});
