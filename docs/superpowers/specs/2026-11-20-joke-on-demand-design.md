# Jokester: Joke on Demand (Design)

## Intent
A web page with one button. Each click asks the backend for a joke; the backend has an LLM write a fresh one via Spring AI and returns it; the page shows it.

## Goals / non-goals
- Goal: a new joke on every click, shown in the browser.
- Non-goals: a stored "joke of the day", accounts, joke history, saving or sharing jokes, streaming.

## Approaches considered
1. **Two separate apps (chosen).** `frontend/` (React + Vite) and `backend/` (Spring Boot), run as two processes; Vite dev server proxies `/api` to Spring. Simplest, standard, no CORS config.
2. Same as 1, but Maven bundles the React build into the Spring jar for a single deployable. Rejected for now: extra build plumbing; easy to add later.
3. Stream the joke token-by-token. Rejected: a joke is short and arrives in ~1s; adds complexity for no visible gain.

## Decided
- React frontend; Spring backend using Spring AI. Joke on demand, not one per day.
- Layout: approach 1.

## My calls (correct any of these)
- Java 21, Spring Boot 4.2.0, Maven; Spring AI 2.1.0 with the Anthropic starter. (Was "Boot 3.x"; corrected at planning time: Spring AI 2.1.0, the current release, is built on Boot 4.2.0.)
- Model: Claude Haiku 5.5 (`claude-haiku-5-5`): fast and cheap, fine for jokes.
- API key from the `ANTHROPIC_API_KEY` env var; never committed. Missing or invalid key → the app still starts; each request returns the 502 error and logs the cause.
- Frontend: React + TypeScript, Vite.
- API: `GET /api/joke` → `200 {"joke": "..."}`. On model failure → `502 {"error": "..."}`.
- Prompt: a short, clean, family-friendly joke, returned as plain text only. Temperature raised (~1.0) so repeated clicks vary. (Dropped at build time: the current Anthropic API ignores temperature, so variety relies on the model's default sampling.)
- UI: button "Tell me a joke"; disabled with a "Thinking…" label while loading; joke shown below; friendly error message if the call fails.
- Tests: a backend controller test with the AI client mocked; a frontend component test for loading/success/error states.

## Left to the builder
Styling details, package names, exact prompt wording.
