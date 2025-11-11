import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, catchError, finalize, of, switchMap, take } from 'rxjs';
import { ChatApiService } from './chat-api.service';
import { ChatSocketService } from './chat-socket.service';
import {
  ChatMessage,
  ConversationMetadata,
  CreateConversationPayload,
  QueueStatusResponse,
  SocketHandshake
} from './models';

enum ChatStage {
  Idle = 'IDLE',
  Confirm = 'CONFIRM',
  Connecting = 'CONNECTING',
  Waiting = 'WAITING',
  Active = 'ACTIVE',
  Ended = 'ENDED'
}

interface StoredCustomerSession {
  token: string;
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
  readonly ChatStage = ChatStage;

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ChatApiService);
  private readonly socket = inject(ChatSocketService);

  readonly isWidgetOpen = signal(false);
  readonly stage = signal(ChatStage.Idle);
  readonly statusText = signal('Need a hand? Start a chat with us.');
  readonly errorText = signal<string | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly queueStatus = signal<QueueStatusResponse | null>(null);
  readonly conversation = signal<ConversationMetadata | null>(null);
  readonly participantId = signal<string | null>(null);
  readonly isSending = signal(false);

  readonly canSendMessages = computed(() => this.stage() === ChatStage.Active);

  readonly messageForm = this.fb.group({
    content: ['', [Validators.required, Validators.minLength(1)]]
  });

  private readonly socketSubscriptions: Subscription[] = [];
  private readonly activeSubscriptions: Subscription[] = [];
  private readonly fingerprint = this.ensureFingerprint();
  private session!: StoredCustomerSession;

  ngOnInit(): void {
    this.session = this.ensureSession();
    this.setComposerEnabled(false);
  }

  ngOnDestroy(): void {
    this.clearSocketSubscriptions();
    this.clearActiveSubscriptions();
    this.socket.disconnect();
  }

  openWidget(): void {
    if (!this.isWidgetOpen()) {
      this.isWidgetOpen.set(true);
    }
    if (this.stage() === ChatStage.Idle || this.stage() === ChatStage.Ended) {
      this.stage.set(ChatStage.Confirm);
    }
  }

  closeWidget(): void {
    this.isWidgetOpen.set(false);
  }

  startConversation(): void {
    if (this.stage() === ChatStage.Connecting) {
      return;
    }
    this.errorText.set(null);
    this.stage.set(ChatStage.Connecting);
    this.statusText.set('Starting a new conversation...');

    const payload: CreateConversationPayload = {
      channel: 'web',
      displayName: this.session.displayName
    };

    const start$ = this.api
      .createConversation(payload)
      .pipe(
        take(1),
        switchMap((conversation) => {
          const customerId = conversation.customer?.id ?? this.session.token;
          this.participantId.set(customerId);
          this.conversation.set(conversation);
          this.messages.set([
            this.createSystemMessage(
              conversation.id,
              'Please wait, our support agent will join you shortly.'
            )
          ]);
          this.statusText.set('We are finding the best available agent for you.');
          this.stage.set(ChatStage.Waiting);
          this.setComposerEnabled(false);
          this.connectSocket(conversation, customerId);
          return this.api
            .requestAgent(conversation.id, conversation.attributes?.channel as string | undefined ?? 'web')
            .pipe(
              catchError((error) => {
                console.error(error);
                this.errorText.set(this.resolveErrorMessage(error, 'Unable to place you in the queue.'));
                return of<QueueStatusResponse | null>(null);
              })
            );
        }),
        finalize(() => {
          if (this.stage() === ChatStage.Connecting) {
            this.stage.set(ChatStage.Confirm);
          }
        })
      );

    this.activeSubscriptions.push(
      start$.subscribe((queueStatus) => {
        if (queueStatus) {
          this.queueStatus.set(queueStatus);
        }
      })
    );
  }

  sendMessage(): void {
    if (!this.canSendMessages()) {
      return;
    }

    if (this.messageForm.invalid) {
      this.messageForm.markAllAsTouched();
      return;
    }

    const content = this.messageForm.value.content?.trim();
    const conversation = this.conversation();
    if (!content || !conversation) {
      return;
    }

    this.isSending.set(true);
    this.socket
      .sendMessage(conversation.id, content)
      .then(() => {
        this.messageForm.reset();
      })
      .catch((error) => {
        console.error(error);
        this.errorText.set(error.message ?? 'Failed to send your message.');
      })
      .finally(() => {
        this.isSending.set(false);
      });
  }

  restartChat(): void {
    this.socket.disconnect();
    this.clearSocketSubscriptions();
    this.clearActiveSubscriptions();
    this.messages.set([]);
    this.queueStatus.set(null);
    this.conversation.set(null);
    this.participantId.set(null);
    this.statusText.set('Need a hand? Start a chat with us.');
    this.errorText.set(null);
    this.stage.set(ChatStage.Confirm);
    this.setComposerEnabled(false);
  }

  trackByMessageId(_index: number, item: ChatMessage): string {
    return item.id;
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

  formatEstimate(duration?: string | null): string | null {
    if (!duration) {
      return null;
    }
    const match = duration.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/);
    if (!match) {
      return duration;
    }
    const [, hours, minutes, seconds] = match;
    const parts: string[] = [];
    if (hours) {
      parts.push(`${hours}h`);
    }
    if (minutes) {
      parts.push(`${minutes}m`);
    }
    if (seconds && !hours && !minutes) {
      parts.push(`${seconds}s`);
    }
    return parts.length ? parts.join(' ') : '<1m';
  }

  private connectSocket(conversation: ConversationMetadata, customerId: string): void {
    this.setComposerEnabled(false);
    this.socket.connectCustomer({
      conversationId: conversation.id,
      customerId,
      displayName: conversation.customer?.displayName ?? this.session.displayName,
      fingerprint: this.fingerprint
    });
    this.attachSocketListeners();
  }

  private attachSocketListeners(): void {
    this.clearSocketSubscriptions();

    this.socketSubscriptions.push(
      this.socket.onHandshake().subscribe((handshake: SocketHandshake) => {
        this.conversation.set(handshake.conversation);
        this.participantId.set(handshake.participant.id);
        this.loadHistory(handshake.conversation.id);
      })
    );

    this.socketSubscriptions.push(
      this.socket.onMessage().subscribe((message) => this.handleIncomingMessage(message))
    );

    this.socketSubscriptions.push(
      this.socket.onError().subscribe((error) => this.errorText.set(error))
    );

    this.socketSubscriptions.push(
      this.socket.onDisconnect().subscribe(() => {
        if (this.stage() !== ChatStage.Idle && this.stage() !== ChatStage.Ended) {
          this.stage.set(ChatStage.Ended);
          this.statusText.set('The chat was closed. We are always here if you need more help.');
          this.setComposerEnabled(false);
        }
      })
    );
  }

  private handleIncomingMessage(message: ChatMessage): void {
    const senderType = message.sender?.type?.toUpperCase();
    let normalized: ChatMessage = message;

    if (senderType === 'AGENT') {
      this.stage.set(ChatStage.Active);
      this.queueStatus.set(null);
      const agentName = message.sender?.displayName || 'our agent';
      this.statusText.set(`You're now chatting with ${agentName}.`);
      this.setComposerEnabled(true);
    }

    if (message.type?.toUpperCase() === 'SYSTEM') {
      const closing = message.content?.trim()
        ? message.content
        : 'Thanks for chatting with us! Feel free to start a new conversation whenever you need help.';
      normalized = { ...message, content: closing };
      this.stage.set(ChatStage.Ended);
      this.statusText.set(closing);
      this.setComposerEnabled(false);
      this.socket.disconnect();
      this.clearSocketSubscriptions();
      this.clearActiveSubscriptions();
    }

    this.messages.update((current) => this.mergeMessages(current, [normalized]));
  }

  private loadHistory(conversationId: string): void {
    this.activeSubscriptions.push(
      this.api
        .listMessages(conversationId)
        .pipe(take(1))
        .subscribe((history) => {
          this.messages.update((current) => this.mergeMessages(current, history));
          if (history.some((msg) => msg.sender?.type?.toUpperCase() === 'AGENT')) {
            this.stage.set(ChatStage.Active);
            this.setComposerEnabled(true);
          }
        })
    );
  }

  private mergeMessages(existing: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
    const byId = new Map<string, ChatMessage>();
    [...existing, ...incoming].forEach((msg) => {
      byId.set(msg.id, msg);
    });
    return Array.from(byId.values()).sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
  }

  private clearSocketSubscriptions(): void {
    while (this.socketSubscriptions.length) {
      this.socketSubscriptions.pop()?.unsubscribe();
    }
  }

  private clearActiveSubscriptions(): void {
    while (this.activeSubscriptions.length) {
      this.activeSubscriptions.pop()?.unsubscribe();
    }
  }

  private createSystemMessage(conversationId: string, content: string): ChatMessage {
    return {
      id: `local-${Date.now()}`,
      conversationId,
      type: 'SYSTEM',
      content,
      timestamp: new Date().toISOString(),
      sender: {
        id: 'system',
        type: 'SYSTEM',
        displayName: 'System'
      }
    };
  }

  private ensureFingerprint(): string {
    const storageKey = 'customer-chat-fingerprint';
    try {
      const existing = localStorage.getItem(storageKey);
      if (existing) {
        return existing;
      }
    } catch (error) {
      console.warn('Unable to read fingerprint from storage', error);
    }
    const generated = crypto.randomUUID();
    try {
      localStorage.setItem(storageKey, generated);
    } catch (error) {
      console.warn('Unable to store fingerprint', error);
    }
    return generated;
  }

  private ensureSession(): StoredCustomerSession {
    const storageKey = 'customer-chat-session';
    try {
      const raw = localStorage.getItem(storageKey);
      if (raw) {
        const parsed = JSON.parse(raw) as StoredCustomerSession;
        if (parsed.token) {
          return parsed;
        }
      }
    } catch (error) {
      console.warn('Failed to restore stored session', error);
    }
    const session: StoredCustomerSession = {
      token: crypto.randomUUID(),
      displayName: 'Visitor'
    };
    try {
      localStorage.setItem(storageKey, JSON.stringify(session));
    } catch (error) {
      console.warn('Unable to persist session details', error);
    }
    return session;
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

  private setComposerEnabled(enabled: boolean): void {
    if (enabled) {
      if (this.messageForm.disabled) {
        this.messageForm.enable({ emitEvent: false });
      }
    } else if (this.messageForm.enabled) {
      this.messageForm.disable({ emitEvent: false });
    }
  }
}
