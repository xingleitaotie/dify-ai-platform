import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import customEventPlugin from './vite-plugin-customevent.js'

export default defineConfig(({ command, mode }) => {
    // 根据环境获取 API 地址
    const apiTarget = mode === 'docker'
        ? 'http://dify-api-gateway:9000'  // Docker 环境
        : 'http://localhost:9000'          // 本地开发环境

    console.log(`[Vite] 运行模式: ${mode}, API 代理目标: ${apiTarget}`)

    return {
        plugins: [
            vue(),
            customEventPlugin()
        ],
        resolve: {
            alias: {
                '@': path.resolve(__dirname, './src')
            }
        },
        server: {
            port: 3000,
            host: '0.0.0.0',  // 允许外部访问
            proxy: {
                '/api': {
                    target: apiTarget,
                    changeOrigin: true,
                    rewrite: (path) => path,
                    configure: (proxy, options) => {
                        proxy.on('error', (err, req, res) => {
                            console.log('proxy error', err);
                        });
                        proxy.on('proxyReq', (proxyReq, req, res) => {
                            console.log('Sending Request to the Target:', req.method, req.url);
                        });
                        proxy.on('proxyRes', (proxyRes, req, res) => {
                            console.log('Received Response from the Target:', proxyRes.statusCode, req.url);
                        });
                    }
                }
            },
            // 热更新配置
            hmr: {
                overlay: true
            }
        },
        build: {
            outDir: 'dist',
            assetsDir: 'assets',
            sourcemap: false,
            // 生产环境优化
            minify: 'terser',
            terserOptions: {
                compress: {
                    drop_console: mode === 'production',
                    drop_debugger: true
                }
            },
            rollupOptions: {
                output: {
                    chunkFileNames: 'js/[name]-[hash].js',
                    entryFileNames: 'js/[name]-[hash].js',
                    assetFileNames: 'assets/[ext]/[name]-[hash].[ext]'
                }
            }
        },
        // 环境变量前缀
        envPrefix: 'VITE_'
    }
})