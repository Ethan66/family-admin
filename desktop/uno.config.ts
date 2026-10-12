import { defineConfig, presetUno } from 'unocss'

export default defineConfig({
  presets: [presetUno()],
  shortcuts: {
    'page-panel': 'rounded-4 bg-white p-5 shadow-sm ring-1 ring-slate-100',
    'page-heading': 'm-0 text-6 font-700 tracking-tight text-slate-900',
    'muted-text': 'text-3.5 text-slate-500',
  },
})
