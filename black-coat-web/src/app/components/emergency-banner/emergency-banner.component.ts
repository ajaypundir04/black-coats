import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatService } from '../../services/chat.service';

@Component({
  selector: 'app-emergency-banner',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './emergency-banner.component.html',
  styleUrls: ['./emergency-banner.component.css']
})
export class EmergencyBannerComponent {
  constructor(public chatService: ChatService) {}
}
