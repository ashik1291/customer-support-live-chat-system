import { Injectable, NgZone } from '@angular/core';
import { io, Socket } from 'socket.io-client';
import { environment } from '../environments/environment';
import { ChatMessage, SocketHandshake } from './models';
import { Observable, Subject } from 'rxjs';

interface CustomerSocketOptions {
  conversationId: string;
  customerId: string;
  displayName?: string;
  fingerprint: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatSocketService {
  private socket?: Socket;
  private readonly messageSubject = new Subject<ChatMessage>();
  private readonly handshakeSubject = new Subject<SocketHandshake>();
  private readonly errorSubject = new Subject<string>();
  private readonly disconnectSubject = new Subject<void>();

  constructor(private readonly zone: NgZone) {}

  connectCustomer(options: CustomerSocketOptions): void {
    this.disconnect();

    const query: Record<string, string> = {
      role: 'customer',
      token: options.customerId,
      displayName: options.displayName ?? '',
      conversationId: options.conversationId,
      fingerprint: options.fingerprint
    };

    this.socket = io(environment.socketUrl, {
      transports: ['websocket'],
      autoConnect: true,
      query
    });

    this.socket.on('connect_error', (err) => {
      this.zone.run(() => this.errorSubject.next(err.message ?? 'Unable to connect to chat service.'));
    });

    this.socket.on('disconnect', () => {
      this.zone.run(() => this.disconnectSubject.next());
    });

    this.socket.on('system:error', (payload: { message?: string }) => {
      this.zone.run(() => this.errorSubject.next(payload?.message ?? 'Chat service error'));
    });

    this.socket.on('system:event', (payload: SocketHandshake) => {
      this.zone.run(() => this.handshakeSubject.next(payload));
    });

    this.socket.on('chat:message', (payload: ChatMessage) => {
      this.zone.run(() => this.messageSubject.next(payload));
    });
  }

  sendMessage(conversationId: string, content: string, type: string = 'TEXT'): Promise<void> {
    return new Promise((resolve, reject) => {
      if (!this.socket || !this.socket.connected) {
        reject(new Error('Not connected to chat service'));
        return;
      }

      this.socket.emit(
        'chat:message',
        {
          conversationId,
          content,
          type
        },
        (ack: unknown) => {
          if (ack && typeof ack === 'object' && 'error' in ack) {
            reject(new Error((ack as any).error ?? 'Failed to send message'));
          } else {
            resolve();
          }
        }
      );
    });
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.removeAllListeners();
      this.socket.disconnect();
      this.socket = undefined;
    }
  }

  onMessage(): Observable<ChatMessage> {
    return this.messageSubject.asObservable();
  }

  onHandshake(): Observable<SocketHandshake> {
    return this.handshakeSubject.asObservable();
  }

  onError(): Observable<string> {
    return this.errorSubject.asObservable();
  }

  onDisconnect(): Observable<void> {
    return this.disconnectSubject.asObservable();
  }
}

