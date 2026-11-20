# Jokester Joke-on-Demand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A React page with a "Tell me a joke" button that fetches a freshly generated joke from a Spring Boot + Spring AI backend.

**Architecture:** Two apps in one repo. `backend/` is Spring Boot exposing `GET /api/joke`; a `JokeService` calls Claude through Spring AI's `ChatClient`. `frontend/` is a Vite React app; in dev, Vite proxies `/api` to `http://localhost:8080`.

**Tech Stack:** Java 21, Maven, Spring Boot 4.2.0, Spring AI 2.1.0 (`spring-ai-starter-model-anthropic`), JUnit 5 + Mockito; React 19 + TypeScript, Vite 8, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-11-20-joke-on-demand-design.md`

## Global Constraints

- Java package: `com.sidebsoftware.jokester`. Compile with `<java.version>21</java.version>` (JDK 25 is installed; that's fine).
- Model `claude-haiku-5-5`, temperature `1.0`, max tokens `200`, set in `application.properties` via `spring.ai.anthropic.chat.model`, `spring.ai.anthropic.chat.temperature`, `spring.ai.anthropic.chat.max-tokens`.
- API key: `spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}`. Never commit a key. A missing env var should fail at startup with Spring's unresolved-placeholder error.
- API contract: `GET /api/joke` → `200 {"joke":"<text>"}`; any generation failure → `502 {"error":"Couldn't come up with a joke right now. Please try again."}`.
- System prompt (exact): `You are a comedian. Reply with exactly one short, clean, family-friendly joke and nothing else: no preamble, no explanation, no surrounding quotation marks.` User message (exact): `Tell me a joke.`
- UI copy: button `Tell me a joke`; while loading `Thinking…` (button disabled); error text `Couldn't come up with a joke right now. Please try again.`
- No tests may call the real Anthropic API.

## Review Focus

1. Model returns empty/whitespace-only text → must be a 502 error, never an empty joke on screen. (Task 1 test.)
2. Model wraps the joke in whitespace or straight/curly quotes despite the prompt → trimmed and unquoted before returning. (Task 1 test.)
3. Rapid double-click → only one request in flight; button disabled until it settles. (Task 3 test.)
4. Backend down, so the Vite proxy returns a 500/502 with an HTML or empty body → page shows the friendly error and doesn't crash on JSON parse. (Task 3 test.)
5. Error, then a successful retry → the old error is cleared and the new joke shows; a joke followed by a failure → the stale joke is replaced by the error. (Task 3 test.)

---

### Task 1: Backend scaffold + JokeService

**Files:**
- Create: `backend/` (generated project: `pom.xml`, `mvnw`, `.mvn/`, `src/main/java/com/sidebsoftware/jokester/JokesterApplication.java`)
- Create: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/sidebsoftware/jokester/JokeService.java`
- Create: `backend/src/main/java/com/sidebsoftware/jokester/JokeGenerationException.java`
- Test: `backend/src/test/java/com/sidebsoftware/jokester/JokeServiceTest.java`

**Interfaces:**
- Produces: `@Service public class JokeService { public JokeService(ChatClient.Builder builder); public String tellJoke(); }`. It returns the cleaned joke text, or throws `JokeGenerationException`.
- Produces: `public class JokeGenerationException extends RuntimeException { JokeGenerationException(String message); JokeGenerationException(String message, Throwable cause); }`

- [ ] **Step 1: Generate the project**

```bash
curl -s https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d bootVersion=4.2.0 -d javaVersion=21 \
  -d groupId=com.sidebsoftware -d artifactId=jokester -d name=jokester \
  -d packageName=com.sidebsoftware.jokester \
  -d dependencies=web,spring-ai-anthropic -o /tmp/jokester-backend.zip \
  && mkdir backend && unzip -q /tmp/jokester-backend.zip -d backend
```

Check that `pom.xml` imports `spring-ai-bom` 2.1.0 and depends on `spring-ai-starter-model-anthropic`. If Initializr has a different Spring AI version, set `<spring-ai.version>2.1.0</spring-ai.version>`. If Initializr rejects the request, write the POM by hand: Boot parent 4.2.0, `spring-boot-starter-webmvc`, `spring-ai-starter-model-anthropic`, `spring-boot-starter-webmvc-test` (test scope), and the `spring-ai-bom` 2.1.0 import. Delete the generated `JokesterApplicationTests` context test, because it would need a real key.

- [ ] **Step 2: Write `application.properties`** using the values from Global Constraints, plus `spring.application.name=jokester`.

- [ ] **Step 3: Write the failing tests** in `JokeServiceTest`. Use `ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS)` and a mocked `ChatClient.Builder` whose `defaultSystem(anyString())` returns itself and whose `build()` returns `chatClient`. Stub `chatClient.prompt().user("Tell me a joke.").call().content()`.

```java
@Test void returnsJokeFromModel()            // content "Why did the chicken…" → same string
@Test void trimsWhitespace()                 // "  A joke.\n" → "A joke."
@Test void stripsSurroundingQuotes()         // "\"A joke.\"" → "A joke."; "“A joke.”" → "A joke."
@Test void blankResponseThrows()             // "   " → assertThrows(JokeGenerationException.class, service::tellJoke)
@Test void nullResponseThrows()              // null → JokeGenerationException
@Test void modelFailureIsWrapped()           // content() throws RuntimeException → JokeGenerationException with that cause
@Test void usesExactSystemPrompt()           // verify(builder).defaultSystem("<exact system prompt>")
```

- [ ] **Step 4: Run them and confirm they fail**: `cd backend && ./mvnw -q test`. Expected: compilation failure, because `JokeService` doesn't exist yet.

- [ ] **Step 5: Implement `JokeService` and `JokeGenerationException`.** The constructor calls `builder.defaultSystem(SYSTEM_PROMPT).build()`. `tellJoke()` calls `prompt().user("Tell me a joke.").call().content()`, wraps any exception, trims the result, strips one matching pair of surrounding `"…"` or `“…”`, trims again, and throws if blank.

- [ ] **Step 6: Run the tests and confirm they pass**: `./mvnw -q test`. Expected: `BUILD SUCCESS`, 7 tests.

- [ ] **Step 7: Commit**: `git add backend && git commit -m "feat(backend): scaffold Spring AI app and JokeService"`

### Task 2: Joke endpoint

**Files:**
- Create: `backend/src/main/java/com/sidebsoftware/jokester/JokeController.java`
- Test: `backend/src/test/java/com/sidebsoftware/jokester/JokeControllerTest.java`

**Interfaces:**
- Consumes: `JokeService.tellJoke()`, `JokeGenerationException` (Task 1).
- Produces: `GET /api/joke` per the Global Constraints contract. Nested records `JokeResponse(String joke)` and `ErrorResponse(String error)`.

- [ ] **Step 1: Write the failing tests.** Use a `@WebMvcTest(JokeController.class)` slice (Boot 4 package: `org.springframework.boot.webmvc.test.autoconfigure`), with `@MockitoBean JokeService` and `MockMvc`.

```java
@Test void returnsJoke()         // tellJoke() → "A joke." ; GET /api/joke → 200, jsonPath("$.joke").value("A joke.")
@Test void generationFailureIs502() // tellJoke() throws JokeGenerationException → 502, jsonPath("$.error").value("Couldn't come up with a joke right now. Please try again.")
```

- [ ] **Step 2: Run them and confirm they fail**: `./mvnw -q test -Dtest=JokeControllerTest`. Expected: compilation failure.

- [ ] **Step 3: Implement `JokeController`** as a `@RestController` with `@GetMapping("/api/joke")`, and an `@ExceptionHandler(JokeGenerationException.class)` returning 502 with an `ErrorResponse`. Log the exception at WARN so the cause stays visible server-side.

- [ ] **Step 4: Run all backend tests**: `./mvnw -q test`. Expected: `BUILD SUCCESS`, 9 tests.

- [ ] **Step 5: Commit**: `git add backend && git commit -m "feat(backend): add GET /api/joke endpoint"`

### Task 3: React frontend

**Files:**
- Create: `frontend/` (Vite `react-ts` template)
- Modify: `frontend/vite.config.ts` (proxy and Vitest config)
- Create: `frontend/src/api.ts`
- Modify: `frontend/src/App.tsx`, `frontend/src/App.css`; strip template boilerplate from `index.css`
- Create: `frontend/src/test-setup.ts`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `GET /api/joke` (Task 2).
- Produces: `export async function fetchJoke(): Promise<string>`, which resolves to the joke and throws `Error` on a non-2xx status, an unparseable body, or a missing or empty `joke` field.

- [ ] **Step 1: Scaffold and install**

```bash
npm create vite@latest frontend -- --template react-ts
cd frontend && npm install && npm install -D vitest @testing-library/react @testing-library/user-event @testing-library/jest-dom jsdom
```

Add `"test": "vitest run"` to `package.json`. In `vite.config.ts`, add `server.proxy: { '/api': 'http://localhost:8080' }` and `test: { environment: 'jsdom', setupFiles: './src/test-setup.ts' }`. The setup file imports `@testing-library/jest-dom/vitest`.

- [ ] **Step 2: Write the failing tests** in `App.test.tsx`, stubbing `globalThis.fetch` with `vi.fn()`:

```tsx
test('shows a joke after clicking')          // fetch → 200 {"joke":"A joke."}; click "Tell me a joke"; findByText("A joke.")
test('disables button while loading')        // fetch → pending promise; click; button has text "Thinking…" and is disabled; second click → fetch called once
test('shows error on 502 JSON')              // 502 {"error":"…"} → findByText("Couldn't come up with a joke right now. Please try again.")
test('shows error on non-JSON 500')          // 500 with body "<html>Proxy error</html>" → same friendly error, no throw
test('shows error when fetch rejects')       // fetch rejects TypeError → same friendly error
test('retry after error clears the error')   // 1st 502, 2nd 200 "A joke." → error gone, joke shown
test('failure after a joke replaces it')     // 1st 200 "A joke.", 2nd 502 → "A joke." gone, error shown
```

- [ ] **Step 3: Run them and confirm they fail**: `npm test`. Expected: failures (the template App has no such button).

- [ ] **Step 4: Implement `fetchJoke` in `api.ts`, and rewrite `App.tsx`.** State is `joke: string | null`, `error: boolean`, `loading: boolean`. On click: set loading, clear the joke and the error, `await fetchJoke()`, set exactly one of joke or error, and clear loading. Render the joke in a `<p>` (or `<blockquote>`) that wraps long text (`overflow-wrap: anywhere`), and the error with `role="alert"`. Styling: a simple centered card. The heading is "Jokester".

- [ ] **Step 5: Run the tests and confirm they pass**: `npm test` (7 passed). `npm run build` should also succeed with no type errors.

- [ ] **Step 6: Commit**: `git add frontend && git commit -m "feat(frontend): joke button page"`

### Task 4: README and end-to-end check

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write the README.** Cover the prerequisites (JDK 21+, Node 22+, an Anthropic API key), then how to run: `export ANTHROPIC_API_KEY=...`, `cd backend && ./mvnw spring-boot:run`, and in a second terminal `cd frontend && npm run dev`, then open http://localhost:5173. Also cover how to run the tests in each app.

- [ ] **Step 2: Manual end-to-end check (needs the user's key).** Start both apps, click the button 3 times, and confirm 3 different jokes appear. Then stop the backend, click, and confirm the friendly error appears. If no key is available in the session, hand this step to the user, and say so.

- [ ] **Step 3: Commit**: `git add README.md docs && git commit -m "docs: README with run instructions"`
