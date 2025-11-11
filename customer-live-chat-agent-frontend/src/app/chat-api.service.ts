import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';
import {
  AgentAcceptRequest,
  AgentActionRequest,
  ChatMessage,
  ConversationMetadata,
  QueueEntry
} from './models';

const API_BASE = environment.apiBaseUrl || '';

@Injectable({
  providedIn: 'root'
})
export class ChatApiService {
  private readonly baseUrl = API_BASE.replace(/\/$/, '');

  constructor(private readonly http: HttpClient) {}

  listQueue(): Observable<QueueEntry[]> {
    return this.http.get<QueueEntry[]>(`${this.baseUrl}/api/agent/queue`);
  }

  listAgentConversations(agentId: string, statuses: string[] = []): Observable<ConversationMetadata[]> {
    const headers = new HttpHeaders({
      'X-Agent-Id': agentId
    });
    let params = new HttpParams();
    statuses.forEach((status) => {
      params = params.append('status', status);
    });
    return this.http.get<ConversationMetadata[]>(`${this.baseUrl}/api/agent/conversations`, {
      headers,
      params
    });
  }

  acceptConversation(conversationId: string, payload: AgentAcceptRequest): Observable<ConversationMetadata> {
    return this.http.post<ConversationMetadata>(
      `${this.baseUrl}/api/agent/conversations/${conversationId}/accept`,
      payload
    );
  }

  listConversationMessages(conversationId: string, agentId: string, limit = 100): Observable<ChatMessage[]> {
    const headers = new HttpHeaders({
      'X-Agent-Id': agentId
    });
    const params = new HttpParams().set('limit', limit);
    return this.http.get<ChatMessage[]>(
      `${this.baseUrl}/api/agent/conversations/${conversationId}/messages`,
      {
        headers,
        params
      }
    );
  }

  sendAgentMessage(
    conversationId: string,
    agentId: string,
    displayName: string,
    content: string,
    type: 'TEXT' | 'SYSTEM' = 'TEXT'
  ): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.baseUrl}/api/conversations/${conversationId}/messages`, {
      senderId: agentId,
      senderDisplayName: displayName,
      senderType: 'AGENT',
      content,
      type
    });
  }

  closeConversation(conversationId: string, payload: AgentActionRequest = {}): Observable<ConversationMetadata> {
    return this.http.post<ConversationMetadata>(
      `${this.baseUrl}/api/agent/conversations/${conversationId}/close`,
      payload
    );
  }
}

