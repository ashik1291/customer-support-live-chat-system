# Customer Support Live Chat System 💬

> A production-ready real-time live chat platform connecting customers and support agents — 
> built with event-driven architecture, distributed session management, and WebSocket-based 
> real-time communication.

---

## What it does

Most customer support chat tools are third-party SaaS. This is a fully custom-built platform 
that gives complete control over the chat lifecycle — from the moment a customer opens the 
widget to the agent closing the conversation. Built to handle concurrent sessions reliably 
without race conditions or lost messages.

---

## System Architecture

```
                    Customer Angular Widget          Agent Angular Dashboard
                            │                                │
                            └──────────┬──────────────────────┘
                                       │ Socket.IO (WebSocket)
                                       ▼
                            Spring Boot Backend (Java 21)
                            ├── REST API          → :8383
                            ├── Socket.IO Server  → :9094
                            ├── PostgreSQL        → conversation metadata + optimistic locking
                            ├── Redis / Redisson  → queue state, session bindings, distributed locks
                            │        └── Pub/Sub  → queue snapshots broadcast to Socket.IO
                            └── Kafka             → chat lifecycle & message event streaming
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Gradle |
| Real-time | Socket.IO, Redis Pub/Sub |
| Messaging | Apache Kafka 3.x |
| Database | PostgreSQL 15+ |
| Caching & Locks | Redis 7+, Redisson |
| Agent Frontend | Angular (port 4201) |
| Customer Widget | Angular embeddable (port 4200) |

---

## Key Design Decisions

- **Socket.IO over raw WebSocket** — handles reconnection, fallback transports, and room-based broadcasting out of the box, which matters for an embeddable customer widget
- **Redisson distributed locks** — prevents race conditions when multiple agents attempt to claim the same conversation simultaneously
- **Redis Pub/Sub for queue snapshots** — agents see real-time queue updates without polling; snapshots are broadcast to all connected agent dashboards instantly
- **Kafka for lifecycle events** — decouples chat event processing from the request cycle; message and conversation lifecycle events are streamed independently for reliability and future extensibility
- **Optimistic locking in PostgreSQL** — conversation metadata updates are conflict-safe without heavy pessimistic locking overhead

---

## Project Structure
customer-support-live-chat-system/
├── customer-live-chat/                      # Spring Boot backend
├── customer-live-chat-agent-frontend/       # Angular agent dashboard
└── customer-live-chat-customer-frontend/    # Angular customer widget

---

## Getting Started

### Prerequisites
- Java 21+
- Node.js 18+, npm 9+
- PostgreSQL 15+
- Redis 7+
- Kafka 3.x cluster

### Backend

```bash
cd customer-live-chat
cp src/main/resources/application.yml.example src/main/resources/application.yml
# Configure DB, Redis, Kafka, and Socket.IO settings
./gradlew bootRun
```

| Service | URL |
|---|---|
| REST API | http://localhost:8383 |
| Socket.IO | ws://localhost:9094 |

### Agent Frontend

```bash
cd customer-live-chat-agent-frontend
npm install && npm run start
# http://localhost:4201
```
Supports up to 3 concurrent chat widgets per agent session with real-time queue updates and conflict handling.

### Customer Widget

```bash
cd customer-live-chat-customer-frontend
npm install && npm run start
# http://localhost:4200
```
Embeddable in any customer-facing site — includes pre-chat form, auto-scroll, send-on-enter, manual close, and auto-reconnect.

---

## Production Checklist

- [ ] Externalize config via environment variables or Spring Config Server
- [ ] Configure CORS and rate limiting via `ChatSecurityProperties`
- [ ] Run PostgreSQL, Redis, Kafka in HA mode
- [ ] Enable Redis persistence
- [ ] Run `./gradlew check` and `npm run build` before deploying
- [ ] Monitor queue size, WebSocket connections, and Kafka publish results via APM

---

## License

Proprietary — contact the owner for usage permissions.
