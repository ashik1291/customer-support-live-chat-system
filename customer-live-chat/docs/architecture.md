# Customer Live Chat Backend Architecture

## High-Level Overview

- **API Gateway (REST + WebSocket/Socket.IO)** – Entry point for web and mobile clients. The Socket.IO server runs alongside the Spring Boot app to deliver bi-directional events with fallback transports.
- **Conversation Service** – Pure domain service responsible for starting conversations, queueing for agents, message persistence (in Redis), lifecycle transitions, and closing conversations.
- **Redis** – Primary real-time data store.
  - `lc:conversation:{conversationId}` → hash containing `ConversationMetadata`.
  - `lc:conversation:{conversationId}:messages` → list of `ChatMessage` entries (JSON-serialized).
  - `lc:queue:pending` → sorted set storing `QueueEntry` records by enqueue timestamp.
  - `lc:agent:{agentId}:conversations` → set of conversations currently assigned to the agent.
  - `lc:presence:{participantId}` → string of last seen timestamp (ISO-8601), expiring after `presenceTtl`.
- **Kafka** – Event streaming backbone.
  - `chat.lifecycle` broadcasts lifecycle events (`CONVERSATION_STARTED`, `CONVERSATION_ACCEPTED`, etc.) for downstream analytics, workflow triggers, or archival workers.
  - `chat.messages` carries `ChatMessageEvent` for async NLP, sentiment analysis, or auditing.
- **Pluggable Interfaces** – The module exposes `ChatAuthenticationProvider` and `ChatEventListener` extension points so host applications can hook their own authentication or event-handling logic without forking the module.
- **Future PostgreSQL Persistence** – Introduce background workers consuming Kafka topics to persist finalized conversations/messages to Postgres. The current Redis models already serialize clean domain objects ready for storage.

## Message Flow

1. **Client Connects**
   - Socket.IO handshake conveys `role`, `token`, optional `conversationId`.
   - `ChatAuthenticationProvider` resolves the actor (anonymous or authenticated).
   - If no `conversationId`, a new conversation is created and the client joins its room.

2. **FAQ / Bot Interaction (Optional)**
   - Handled on the client or via additional services listening on Kafka. Messages sent still pass through `ConversationService.sendMessage`.

3. **Agent Request**
   - Client calls `POST /api/conversations/{id}/queue`. The conversation transitions to `QUEUED`, a `QueueEntry` is pushed into `lc:queue:pending`, and a lifecycle event is published.

4. **Agent Accepts**
   - Admin UI polls `GET /api/agent/queue`.
   - Agent posts to `/api/agent/conversations/{id}/accept` (or uses Socket event) which removes the queue entry, assigns the agent, and emits a `CONVERSATION_ACCEPTED` event.

5. **Messaging**
   - Socket.IO `chat:message` events invoke `ConversationService.sendMessage`, which writes to Redis, updates presence, and publishes Kafka events. The message is simultaneously broadcast to the Socket.IO room.

6. **Closure**
   - Agent triggers `/api/agent/conversations/{id}/close`. The conversation is marked `CLOSED`, queue entries are removed, and a lifecycle event is emitted.

## Resilience & Scaling

- **Redis TTLs** ensure temporary conversations are not leaked. Stale presence is auto-removed.
- **Idempotent Services** – Conversation updates always re-persist the full `ConversationMetadata`, making replays safe.
- **Horizontal Scalability** – Multiple Spring Boot instances can run simultaneously because they all hit shared Redis and Kafka. Socket.IO rooms are isolated per node but cross-node fan-out can be added with Netty-SocketIO's pub/sub support.
- **Backpressure** – Queue positions are tracked in Redis, allowing host apps to implement wait time estimates or deflection logic.
- **Concurrency** – Redis atomic operations guarantee queue ordering. Kafka ensures at-least-once delivery of events for downstream consumers.

## Extension Points

- **Authentication** – Replace `DefaultChatAuthenticationProvider` with a bean that wires into SSO, OAuth, or session stores.
- **Event Handling** – Implement `ChatEventListener` to push notifications, trigger CRM updates, or feed analytics pipelines.
- **Storage** – Swap `ConversationRepository` with a custom implementation (e.g., composite Redis/Postgres) without touching higher-level services.
- **Configuration** – Override `chat.*` properties for namespace isolation, TTLs, queue thresholds, Socket.IO host/port, etc.

## Security Considerations

- Enforce TLS termination and set Socket.IO to accept only secure transports in production.
- Integrate with Spring Security to protect REST endpoints and validate agent/customer tokens.
- Rate-limit message events per connection to defend against abuse.
- Validate/escape message content before rendering in agent UI.

## Operational Best Practices

- Enable Redis keyspace notifications for proactive cleanup or metrics.
- Monitor Kafka lag for downstream persistence jobs.
- Instrument Socket.IO connection counts and message throughput.
- Build synthetic transactions to confirm full chat lifecycle in staging environments.

