import { Injectable, signal } from '@angular/core';
import { ChatMessage, VerifiedCitation } from '../models/chat.model';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly API_HOST = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
    ? 'http://localhost:8080'
    : window.location.origin;

  private readonly API_STREAM_URL = `${this.API_HOST}/api/v1/chat/stream`;

  // Signals
  readonly messages = signal<ChatMessage[]>([]);
  readonly isStreaming = signal<boolean>(false);
  readonly selectedLang = signal<string>('en');
  readonly dailyQuotaRemaining = signal<number>(10);
  readonly emergencyNotice = signal<string | null>(null);

  // Citation Drawer Signals
  readonly isCitationDrawerOpen = signal<boolean>(false);
  readonly selectedCitation = signal<VerifiedCitation | null>(null);
  private citationsCache = new Map<string, VerifiedCitation>();

  constructor(private authService: AuthService) {}

  async streamQuestion(questionText: string): Promise<void> {
    const trimmed = questionText.trim();
    if (!trimmed || this.isStreaming()) return;

    // Add user message
    const userMsgId = crypto.randomUUID();
    this.messages.update(msgs => [
      ...msgs,
      { id: userMsgId, role: 'user', content: trimmed }
    ]);

    // Add placeholder assistant message
    const assistantMsgId = crypto.randomUUID();
    this.messages.update(msgs => [
      ...msgs,
      { id: assistantMsgId, role: 'assistant', content: '', isStreaming: true, citations: [] }
    ]);

    this.isStreaming.set(true);
    let fullAnswer = '';

    try {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json'
      };
      const token = this.authService.token();
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(this.API_STREAM_URL, {
        method: 'POST',
        headers,
        body: JSON.stringify({ question: trimmed })
      });

      if (!response.ok) {
        throw new Error(`HTTP error ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error('Readable stream not supported');

      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        let currentEvent = 'message';

        for (const line of lines) {
          const trimmedLine = line.trim();
          if (trimmedLine.startsWith('event:')) {
            currentEvent = trimmedLine.substring(6).trim();
          } else if (trimmedLine.startsWith('data:')) {
            const dataStr = trimmedLine.substring(5).trim();
            if (dataStr) {
              this.handleSseData(assistantMsgId, currentEvent, dataStr, (token) => {
                fullAnswer += token;
                this.updateMessageContent(assistantMsgId, fullAnswer);
              });
            }
          }
        }
      }

      this.dailyQuotaRemaining.update(q => Math.max(0, q - 1));

    } catch (err: any) {
      this.updateMessageContent(assistantMsgId, `⚠️ Error: ${err.message || 'Stream failed'}. Please check if the server is waking up.`);
    } finally {
      this.messages.update(msgs =>
        msgs.map(m => m.id === assistantMsgId ? { ...m, isStreaming: false } : m)
      );
      this.isStreaming.set(false);
    }
  }

  private handleSseData(
    msgId: string,
    eventName: string,
    dataStr: string,
    onToken: (text: string) => void
  ): void {
    try {
      const data = JSON.parse(dataStr);
      switch (eventName) {
        case 'token':
          if (data.text) onToken(data.text);
          break;

        case 'citations':
          if (Array.isArray(data.citations)) {
            data.citations.forEach((c: VerifiedCitation) => {
              this.citationsCache.set(c.tag, c);
            });
            this.messages.update(msgs =>
              msgs.map(m => m.id === msgId ? { ...m, citations: data.citations } : m)
            );
          }
          break;

        case 'done':
          this.messages.update(msgs =>
            msgs.map(m => m.id === msgId ? { ...m, model: data.provider, latencyMs: data.latencyMs } : m)
          );
          break;

        case 'refusal':
          onToken(`ℹ️ ${data.message}`);
          break;

        case 'error':
          onToken(`⚠️ [${data.code}] ${data.message}`);
          break;
      }
    } catch {
      onToken(dataStr);
    }
  }

  private updateMessageContent(msgId: string, content: string): void {
    this.messages.update(msgs =>
      msgs.map(m => m.id === msgId ? { ...m, content } : m)
    );
  }

  openCitation(tag: string): void {
    const citation = this.citationsCache.get(tag);
    if (citation) {
      this.selectedCitation.set(citation);
      this.isCitationDrawerOpen.set(true);
    }
  }

  closeCitationDrawer(): void {
    this.isCitationDrawerOpen.set(false);
  }

  closeEmergencyNotice(): void {
    this.emergencyNotice.set(null);
  }
}
