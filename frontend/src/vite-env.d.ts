/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend origin in production, e.g. https://api.example.com. Empty in dev (Vite proxy). */
  readonly VITE_API_URL?: string
  /** Live quiz WebSocket, e.g. wss://api.example.com/ws. Empty in dev (Vite proxy). */
  readonly VITE_WS_URL?: string
}
