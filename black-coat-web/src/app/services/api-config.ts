/**
 * Dynamic API Host and Base URL resolver
 * Automatically detects Render, GitHub Pages, and Localhost environments
 * Supports localStorage override via 'blackcoat_api_url'
 */

export function getApiHost(): string {
  if (typeof window === 'undefined') {
    return 'http://localhost:8080';
  }

  // 1. User/Runtime Override in localStorage
  const stored = localStorage.getItem('blackcoat_api_url');
  if (stored && stored.trim()) {
    return stored.trim().replace(/\/+$/, '');
  }

  // 2. Global window override (injected by host)
  if ((window as any).__BLACKCOAT_API_URL__) {
    return String((window as any).__BLACKCOAT_API_URL__).trim().replace(/\/+$/, '');
  }

  // 3. Local development
  const hostname = window.location.hostname;
  if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '') {
    return 'http://localhost:8080';
  }

  // 4. Render deployment: if host is <name>-web.onrender.com -> API is <name>-api.onrender.com
  if (hostname.includes('.onrender.com')) {
    if (hostname.includes('-web.')) {
      return `https://${hostname.replace('-web.', '-api.')}`;
    }
    return 'https://black-coat-api.onrender.com';
  }

  // 5. Default production backend for GitHub Pages or other static hosting
  return 'https://black-coat-api.onrender.com';
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
    throw new Error(errorMsg);
  }

  if (!data) {
    throw new Error(`Server returned an empty response (HTTP ${res.status}) with no data.`);
  }

  return data as T;
}
