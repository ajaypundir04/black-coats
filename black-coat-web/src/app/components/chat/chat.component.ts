import { Component, ElementRef, ViewChild, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../services/chat.service';
import { SpeechService } from '../../services/speech.service';
import { VerifiedCitation } from '../../models/chat.model';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.css']
})
export class ChatComponent {
  @ViewChild('chatScroll') chatScroll!: ElementRef<HTMLDivElement>;
  @ViewChild('chatInputEl') chatInputEl!: ElementRef<HTMLTextAreaElement>;

  questionText = '';
  readonly inputError = signal<string | null>(null);

  readonly promptChips = [
    { title: '⚖️ Punishment for theft under BNS', prompt: 'What is the definition and punishment for theft under Bharatiya Nyaya Sanhita?' },
    { title: '📖 Murder vs Culpable Homicide under BNS', prompt: 'What is the legal difference between murder and culpable homicide under BNS 2023?' },
    { title: '🛡️ Legal rights during police arrest', prompt: 'What are a citizen\'s legal rights if detained or arrested by police without warrant?' },
    { title: '💼 Cheating under BNS Section 318', prompt: 'What constitutes cheating under Section 318 of Bharatiya Nyaya Sanhita?' }
  ];

  constructor(
    public chatService: ChatService,
    public speechService: SpeechService
  ) {
    // Sync speech transcript into questionText
    effect(() => {
      const transcript = this.speechService.transcript();
      if (transcript) {
        this.questionText = transcript;
        this.adjustTextareaHeight();
      }
    });

    // Auto-scroll when messages update
    effect(() => {
      this.chatService.messages();
      setTimeout(() => this.scrollToBottom(), 50);
    });
  }

  onInputChange(): void {
    if (this.inputError()) {
      this.inputError.set(null);
    }
    this.adjustTextareaHeight();
  }

  onSubmit(): void {
    this.inputError.set(null);
    const trimmed = this.questionText.trim();
    if (this.chatService.isStreaming()) return;

    if (!trimmed) {
      this.inputError.set('Please enter a legal query');
      return;
    }
    if (trimmed.length < 3) {
      this.inputError.set('Query must be at least 3 characters');
      return;
    }
    if (trimmed.length > 2000) {
      this.inputError.set('Query cannot exceed 2000 characters');
      return;
    }

    const query = trimmed;
    this.questionText = '';
    this.adjustTextareaHeight();
    this.chatService.streamQuestion(query);
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.onSubmit();
    }
  }

  selectPrompt(prompt: string): void {
    this.chatService.streamQuestion(prompt);
  }

  onVoiceClick(): void {
    this.speechService.toggleListening(this.chatService.selectedLang());
  }

  openCitation(tag: string): void {
    this.chatService.openCitation(tag);
  }

  formatContent(text: string): string {
    if (!text) return '';
    // Highlight [S#] citation tags
    return text.replace(/\[S(\d+)\]/g, '<span class="citation-chip-tag" data-tag="[S$1]">[S$1]</span>');
  }

  onContentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (target && target.classList.contains('citation-chip-tag')) {
      const tag = target.getAttribute('data-tag');
      if (tag) this.openCitation(tag);
    }
  }

  private adjustTextareaHeight(): void {
    if (this.chatInputEl) {
      const el = this.chatInputEl.nativeElement;
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 120) + 'px';
    }
  }

  private scrollToBottom(): void {
    if (this.chatScroll) {
      this.chatScroll.nativeElement.scrollTop = this.chatScroll.nativeElement.scrollHeight;
    }
  }
}
