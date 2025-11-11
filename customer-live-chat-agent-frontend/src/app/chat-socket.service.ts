import { Injectable, NgZone } from '@angular/core';
import { io, Socket } from 'socket.io-client';
import { environment } from '../environments/environment';
import { Observable, ReplaySubject, Subject } from 'rxjs';
import { ChatMessage, SocketHandshake } from './models';

const SOCKET_BASE = environment.socketUrl || '';
const MESSAGE_EVENT = 'chat:message';
const SYSTEM_EVENT = 'system:event';

export interface AgentSocketOptions {
  agentId: string;
  displayName: string;
  conversationId: string;
}

@Injectable({ providedIn: 'root' })
export class ChatSocketService {
  private socket?: Socket;
  private handshake$ = new ReplaySubject<SocketHandshake>(1);
  private messages$ = new Subject<ChatMessage>();
  private disconnect$ = new Subject<void>();
  private error$ = new Subject<string>();

  constructor(private readonly zone: NgZone) {}

  connectAgent(options: AgentSocketOptions): Observable<SocketHandshake> {
    this.disconnect();

    this.handshake$ = new ReplaySubject<SocketHandshake>(1);
    this.messages$ = new Subject<ChatMessage>();
    this.disconnect$ = new Subject<void>();
    this.error$ = new Subject<string>();

    this.socket = io(SOCKET_BASE.replace(/\/$/, ''), {
      transports: ['websocket'],
      query: {
        role: 'agent',
        token: options.agentId,
        displayName: options.displayName,
        conversationId: options.conversationId
      }
    });

    this.registerListeners();
    return this.handshake$.asObservable();
  }

  onMessage(): Observable<ChatMessage> {
    return this.messages$.asObservable();
  }

  onDisconnect(): Observable<void> {
    return this.disconnect$.asObservable();
  }

  onError(): Observable<string> {
    return this.error$.asObservable();
  }

  sendMessage(conversationId: string, content: string, type: string = 'TEXT'): Promise<ChatMessage> {
    return new Promise((resolve, reject) => {
      if (!this.socket) {
        reject(new Error('Socket not connected'));
        return;
      }

      this.socket.emit(
        MESSAGE_EVENT,
        { conversationId, content, type },
        (response: ChatMessage | { error?: string }) => {
          if (response && typeof response === 'object' && 'error' in response) {
            reject(new Error(response.error ?? 'Unable to send message'));
          } else {
            resolve(response as ChatMessage);
          }
        }
      );
    });
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = undefined;
    }
  }

  private registerListeners(): void {
    if (!this.socket) {
      return;
    }

    this.socket.on('connect_error', (error: Error) => {
      this.zone.run(() => {
        this.handshake$.error(error);
        this.error$.next(error.message ?? 'Unable to connect to chat service.');
      });
    });

    this.socket.on(SYSTEM_EVENT, (payload: SocketHandshake) => {
      this.zone.run(() => {
        this.handshake$.next(payload);
      });
    });

    this.socket.on(MESSAGE_EVENT, (payload: ChatMessage) => {
      this.zone.run(() => this.messages$.next(payload));
    });

    this.socket.on('system:error', (payload: { message?: string }) => {
      this.zone.run(() => this.error$.next(payload?.message ?? 'Chat service error'));
    });

    this.socket.on('disconnect', () => {
      this.zone.run(() => this.disconnect$.next());
    });
  }
}

