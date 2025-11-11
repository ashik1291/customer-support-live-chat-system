import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, signal, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, catchError, interval, of, startWith, switchMap, take } from 'rxjs';
import { ChatApiService } from './chat-api.service';
import { ChatSocketService } from './chat-socket.service';
import { ChatMessage, ConversationMetadata, QueueEntry } from './models';

enum AgentStage {
  Idle = 'IDLE',
  Active = 'ACTIVE',
  Ended = 'ENDED'
}

interface AgentSession {
  agentId: string;
  displayName: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent implements OnInit, OnDestroy {
  readonly AgentStage = AgentStage;

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ChatApiService);
  private readonly socket = inject(ChatSocketService);

  readonly agentSession = signal<AgentSession | null>(null);
  readonly queue = signal<QueueEntry[]>([]);
  readonly activeConversation = signal<ConversationMetadata | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly stage = signal<AgentStage>(AgentStage.Idle);
  readonly statusText = signal('Select a chat request to begin.');
  readonly queueError = signal<string | null>(null);
  readonly chatError = signal<string | null>(null);
  readonly isConnecting = signal(false);
  readonly isSending = signal(false);

  readonly loginForm = this.fb.group({
    agentId: ['', [Validators.required, Validators.minLength(3)]],
    displayName: ['', [Validators.required, Validators.minLength(2)]]
  });

  readonly messageForm = this.fb.group({
    content: ['', [Validators.required, Validators.minLength(1)]]
  });

  private queueSubscription?: Subscription;
  private socketSubscriptions: Subscription[] = [];

  ngOnInit(): void {
    const stored = this.readStoredSession();
    if (stored) {
      this.agentSession.set(stored);
      this.loginForm.patchValue(stored);
      this.startQueuePolling();
    }
  }

  ngOnDestroy(): void {
    this.stopQueuePolling();
    this.clearSocketSubscriptions();
    this.socket.disconnect();
  }

  signIn(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const agentId = this.loginForm.controls.agentId.value?.trim() ?? '';
    const displayName = this.loginForm.controls.displayName.value?.trim() ?? '';
    if (!agentId || !displayName) {
      return;
    }

    const session: AgentSession = { agentId, displayName };
    this.agentSession.set(session);
    this.persistSession(session);
    this.stage.set(AgentStage.Idle);
    this.statusText.set('Pick a chat request to connect with a customer.');
    this.startQueuePolling();
  }

  signOut(): void {
    this.stopQueuePolling();
    this.clearSocketSubscriptions();
    this.socket.disconnect();
    this.agentSession.set(null);
    this.queue.set([]);
    this.activeConversation.set(null);
    this.messages.set([]);
    this.stage.set(AgentStage.Idle);
    this.statusText.set('Signed out.');
    localStorage.removeItem('agentSession');
  }

  joinConversation(entry: QueueEntry): void {
    const session = this.agentSession();
    if (!session || this.isConnecting()) {
      return;
    }

    this.isConnecting.set(true);
    this.chatError.set(null);
    this.api
      .acceptConversation(entry.conversationId, {
        agentId: session.agentId,
        displayName: session.displayName
      })
      .pipe(
        take(1),
        catchError((error) => {
          console.error(error);
          this.chatError.set(this.resolveErrorMessage(error, 'Unable to join this chat request.'));
          this.isConnecting.set(false);
          return of<ConversationMetadata | null>(null);
        })
      )
      .subscribe((conversation) => {
        if (conversation) {
          this.openConversation(conversation);
          this.refreshQueue();
        }
      });
  }

  refreshQueue(): void {
    this.api
      .listQueue()
      .pipe(
        take(1),
        catchError((error) => {
          console.error(error);
          this.queueError.set(this.resolveErrorMessage(error, 'Unable to load the queue.'));
          return of<QueueEntry[]>([]);
        })
      )
      .subscribe((entries) => {
        this.queueError.set(null);
        this.queue.set(entries);
      });
  }

  sendMessage(): void {
    if (this.messageForm.invalid) {
      this.messageForm.markAllAsTouched();
      return;
    }

    const conversation = this.activeConversation();
    const content = this.messageForm.controls.content.value?.trim();
    if (!conversation || !content) {
      return;
    }

    this.isSending.set(true);
    this.chatError.set(null);
    this.socket
      .sendMessage(conversation.id, content)
      .then(() => {
        this.messageForm.reset();
      })
      .catch((error) => {
        console.error(error);
        this.chatError.set(error.message ?? 'Unable to send message.');
      })
      .finally(() => this.isSending.set(false));
  }

  closeConversation(): void {
    const conversation = this.activeConversation();
    const session = this.agentSession();
    if (!conversation || !session || this.stage() === AgentStage.Ended) {
      return;
    }

    this.isSending.set(true);
    this.chatError.set(null);

    this.api
      .closeConversation(conversation.id, {
        agentId: session.agentId,
        displayName: session.displayName
      })
      .pipe(
        take(1),
        catchError((error) => {
          console.error(error);
          this.chatError.set(this.resolveErrorMessage(error, 'Unable to close the conversation.'));
          return of<ConversationMetadata | null>(null);
        })
      )
      .subscribe((result) => {
        this.isSending.set(false);
        if (result) {
          this.addSystemMessage(conversation.id, 'You closed this chat.');
          this.stage.set(AgentStage.Ended);
          this.statusText.set('Conversation closed. Select another request to continue helping customers.');
          this.socket.disconnect();
          this.clearSocketSubscriptions();
          this.refreshQueue();
        }
      });
  }

  trackQueue(_index: number, entry: QueueEntry): string {
    return entry.conversationId;
  }

  trackMessage(_index: number, message: ChatMessage): string {
    return message.id;
  }

  messageClasses(message: ChatMessage): Record<string, boolean> {
    const senderType = message.sender?.type?.toUpperCase();
    return {
      'message-bubble': true,
      'message-agent': senderType === 'AGENT',
      'message-customer': senderType === 'CUSTOMER',
      'message-system': senderType === 'SYSTEM'
    };
  }

  formatTime(value?: string): string {
    if (!value) {
      return '--:--';
    }
    return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  private openConversation(conversation: ConversationMetadata): void {
    const session = this.agentSession();
    if (!session) {
      return;
    }

    this.activeConversation.set(conversation);
    this.messages.set([]);
    this.chatError.set(null);
    this.statusText.set('Connecting to the customer...');
    this.isConnecting.set(true);

    this.clearSocketSubscriptions();
    this.socket.disconnect();

    const handshake$ = this.socket.connectAgent({
      agentId: session.agentId,
      displayName: session.displayName,
      conversationId: conversation.id
    });

    this.socketSubscriptions.push(
      handshake$.pipe(take(1)).subscribe({
        next: (handshake) => {
          this.stage.set(AgentStage.Active);
          this.activeConversation.set(handshake.conversation);
          this.statusText.set('You are now connected. Say hello!');
          this.isConnecting.set(false);
          this.loadHistory(handshake.conversation.id, session.agentId);
        },
        error: (error) => {
          console.error(error);
          this.chatError.set(error.message ?? 'Failed to connect to the chat service.');
          this.statusText.set('Connection failed. Try selecting the request again.');
          this.isConnecting.set(false);
          this.stage.set(AgentStage.Idle);
          this.socket.disconnect();
        }
      })
    );

    this.attachSocketListeners();
  }

  private loadHistory(conversationId: string, agentId: string): void {
    this.socketSubscriptions.push(
      this.api
        .listConversationMessages(conversationId, agentId)
        .pipe(
          take(1),
          catchError((error) => {
            console.error(error);
            this.chatError.set(this.resolveErrorMessage(error, 'Unable to load previous messages.'));
            return of<ChatMessage[]>([]);
          })
        )
        .subscribe((history) => {
          if (history.length) {
            this.messages.update((current) => this.mergeMessages(current, history));
          }
        })
    );
  }

  private attachSocketListeners(): void {
    this.socketSubscriptions.push(
      this.socket.onMessage().subscribe((message) => {
        this.messages.update((current) => this.mergeMessages(current, [message]));
        if (message.sender?.type === 'CUSTOMER') {
          this.statusText.set(`${message.sender.displayName || 'Customer'} is waiting for your reply.`);
        }
        if (message.type?.toUpperCase() === 'SYSTEM') {
          this.stage.set(AgentStage.Ended);
          this.statusText.set(message.content || 'The chat was closed.');
          this.socket.disconnect();
          this.clearSocketSubscriptions();
        }
      })
    );

    this.socketSubscriptions.push(
      this.socket.onDisconnect().subscribe(() => {
        if (this.stage() === AgentStage.Active) {
          this.chatError.set('Connection lost. Reopen the conversation if you need to continue.');
        }
      })
    );

    this.socketSubscriptions.push(
      this.socket.onError().subscribe((error) => this.chatError.set(error))
    );
  }

  private mergeMessages(existing: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
    const byId = new Map<string, ChatMessage>();
    [...existing, ...incoming].forEach((msg) => byId.set(msg.id, msg));
    return Array.from(byId.values()).sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
  }

  private addSystemMessage(conversationId: string, content: string): void {
    const message: ChatMessage = {
      id: `local-${Date.now()}`,
      conversationId,
      type: 'SYSTEM',
      content,
      timestamp: new Date().toISOString(),
      sender: {
        id: 'agent',
        type: 'SYSTEM',
        displayName: 'System'
      }
    };
    this.messages.update((current) => [...current, message]);
  }

  private startQueuePolling(): void {
    this.stopQueuePolling();
    this.refreshQueue();

    this.queueSubscription = interval(5000)
      .pipe(
        startWith(0),
        switchMap(() =>
          this.api.listQueue().pipe(
            catchError((error) => {
              console.error(error);
              this.queueError.set(this.resolveErrorMessage(error, 'Unable to refresh the queue.'));
              return of<QueueEntry[]>([]);
            })
          )
        )
      )
      .subscribe((entries) => {
        this.queueError.set(null);
        this.queue.set(entries);
      });
  }

  private stopQueuePolling(): void {
    this.queueSubscription?.unsubscribe();
    this.queueSubscription = undefined;
  }

  private clearSocketSubscriptions(): void {
    while (this.socketSubscriptions.length) {
      this.socketSubscriptions.pop()?.unsubscribe();
    }
  }

  private readStoredSession(): AgentSession | null {
    try {
      const raw = localStorage.getItem('agentSession');
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as AgentSession;
      if (parsed.agentId && parsed.displayName) {
        return parsed;
      }
    } catch (error) {
      console.warn('Failed to restore agent session', error);
    }
    return null;
  }

  private persistSession(session: AgentSession): void {
    localStorage.setItem('agentSession', JSON.stringify(session));
  }

  private resolveErrorMessage(error: unknown, fallback: string): string {
    if (error && typeof error === 'object') {
      if ('error' in error) {
        const payload = (error as any).error;
        if (typeof payload === 'string') {
          return payload;
        }
        if (payload?.message) {
          return payload.message;
        }
      }
      if ('message' in error && typeof (error as any).message === 'string') {
        return (error as any).message;
      }
    }
    return fallback;
  }
}


