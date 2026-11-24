import { Injectable, signal } from '@angular/core';
import { getApiHost, setApiHost } from './api-config';

@Injectable({
  providedIn: 'root'
})
export class HealthService {
  readonly serverStatus = signal<'checking' | 'waking' | 'online' | 'error'>('checking');
  readonly statusLabel = signal<string>('Checking Server...');
  readonly currentApiUrl = signal<string>(getApiHost());

  private pollTimer: any = null;

  constructor() {
    this.pollServerHealth();
  }

  get apiHost(): string {
    return getApiHost();
  }

  updateApiUrl(newUrl: string | null): void {
    setApiHost(newUrl);
    this.currentApiUrl.set(getApiHost());
    this.pollServerHealth();
  }

  async pollServerHealth(): Promise<void> {
    if (this.pollTimer) clearTimeout(this.pollTimer);
    const host = getApiHost();
    this.currentApiUrl.set(host);

    try {
      const res = await fetch(`${host}/actuator/health`, { method: 'GET' });
      if (res.ok) {
        this.serverStatus.set('online');
        this.statusLabel.set('API Online (JVM)');
      } else {
        throw new Error(`HTTP ${res.status}`);
      }
    } catch {
      this.serverStatus.set('waking');
      this.statusLabel.set('Server Waking Up...');
      this.pollTimer = setTimeout(() => this.pollServerHealth(), 5000);
    }
  }
}
