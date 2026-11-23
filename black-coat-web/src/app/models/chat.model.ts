export interface VerifiedCitation {
  sectionId: string;
  tag: string;
  actShortName: string;
  sectionNo: string;
  heading: string;
  textSnippet: string;
  verified: boolean;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  citations?: VerifiedCitation[];
  model?: string;
  latencyMs?: number;
  isStreaming?: boolean;
}

export interface ChatStreamRequest {
  conversationId?: string;
  question: string;
}
