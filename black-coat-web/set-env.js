const fs = require('fs');
const path = require('path');

// Read API URL from environment variable, with fallback to live Render backend
const rawUrl = process.env.API_URL || process.env.BLACKCOAT_API_URL || 'https://black-coats.onrender.com';
const apiUrl = rawUrl.trim().replace(/\/+$/, '');

console.log(`[set-env] Injected Frontend Backend API URL: ${apiUrl}`);

// 1. Generate Angular Environments (src/environments/)
const envDir = path.join(__dirname, 'src', 'environments');
if (!fs.existsSync(envDir)) {
  fs.mkdirSync(envDir, { recursive: true });
}

const prodEnvContent = `export const environment = {
  production: true,
  apiUrl: '${apiUrl}'
};
`;

const devEnvContent = `export const environment = {
  production: false,
  apiUrl: '${apiUrl}'
};
`;

fs.writeFileSync(path.join(envDir, 'environment.prod.ts'), prodEnvContent, 'utf8');
fs.writeFileSync(path.join(envDir, 'environment.ts'), devEnvContent, 'utf8');

// 2. Generate Runtime JS config (src/assets/env.js & assets/env.js)
const assetsDir = path.join(__dirname, 'src', 'assets');
if (!fs.existsSync(assetsDir)) {
  fs.mkdirSync(assetsDir, { recursive: true });
}

const envJsContent = `(function(window) {
  window.__BLACKCOAT_API_URL__ = '${apiUrl}';
})(this);
`;

fs.writeFileSync(path.join(assetsDir, 'env.js'), envJsContent, 'utf8');

// Also write to black-coat-web root for standalone index.html/app.js
fs.writeFileSync(path.join(__dirname, 'env.js'), envJsContent, 'utf8');

console.log('[set-env] Successfully generated environment.ts, environment.prod.ts, and env.js');
