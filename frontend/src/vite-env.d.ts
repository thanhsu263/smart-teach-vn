/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend origin in production, e.g. https://api.example.com. Empty in dev (Vite proxy). */
  readonly VITE_API_URL?: string
  /** Live quiz WebSocket, e.g. wss://api.example.com/ws. Empty in dev (Vite proxy). */
  readonly VITE_WS_URL?: string
  /** Public demo teacher. When both are set, the login page shows a one-click demo button. */
  readonly VITE_DEMO_EMAIL?: string
  readonly VITE_DEMO_PASSWORD?: string
}
