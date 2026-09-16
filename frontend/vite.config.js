import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发时把 /api 转发到 Spring Boot,避免跨域
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure(proxy) {
          // Vite 代理默认会缓冲 text/event-stream,导致流式失效;
          // 强制 identity 编码 + no-cache,让 SSE 逐块透传
          proxy.on('proxyRes', (proxyRes, req, res) => {
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              proxyRes.headers['content-encoding'] = 'identity'
              res.setHeader('Cache-Control', 'no-cache')
              res.setHeader('Connection', 'keep-alive')
            }
          })
        },
      },
    },
  },
  build: {
    // 产物直接打进 Spring Boot 的 static 目录,最终一个 jar 整体运行
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
