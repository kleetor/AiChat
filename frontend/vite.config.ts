import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/login': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/kb-manager': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/memory-manager': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/prompt-hub': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/workshop': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Thymeleaf 模板引用的 JS/CSS 静态资源 (这些文件仅存在于 Spring Boot static/ 目录)
      '^/(common\\.js|api-paths\\.js|login\\.js|login\\.css|app\\.js|app\\.css|admin\\.js|admin\\.css|memory\\.js|kbManager\\.js|workshop\\.js|workshop\\.css|promptHub\\.js|theme\\.css)': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: false,
    minify: 'esbuild',
    esbuild: {
      // ts-expect-error: Vite types may not expose esbuild.drop, but it's supported by underlying esbuild
      // @ts-ignore
      drop: ['console', 'debugger'],
    },
  },
})
