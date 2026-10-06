import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const FLAGS_DIR = fileURLToPath(new URL('./node_modules/country-flag-icons/3x2', import.meta.url));

/**
 * Serves country flags as separate files at /flags/XX.svg, so the phone field
 * only downloads the flags actually shown instead of bundling all ~250.
 */
function countryFlags(): Plugin {
  return {
    name: 'country-flags',
    configureServer(server) {
      server.middlewares.use('/flags', (req, res, next) => {
        const code = /^\/([A-Z]{2})\.svg$/.exec(req.url ?? '')?.[1];
        if (!code) {
          next();
          return;
        }
        try {
          res.setHeader('Content-Type', 'image/svg+xml');
          res.end(readFileSync(`${FLAGS_DIR}/${code}.svg`));
        } catch {
          next();
        }
      });
    },
    generateBundle() {
      for (const file of readdirSync(FLAGS_DIR).filter((name) => /^[A-Z]{2}\.svg$/.test(name))) {
        this.emitFile({ type: 'asset', fileName: `flags/${file}`, source: readFileSync(`${FLAGS_DIR}/${file}`) });
      }
    },
  };
}

export default defineConfig({
  plugins: [react(), countryFlags()],
  resolve: {
    // import.meta.url rather than __dirname: this config is loaded as ESM.
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // In development the app talks to the backend through this proxy, so the
    // browser sees a single origin and no CORS preflight is involved.
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
