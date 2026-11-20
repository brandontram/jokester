# Jokester

A web page with one button: click **Tell me a joke** and a fresh joke is written for you by Claude.

- `backend/`: Spring Boot 4.2 + Spring AI 2.1. Exposes `GET /api/joke`.
- `frontend/`: React + TypeScript (Vite). Proxies `/api` to the backend in development.

## Prerequisites

- JDK 21 or newer
- Node.js 22 or newer
- An Anthropic API key

## Run it

In one terminal, start the backend on port 8080:

```bash
export ANTHROPIC_API_KEY=your-key-here
cd backend
./mvnw spring-boot:run
```

In a second terminal, start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Then open http://localhost:5173.

If `ANTHROPIC_API_KEY` is missing or wrong, the backend still starts, but every click shows the friendly error message. The backend log shows the real cause (a `JokeGenerationException` with the Anthropic auth error underneath).

## API

| Request | Response |
|---|---|
| `GET /api/joke` | `200 {"joke": "..."}` |
| model failure or empty reply | `502 {"error": "Couldn't come up with a joke right now. Please try again."}` |

Model settings (model, max tokens) live in `backend/src/main/resources/application.properties`.

## Tests

None of the tests call the real Anthropic API.

```bash
cd backend && ./mvnw test     # service + controller tests, model mocked
cd frontend && npm test       # UI tests, fetch mocked
```
