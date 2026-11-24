import { Injectable, signal, computed } from '@angular/core';
import { User, AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';
import { getApiHost, safeFetchJson } from './api-config';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly TOKEN_KEY = 'blackcoat_access_token';
  private readonly REFRESH_KEY = 'blackcoat_refresh_token';
  private readonly USER_KEY = 'blackcoat_user';

  get apiBase(): string {
    return `${getApiHost()}/api/v1/auth`;
  }

  // Reactive Signals
  readonly token = signal<string | null>(localStorage.getItem(this.TOKEN_KEY));
  readonly user = signal<User | null>(this.loadStoredUser());
  readonly isAuthenticated = computed(() => !!this.token());

  readonly isAuthModalOpen = signal<boolean>(false);
  readonly authModalTab = signal<'login' | 'register'>('login');
  readonly authError = signal<string | null>(null);

  async login(credentials: LoginRequest): Promise<void> {
    this.authError.set(null);
    try {
      const data = await safeFetchJson<AuthResponse>(`${this.apiBase}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials)
      });

      this.setSession(data);
      this.isAuthModalOpen.set(false);
    } catch (err: any) {
      this.authError.set(err.message || 'Login failed');
      throw err;
    }
  }

  async register(req: RegisterRequest): Promise<void> {
    this.authError.set(null);
    try {
      const data = await safeFetchJson<AuthResponse>(`${this.apiBase}/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req)
      });

      this.setSession(data);
      this.isAuthModalOpen.set(false);
    } catch (err: any) {
      this.authError.set(err.message || 'Registration failed');
      throw err;
    }
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.token.set(null);
    this.user.set(null);
  }

  openAuthModal(tab: 'login' | 'register' = 'login'): void {
    this.authModalTab.set(tab);
    this.authError.set(null);
    this.isAuthModalOpen.set(true);
  }

  closeAuthModal(): void {
    this.isAuthModalOpen.set(false);
  }

  private setSession(authData: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, authData.accessToken);
    localStorage.setItem(this.REFRESH_KEY, authData.refreshToken);
    localStorage.setItem(this.USER_KEY, JSON.stringify(authData.user));
    this.token.set(authData.accessToken);
    this.user.set(authData.user);
  }

  private loadStoredUser(): User | null {
    try {
      const str = localStorage.getItem(this.USER_KEY);
      return str ? JSON.parse(str) : null;
    } catch {
      return null;
    }
  }
}
