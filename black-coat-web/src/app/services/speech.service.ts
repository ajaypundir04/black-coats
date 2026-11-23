import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class SpeechService {
  readonly isListening = signal<boolean>(false);
  readonly transcript = signal<string>('');
  readonly isSupported = signal<boolean>(false);

  private recognition: any = null;

  constructor() {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SpeechRecognition) {
      this.isSupported.set(true);
      this.recognition = new SpeechRecognition();
      this.recognition.continuous = false;
      this.recognition.interimResults = false;

      this.recognition.onstart = () => this.isListening.set(true);
      this.recognition.onend = () => this.isListening.set(false);
      this.recognition.onerror = () => this.isListening.set(false);
      this.recognition.onresult = (event: any) => {
        const text = event.results[0][0].transcript;
        this.transcript.set(text);
      };
    }
  }

  toggleListening(lang: string = 'en'): void {
    if (!this.recognition) return;

    if (this.isListening()) {
      this.recognition.stop();
    } else {
      this.recognition.lang = lang === 'hi' ? 'hi-IN' : 'en-IN';
      this.recognition.start();
    }
  }
}
