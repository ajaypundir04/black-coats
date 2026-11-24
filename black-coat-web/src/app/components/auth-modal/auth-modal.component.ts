import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { LoginRequest, RegisterRequest } from '../../models/auth.model';

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auth-modal.component.html',
  styleUrls: ['./auth-modal.component.css']
})
export class AuthModalComponent {
  loginData: LoginRequest = { email: '', password: '' };
  registerData: RegisterRequest = { email: '', password: '', displayName: '', langPref: 'en' };
  confirmPassword = '';
  isSubmitting = false;

  clientErrors: Record<string, string> = {};

  private readonly EMAIL_REGEX = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

  constructor(public authService: AuthService) {}

  switchTab(tab: 'login' | 'register'): void {
    this.authService.authModalTab.set(tab);
    this.authService.authError.set(null);
    this.clientErrors = {};
  }

  getFieldError(field: string): string | null {
    return this.clientErrors[field] || this.authService.fieldErrors()[field] || null;
  }

  clearFieldError(field: string): void {
    if (this.clientErrors[field]) {
      delete this.clientErrors[field];
    }
    const current = { ...this.authService.fieldErrors() };
    if (current[field]) {
      delete current[field];
      this.authService.fieldErrors.set(current);
    }
  }

  onPasswordInput(): void {
    this.clearFieldError('password');
    if (this.confirmPassword && this.confirmPassword !== this.registerData.password) {
      this.clientErrors['confirmPassword'] = 'Passwords do not match';
    } else {
      delete this.clientErrors['confirmPassword'];
    }
  }

  hasMinLength(): boolean {
    return (this.registerData.password || '').length >= 8;
  }

  hasLetterAndNumber(): boolean {
    const pw = this.registerData.password || '';
    return /[a-zA-Z]/.test(pw) && /\d/.test(pw);
  }

  async onLogin(): Promise<void> {
    this.clientErrors = {};
    const email = (this.loginData.email || '').trim();
    const password = this.loginData.password || '';

    let hasError = false;
    if (!email) {
      this.clientErrors['email'] = 'Email address is required';
      hasError = true;
    } else if (!this.EMAIL_REGEX.test(email)) {
      this.clientErrors['email'] = 'Please enter a valid email address';
      hasError = true;
    }

    if (!password) {
      this.clientErrors['password'] = 'Password is required';
      hasError = true;
    }

    if (hasError) return;

    this.isSubmitting = true;
    try {
      await this.authService.login({ email, password });
      this.loginData = { email: '', password: '' };
    } catch {
      // Error handled in authService signals
    } finally {
      this.isSubmitting = false;
    }
  }

  async onRegister(): Promise<void> {
    this.clientErrors = {};
    const displayName = (this.registerData.displayName || '').trim();
    const email = (this.registerData.email || '').trim();
    const password = this.registerData.password || '';

    let hasError = false;

    // Display Name Validation
    if (displayName && displayName.length > 80) {
      this.clientErrors['displayName'] = 'Name cannot exceed 80 characters';
      hasError = true;
    }

    // Email Validation
    if (!email) {
      this.clientErrors['email'] = 'Email address is required';
      hasError = true;
    } else if (!this.EMAIL_REGEX.test(email)) {
      this.clientErrors['email'] = 'Please enter a valid email address (e.g. advocate@example.com)';
      hasError = true;
    }

    // Password Validation
    if (!password) {
      this.clientErrors['password'] = 'Password is required';
      hasError = true;
    } else if (password.length < 8) {
      this.clientErrors['password'] = 'Password must be at least 8 characters';
      hasError = true;
    } else if (password.length > 72) {
      this.clientErrors['password'] = 'Password cannot exceed 72 characters';
      hasError = true;
    } else if (!/[a-zA-Z]/.test(password) || !/\d/.test(password)) {
      this.clientErrors['password'] = 'Password must contain at least one letter and one number';
      hasError = true;
    }

    // Confirm Password Validation
    if (this.confirmPassword !== password) {
      this.clientErrors['confirmPassword'] = 'Passwords do not match';
      hasError = true;
    }

    if (hasError) return;

    this.isSubmitting = true;
    try {
      await this.authService.register({
        ...this.registerData,
        email,
        password,
        displayName: displayName || 'Advocate'
      });
      this.registerData = { email: '', password: '', displayName: '', langPref: 'en' };
      this.confirmPassword = '';
    } catch {
      // Error handled in authService signals
    } finally {
      this.isSubmitting = false;
    }
  }
}
