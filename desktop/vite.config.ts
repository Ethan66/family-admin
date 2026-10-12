import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

export default defineConfig(({ mode }) => {
  const tempEnv = loadEnv(mode, process.cwd(), 'VITE_')
  const elementPlusResolver = ElementPlusResolver({ importStyle: process.env.VITEST === 'true' ? false : 'css' })
  if (mode === 'server') {
    if (!tempEnv.VITE_API_BASE_URL) {
      throw new Error('server 模式必须配置 VITE_API_BASE_URL')
    }
    if (new URL(tempEnv.VITE_API_BASE_URL).protocol !== 'https:') {
      throw new Error('server 模式的服务地址必须使用 HTTPS')
    }
  }

  return {
    plugins: [
      vue(),
      UnoCSS(),
      AutoImport({ resolvers: [elementPlusResolver] }),
      Components({ resolvers: [elementPlusResolver] }),
    ],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    clearScreen: false,
    server: { port: 1420, strictPort: true },
    envPrefix: ['VITE_', 'TAURI_ENV_*'],
    build: { target: 'chrome105', sourcemap: false },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      clearMocks: true,
    },
  }
})
