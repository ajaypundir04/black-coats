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
  isSubmitting = false;

  constructor(public authService: AuthService) {}

  async onLogin(): Promise<void> {
    if (!this.loginData.email || !this.loginData.password) return;
    this.isSubmitting = true;
    try {
      await this.authService.login(this.loginData);
      this.loginData = { email: '', password: '' };
    } catch {
      // Error handled in authService signal
    } finally {
      this.isSubmitting = false;
    }
  }

  async onRegister(): Promise<void> {
    const email = (this.registerData.email || '').trim();
    const password = this.registerData.password || '';

    if (!email) {
      this.authService.authError.set('Please enter a valid email address');
      return;
    }
    if (password.length < 8) {
      this.authService.authError.set('Password must be at least 8 characters');
      return;
    }

    this.isSubmitting = true;
    try {
      await this.authService.register({
        ...this.registerData,
        email,
        password
      });
      this.registerData = { email: '', password: '', displayName: '', langPref: 'en' };
    } catch {
      // Error handled in authService signal
    } finally {
      this.isSubmitting = false;
    }
  }
}
