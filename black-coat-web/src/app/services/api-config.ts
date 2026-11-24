/**
 * Dynamic API Host and Base URL resolver
 * Automatically detects Render, GitHub Pages, and Localhost environments
 * Supports localStorage override via 'blackcoat_api_url'
 */

export function getApiHost(): string {
  if (typeof window === 'undefined') {
    return 'http://localhost:8080';
  }

  // 1. User/Runtime Override in localStorage (purges stale 'black-coat-api' references)
  const stored = localStorage.getItem('blackcoat_api_url');
  if (stored && stored.trim()) {
    if (stored.includes('black-coat-api.onrender.com') || stored.includes('black-coat.onrender.com')) {
      localStorage.removeItem('blackcoat_api_url');
    } else {
      return stored.trim().replace(/\/+$/, '');
    }
  }

  // 2. Global window override (injected by set-env.js / assets/env.js from process.env.API_URL)
  if (typeof window !== 'undefined' && (window as any).__BLACKCOAT_API_URL__) {
    const injected = String((window as any).__BLACKCOAT_API_URL__).trim().replace(/\/+$/, '');
    if (injected && !injected.includes('black-coat-api.onrender.com')) {
      return injected;
    }
  }

  // 3. HTML Meta Tag override (<meta name="api-url" content="...">)
  if (typeof document !== 'undefined') {
    const metaTag = document.querySelector('meta[name="api-url"]');
    if (metaTag) {
      const metaUrl = metaTag.getAttribute('content');
      if (metaUrl && metaUrl.trim() && !metaUrl.includes('black-coat-api.onrender.com')) {
        return metaUrl.trim().replace(/\/+$/, '');
      }
    }
  }

  // 4. Local development
  const hostname = window.location.hostname;
  if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '') {
    return 'http://localhost:8080';
  }

  // 5. Default production backend for Render and GitHub Pages
  return 'https://black-coats.onrender.com';
}

export function setApiHost(url: string | null): void {
  if (!url || !url.trim()) {
    localStorage.removeItem('blackcoat_api_url');
  } else {
    localStorage.setItem('blackcoat_api_url', url.trim().replace(/\/+$/, ''));
  }
}

/**
 * Safely fetches and parses JSON, preventing 'Unexpected end of JSON input'
 */
export async function safeFetchJson<T = any>(url: string, init: RequestInit = {}): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (netErr: any) {
    throw new Error(
      `Cannot connect to backend server at ${url}. ` +
      `If your Render backend is sleeping (cold start) or still deploying, please wait 30 seconds and retry. ` +
      `(${netErr.message || 'Network error'})`
    );
  }

  const rawText = await res.text();
  let data: any = null;

  if (rawText && rawText.trim().length > 0) {
    try {
      data = JSON.parse(rawText);
    } catch {
      // Body is not JSON (e.g. HTML 404/502 page from Render reverse proxy)
      data = { error: rawText.replace(/<[^>]*>/g, ' ').substring(0, 200).trim() };
    }
  }

  if (!res.ok) {
    let errorMsg = data?.error || data?.message;
    if (!errorMsg) {
      if (res.status === 404) {
        errorMsg = `Endpoint not found (HTTP 404) at ${url}. Check your backend API URL configuration.`;
      } else if (res.status === 502 || res.status === 503 || res.status === 504) {
        errorMsg = `Server is currently starting up (HTTP ${res.status}). Render free instances take ~45s to wake up on first request. Please retry in a moment.`;
      } else {
        errorMsg = `Server error HTTP ${res.status} (${res.statusText || 'Request failed'})`;
      }
    }
    const err = new Error(errorMsg) as any;
    if (data?.fieldErrors) {
      err.fieldErrors = data.fieldErrors;
    }
    throw err;
  }

  if (!data) {
    throw new Error(`Server returned an empty response (HTTP ${res.status}) with no data.`);
  }

  return data as T;
}
