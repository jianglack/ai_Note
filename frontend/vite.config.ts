import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [tailwindcss(), react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, '/');
          if (!normalizedId.includes('node_modules')) return undefined;
          const packagePath = normalizedId.split('node_modules/')[1];
          const parts = packagePath.split('/');
          const packageName = parts[0].startsWith('@') ? `${parts[0]}/${parts[1]}` : parts[0];

          if (['react', 'react-dom', 'react-router', 'react-router-dom'].includes(packageName)) {
            return 'react-vendor';
          }
          if (packageName.startsWith('@tiptap/')
              || packageName.startsWith('prosemirror')
              || packageName === 'lowlight') {
            return 'editor-vendor';
          }
          if (packageName === 'katex') {
            return 'math-vendor';
          }
          if (packageName.startsWith('d3-')) {
            return 'd3-vendor';
          }
          if (packageName === 'recharts'
              || packageName === 'victory-vendor') {
            return 'charts-vendor';
          }
          if (packageName === 'reactflow'
              || packageName.startsWith('@reactflow/')
              || packageName === 'dagre'
              || packageName === 'graphlib') {
            return 'flow-vendor';
          }
          if (['dompurify', 'marked', 'turndown', 'react-markdown', 'remark-gfm', 'remark-parse',
               'remark-rehype', 'unified', 'vfile'].includes(packageName)
              || packageName.startsWith('micromark')
              || packageName.startsWith('mdast-util')
              || packageName.startsWith('hast-util')
              || packageName.startsWith('unist-util')
              || packageName === 'property-information') {
            return 'content-vendor';
          }
          if (packageName === 'highlight.js') {
            return 'highlight-vendor';
          }
          if (packageName.includes('force-graph')
              || packageName === 'kapsule'
              || packageName === 'react-kapsule'
              || packageName === 'accessor-fn'
              || packageName === 'index-array-by') {
            return 'graph-vendor';
          }
          if (packageName === 'zustand'
              || packageName === 'redux'
              || packageName === '@reduxjs/toolkit'
              || packageName === 'react-redux'
              || packageName === 'reselect'
              || packageName === 'immer') {
            return 'state-vendor';
          }
          return undefined;
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        // SSE 流式响应需要禁用代理缓冲
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              // 禁用缓冲，确保 SSE 事件实时到达浏览器
              proxyRes.headers['Cache-Control'] = 'no-cache';
              proxyRes.headers['X-Accel-Buffering'] = 'no';
            }
          });
        }
      }
    }
  }
});

