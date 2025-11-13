import { Injectable, NgZone } from '@angular/core';
import { io, Socket } from 'socket.io-client';
import { Observable, ReplaySubject, Subject } from 'rxjs';
import { environment } from '../environments/environment';
import { QueueEntry } from './models';

const SOCKET_BASE = environment.socketUrl || '';
const QUEUE_EVENT = 'queue:snapshot';

@Injectable({ providedIn: 'root' })
export class QueueSocketService {
  private socket?: Socket;
  private readonly snapshots$ = new ReplaySubject<QueueEntry[]>(1);
  private readonly errors$ = new Subject<string>();

  constructor(private readonly zone: NgZone) {}

  connect(agentId: string, displayName: string): void {
    if (this.socket) {
      return;
    }

    const socket = io(SOCKET_BASE.replace(/\/$/, ''), {
      transports: ['websocket'],
      query: {
        role: 'agent',
        token: agentId,
        displayName,
        scope: 'queue'
      }
    });

    socket.on('connect_error', (error: Error) => {
      this.zone.run(() => this.errors$.next(error.message ?? 'Unable to connect to queue updates.'));
    });

    socket.on('system:error', (payload: { message?: string }) => {
      this.zone.run(() => this.errors$.next(payload?.message ?? 'Queue update error'));
    });

    socket.on(QUEUE_EVENT, (payload: QueueEntry[]) => {
      this.zone.run(() => this.snapshots$.next(Array.isArray(payload) ? payload : []));
    });

    socket.on('disconnect', () => {
      this.zone.run(() => {
        this.snapshots$.next([]);
        this.errors$.next('Disconnected from queue updates.');
        this.destroy();
      });
    });

    this.socket = socket;
  }

  snapshots(): Observable<QueueEntry[]> {
    return this.snapshots$.asObservable();
  }

  errors(): Observable<string> {
    return this.errors$.asObservable();
  }

  disconnect(): void {
    this.destroy();
  }

  private destroy(): void {
    if (this.socket) {
      this.socket.removeAllListeners();
      this.socket.disconnect();
      this.socket = undefined;
    }
  }
}
