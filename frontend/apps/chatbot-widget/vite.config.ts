import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const isSdk = process.env.AGENTX_WIDGET_SDK_BUILD === '1';
  return {
    plugins: [react()],
    build: isSdk
      ? {
          lib: {
            entry: './src/sdk.tsx',
            name: 'AgentXChatbot',
            formats: ['umd'],
            fileName: () => 'sdk.js',
          },
          rollupOptions: {
            external: ['react', 'react-dom/client', '@agentx/chat-core'],
            output: {
              globals: {
                react: 'React',
                'react-dom/client': 'ReactDOM',
                '@agentx/chat-core': 'AgentXChatCore',
              },
            },
          },
          outDir: 'dist',
          emptyOutDir: false,
        }
      : {
          lib: {
            entry: './src/main.tsx',
            name: 'AgentXChatbotWidget',
            formats: ['es'],
            fileName: () => 'main.js',
          },
          outDir: 'dist',
          emptyOutDir: false,
        },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test-setup.ts',
    },
  };
});
