import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class HealthService {
  private readonly API_HOST = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
    ? 'http://localhost:8080'
    : window.location.origin;

  readonly serverStatus = signal<'checking' | 'waking' | 'online'>('checking');
  readonly statusLabel = signal<string>('Checking Server...');

  constructor() {
    this.pollServerHealth();
  }

  async pollServerHealth(): Promise<void> {
    try {
      const res = await fetch(`${this.API_HOST}/actuator/health`);
      if (res.ok) {
        this.serverStatus.set('online');
        this.statusLabel.set('API Online (JVM)');
      } else {
        throw new Error('Not ready');
      }
    } catch {
      this.serverStatus.set('waking');
      this.statusLabel.set('Server Waking Up...');
      setTimeout(() => this.pollServerHealth(), 4000);
    }
  }
}
