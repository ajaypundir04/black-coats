import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChatService } from '../../services/chat.service';

@Component({
  selector: 'app-citation-drawer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './citation-drawer.component.html',
  styleUrls: ['./citation-drawer.component.css']
})
export class CitationDrawerComponent {
  constructor(public chatService: ChatService) {}
}
