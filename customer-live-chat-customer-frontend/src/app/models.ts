export interface ChatParticipant {
  id: string;
  type: 'CUSTOMER' | 'AGENT' | 'SYSTEM' | string;
  displayName?: string;
  metadata?: Record<string, unknown>;
}

export type ConversationStatus = 'OPEN' | 'QUEUED' | 'ASSIGNED' | 'CLOSED';

export interface ConversationMetadata {
  id: string;
  status: ConversationStatus;
  customer?: ChatParticipant | null;
  agent?: ChatParticipant | null;
  createdAt?: string;
  updatedAt?: string;
  acceptedAt?: string;
  closedAt?: string;
  attributes?: Record<string, unknown>;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  type: string;
  sender?: ChatParticipant | null;
  content: string;
  metadata?: Record<string, unknown>;
  timestamp: string;
}

export interface QueueStatusResponse {
  position: number;
  estimatedWait?: string;
}

export interface CreateConversationPayload {
  channel: string;
  displayName?: string;
  email?: string;
  subject?: string;
  [key: string]: unknown;
}

export interface SendMessagePayload {
  senderId: string;
  senderDisplayName?: string;
  senderType?: string;
  content: string;
  type?: string;
  metadata?: Record<string, unknown>;
}

export interface SocketHandshake {
  participant: ChatParticipant;
  conversation: ConversationMetadata;
}

export interface ChatQueueInfo {
  position: number;
  message?: string;
}

