# Customer Live Chat Frontend Setup

Two standalone Angular applications live alongside the backend to deliver the agent console and the customer chat widget. Both apps target Angular 17 and can be developed independently.

## Prerequisites

- Node.js 18 LTS or newer (ships with `npm` 9+).
- Google Chrome (for the default Karma runner).
- Java backend running locally on `http://localhost:8383` (default from `application.yml`).

> **Tip:** If you work behind a corporate proxy, configure Node.js/npm to route traffic accordingly before installing dependencies.

## Install Dependencies

From the repository root:

```powershell
cd customer-live-chat-customer-frontend
npm install

cd ..\customer-live-chat-agent-frontend
npm install
```

The `package.json` in each project includes the Angular CLI so no global install is required.

## Local Development

Each project ships with a `proxy.conf.json` that forwards `/api` calls to the Spring Boot backend. Run both servers in parallel from separate shells:

```powershell
cd customer-live-chat-customer-frontend
npm start

# in another terminal
cd customer-live-chat-agent-frontend
npm start
```

- Customer widget: `http://localhost:4200`
- Agent console: `http://localhost:4201` (Angular CLI assigns the next free port automatically; check the terminal output)
- Socket.io gateway: `http://localhost:9094` (configured in `environment*.ts`)

When the backend runs on a different host/port update `src/environments/environment.ts` in each project or pass `--proxy-config` with a customised proxy file.

## Production Build

```powershell
npm run build
```

The compiled assets land in `dist/**` and can be hosted on any static web server behind the same origin as the backend or with a reverse proxy that forwards `/api`.

## Testing

Both projects include starter Karma/Jasmine configuration:

```powershell
npm test
```

Add targeted component/service specs as the UI grows. For end-to-end testing consider Playwright or Cypress once real-time flows are integrated.

## Project Highlights

### Customer App (`customer-live-chat-customer-frontend`)

- Pre-chat intake form captures name, email, subject, and an initial question.
- Session persistence in `localStorage` so customers can refresh and keep the conversation.
- Queue request with live position + estimated wait parsing.
- Polling for recent messages with automatic status updates when an agent replies.
- Responsive layout optimised for embedding in support portals.

### Agent App (`customer-live-chat-agent-frontend`)

- Agent sign-in stores credentials locally for quick reloads.
- Live queue monitoring with polling every 5 seconds and accept actions.
- Assigned conversation list with status badges and quick refresh.
- Message composer, close conversation workflow, and live message stream.
- Dev proxy + environment config to stay in sync with the backend API.

Both applications share similar TypeScript models so backend DTO updates are easy to propagate.

