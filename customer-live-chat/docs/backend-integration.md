# Live Chat Backend Integration Guide

This document explains how any frontend (web, mobile, desktop) can integrate with the `customer-live-chat` backend. The examples use HTTP(S) and WebSocket(S) requests; adapt them to your stack or networking library.

## Environments & Base URLs
- REST API base: `https://<backend-host>:8383`
- Socket.IO gateway: `wss://<backend-host>:9094`

Both services require the same authentication token/headers your deployment enforces (if any).

## Authentication
The backend can be secured behind an auth proxy or use API keys/JWT. Every call must include whatever headers are configured (e.g., `Authorization: Bearer <token>`). There is no built-in user management in the backend.

## Core Concepts
- **Conversation** – chat session between a customer and an agent.
- **Queue entry** – pending customer waiting for an agent.
- **Participants** – `CUSTOMER` or `AGENT`; each has metadata (name, phone, etc.).
- **Messages** – chat history items; created via REST and streamed via Socket.IO.

## REST Endpoints
All endpoints live under `/api`.

### Customer Flow
1. **Start/ensure session**
   ```http
   POST /api/customer/sessions
   Content-Type: application/json
   {
     "fingerprint": "client-generated-id",
     "displayName": "Jane Doe",
     "contact": "0123456789"
   }
   → 200 OK { "conversationId": "uuid", "status": "QUEUED" | "ASSIGNED" }
   ```

2. **Send message**
   ```http
   POST /api/customer/conversations/{conversationId}/messages
   {
     "content": "Hello!",
     "metadata": {"source": "web"}
   }
   ```

3. **Close conversation**
   ```http
   POST /api/customer/conversations/{conversationId}/close
   ```

4. **Fetch history (optional)**
   ```http
   GET /api/customer/conversations/{conversationId}/messages?limit=50
   ```

### Agent Flow
1. **Agent login/boot** (fetch active conversations & queue snapshot if needed)
   ```http
   POST /api/agent/sessions
   {
     "agentId": "agent-123",
     "displayName": "Ashik",
     "channel": "web"
   }
   → session/assignment info
   ```

2. **List queue (fallback/pagination)**
   ```http
   GET /api/agent/queue?page=0&size=20
   ```

3. **Accept conversation**
   ```http
   POST /api/agent/conversations/{conversationId}/accept
   {
     "agentId": "agent-123",
     "displayName": "Ashik"
   }
   ```
   - Returns 200 with conversation details
   - 409/GONE if another agent already took it

4. **Send message**
   ```http
   POST /api/agent/conversations/{conversationId}/messages
   {
     "content": "How can I help?",
     "metadata": {"channel": "support-panel"}
   }
   ```

5. **Close conversation**
   ```http
   POST /api/agent/conversations/{conversationId}/close
   {
     "reason": "resolved"
   }
   ```

6. **List assigned conversations**
   ```http
   GET /api/agent/conversations?status=ASSIGNED,CONNECTED
   ```

### Common Response Fields
```json
{
  "id": "uuid",
  "status": "QUEUED | ASSIGNED | CONNECTED | CLOSED",
  "customer": {
    "id": "visitor-id",
    "displayName": "customer name",
    "phone": "0123456789"
  },
  "agent": {
    "id": "agent-id",
    "displayName": "Agent Name"
  },
  "createdAt": "2025-11-13T11:36:03.351Z",
  "updatedAt": "2025-11-13T11:40:00.000Z"
}
```

## Socket.IO Channels
Connect to the Socket.IO gateway with query parameters:
```
wss://<host>:9094/socket.io/?role=<agent|customer>&fingerprint=<id>&conversationId=<optional>&scope=<queue|conversation>
```

### Customer socket
- **Handshake:** `role=customer`, include `conversationId` when reconnecting.
- **Events received:**
  - `chat:message` – new message payload `{ conversationId, message }`
  - `system:event` – queue updates, assignment status, closure notices
  - `system:error` – transient errors (retry/backoff)

### Agent socket
Agents typically open two connections:
1. **Queue stream** – `role=agent&scope=queue`
   - Receives `queue:snapshot` events with an array of queue entries sorted by wait time.
2. **Conversation stream** – `role=agent&conversationId=<id>` per active chat
   - Receives `chat:message`, `system:event`, `system:error`

### Sending via Socket.IO
All messages are sent via REST today; Socket.IO is used for realtime delivery only. If you want two-way websockets, extend the gateway (reserve channel names: `chat:send`, `conversation:close`).

## Error Handling
- HTTP 400 – validation (missing id, bad payload)
- HTTP 401/403 – auth failures (if enabled)
- HTTP 409 – optimistic conflicts (another agent accepted first)
- HTTP 410 – stale queue entry / conversation gone
- HTTP 422 – semantic errors
- HTTP 500 – unexpected server error

Socket error payload structure:
```json
{
  "code": "conflict",
  "message": "Conversation is no longer available to accept."
}
```

## Frontend Checklist
- Store customer fingerprint locally (cookie/localStorage) to resume sessions.
- Retry REST calls with exponential backoff for transient failures.
- On reconnect, rejoin Socket.IO rooms and re-fetch last `limit` messages.
- Watch for `system:event` with statuses `CLOSED`/`DISCONNECTED` to disable inputs.
- When accepting chats, limit concurrent widgets to `chat.queue.maxConcurrentByAgent` (default 3).

## Testing Utilities
- `POST /api/admin/seed` (if enabled) to populate fake queue entries
- `GET /actuator/health` – service liveness
- Logs stream queue snapshots and chat lifecycle events (configure log level `DEBUG` for `com.example.chat`)

## Support
For troubleshooting provide:
- Conversation ID(s)
- Timestamps (UTC)
- Relevant log excerpts and Redis DB size
- Socket.IO client logs (connect/disconnect events)
