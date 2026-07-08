import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const proxyTarget = process.env.VITE_API_PROXY_TARGET ?? "http://localhost:8080";

export default defineConfig({
    plugins: [vue()],
    build: {
        rollupOptions: {
            output: {
                manualChunks: {
                    "vue-vendor": ["vue", "vue-router"],
                    "ui-vendor": ["ant-design-vue"]
                }
            }
        }
    },
    server: {
        port: 5173,
        proxy: {
            "/api": {
                target: proxyTarget,
                changeOrigin: true
            }
        }
    },
    test: {
        environment: "happy-dom"
    }
});
