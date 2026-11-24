import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { ChatService } from '../../services/chat.service';
import { HealthService } from '../../services/health.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css']
})
export class NavbarComponent {
  constructor(
    public authService: AuthService,
    public chatService: ChatService,
    public healthService: HealthService
  ) {}

  onLangChange(event: Event): void {
    const val = (event.target as HTMLSelectElement).value;
    this.chatService.selectedLang.set(val);
  }

  configureApiUrl(): void {
    const current = this.healthService.apiHost;
    const promptVal = prompt(
      `Current Backend API URL: ${current}\n\nEnter backend API endpoint URL (or leave blank for auto-detected):`,
      current
    );
    if (promptVal !== null) {
      this.healthService.updateApiUrl(promptVal.trim() || null);
    }
  }
}
