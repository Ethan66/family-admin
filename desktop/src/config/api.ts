const LOCAL_API_BASE_URL = 'http://localhost:8080/api'

/** 根据启动模式确定 API 地址；服务器模式必须显式配置 HTTP 或 HTTPS 地址。 */
export function resolveApiBaseUrl(mode: string, configuredUrl: string | undefined): string {
  if (mode !== 'server') return LOCAL_API_BASE_URL
  if (!configuredUrl) {
    throw new Error('server 模式必须配置 VITE_API_BASE_URL')
  }

  const tempUrl = new URL(configuredUrl)
  if (tempUrl.protocol !== 'http:' && tempUrl.protocol !== 'https:') {
    throw new Error('server 模式的服务地址必须使用 HTTP 或 HTTPS')
  }
  return tempUrl.href.replace(/\/$/, '')
}

export function getApiBaseUrl(): string {
  return resolveApiBaseUrl(import.meta.env.MODE, import.meta.env.VITE_API_BASE_URL)
}
