import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Reuse SERVER_PORT from the repo-root .env so the proxy follows the backend port.
  const rootEnv = loadEnv(mode, '..', '')
  const backend = `http://localhost:${rootEnv.SERVER_PORT || '8080'}`

  return {
    plugins: [react(), tailwindcss()],
    server: {
      // Same origin in dev, so CORS is not involved. Dropping the browser's Origin header keeps
      // it that way even when Vite picks another port because 5173 is busy.
      proxy: {
        '/api': {
          target: backend,
          configure: (proxy) => proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
        },
        // Live quiz WebSocket (STOMP).
        '/ws': {
          target: backend,
          ws: true,
          configure: (proxy) => proxy.on('proxyReqWs', (proxyReq) => proxyReq.removeHeader('origin')),
        },
      },
    },
  }
})
