# VTChat — Phases 1–12 (Auth, Profile & Settings, Search + Private Chat, Real-Time Messaging, Advanced Message Features, Media + Voice, Translation + Thanglish, Voice + Video Calls + Call History, Presence + Typing, PDF Download State, Delivery + Read Receipts, Notifications + Chat Management, Redis + Rate Limiting + Scalability, Python AI + LLM Assistant, Embeddings + Vector DB + RAG)

Private one-to-one chat app. **This delivery covers Phases 1 to 12**:
- Phase 12: "My Knowledge" (RAG) inside the AI page: upload PDF/TXT/MD/DOC/DOCX, they are chunked and embedded in the Python AI service and stored in Qdrant; a Normal AI / My Knowledge switch answers from your own documents with sources (see the last section, "Phase 12").
- Phase 11: an AI assistant (`/ai`): React -> Spring Boot -> a separate Python FastAPI AI service -> a configurable LLM, with its own persisted conversations, streaming answers, Markdown + code copy, and Phase 10 rate limiting (see the last section, "Phase 11").
- Phase 1: project setup and complete authentication (register, login, forgot password, 4-digit email OTP via Resend, JWT access + refresh tokens).
- Phase 2: profile (picture + username), settings (light/dark/system theme, account, logout, change password) and navigation.
  **Phase 2 is documented in the "Phase 2 — Profile + Settings" section; Phase 3 in the last section.**
- Phase 3: user search, profile preview, and private one-to-one conversations.
- Phase 4: real-time messaging over STOMP/SockJS with message history in MySQL (see "Phase 4").
- Phase 8.5: online status, last seen and typing indicator, with privacy switches (see the last section, "Phase 8.5").
- Phase 8: one-to-one voice and video calls with WebRTC; the STOMP socket carries signaling only (see the last section, "Phase 8").
- Phase 7: translate text messages (en, ta, hi, ml, kn) through a Python IndicTrans2 microservice with automatic source detection, including Thanglish (see the last section, "Phase 7").
- Phase 6: images, videos, documents and voice messages (see the last section, "Phase 6").
- Phase 5: reactions, replies, edit, delete for me / for everyone, forward, copy, selection, message search, "Sent" status (documented in the last section).

Group calls, screen sharing, recording, push notifications, Redis and AI belong to later phases and are not included.

## 1. Project structure

```text
vtchat/
├── README.md
├── .gitignore
├── backend/                                  Spring Boot 3.3 · Java 17 · Maven · MySQL
│   ├── pom.xml
│   ├── .env.example
│   └── src/main/
│       ├── resources/application.yml
│       └── java/com/vtchat/
│           ├── VtChatApplication.java
│           ├── config/        AppProperties, SecurityConfig (CORS, filter chain, BCrypt), AsyncConfig
│           ├── controller/    AuthController                      (thin HTTP layer + refresh cookie)
│           ├── service/       AuthService, OtpService, TokenService, EmailService (Resend), CleanupScheduler
│           ├── repository/    UserRepository, OtpTokenRepository, RefreshTokenRepository
│           ├── entity/        User, OtpToken, OtpPurpose, RefreshToken
│           ├── dto/           AuthRequest, AuthResponses, ApiResponse, ErrorResponse
│           ├── security/      JwtService, JwtAuthenticationFilter, AuthenticatedUser, RestSecurityErrorHandler
│           ├── exception/     ApiException, GlobalExceptionHandler
│           └── util/          HashUtil, EmailUtil
└── frontend/                                 React 18 · Vite · Tailwind 4 · React Router · Axios · Framer Motion · SweetAlert2
    ├── package.json  vite.config.js  index.html  .env.example
    ├── public/favicon.svg
    └── src/
        ├── main.jsx  App.jsx  index.css
        ├── routes/      AppRoutes, ProtectedRoute, PublicRoute
        ├── context/     AuthContext
        ├── hooks/       useAuth, useCountdown
        ├── services/    api.js (axios + auto refresh), authService.js
        ├── layouts/     AuthLayout, AppLayout
        ├── components/  OtpInput, OtpForm, TextField, PasswordInput, PasswordRules, Button, ...
        ├── pages/       Register, VerifyOtp, Login, VerifyLoginOtp, ForgotPassword,
        │                VerifyResetOtp, ResetPassword, Home, NotFound
        └── utils/       validators, errors, alerts (SweetAlert2), tokenStore, pendingFlow
```

## 2. Prerequisites

Java 17+, Maven 3.9+, Node 18+ (20+ recommended), MySQL 8+, a free [Resend](https://resend.com) account.

## 3. Database setup

The app creates the `vtchat` database and all tables on first start (`createDatabaseIfNotExist=true`, Hibernate `ddl-auto: update`).
If you prefer to create it yourself:

```sql
CREATE DATABASE vtchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Tables created: `users`, `otp_tokens`, `refresh_tokens`.
(`ddl-auto: update` is for development; Phase 13 should switch to migrations.)

## 4. Resend setup

1. Sign up at resend.com → **API Keys** → create a key → put it in `RESEND_API_KEY`.
2. **Testing:** set `EMAIL_FROM=VTChat <onboarding@resend.dev>`. Resend only delivers from this sender to **the email address of your own Resend account**, so register/log in with that address while testing.
3. **Real use:** add and verify your domain in Resend → Domains, then use e.g. `EMAIL_FROM=VTChat <no-reply@yourdomain.com>`.

If sending fails the API returns `502 EMAIL_SEND_FAILED` (or `503 EMAIL_NOT_CONFIGURED`); the reason is in the backend log (OTP values are never logged).

## 5. Environment variables

**backend/.env** (copy from `.env.example`; Spring loads it automatically when you run from `backend/`)

| Variable | Purpose |
|---|---|
| `SERVER_PORT` | API port (8080) |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | MySQL connection |
| `CLIENT_URL` | Frontend origin allowed by CORS (`http://localhost:5173`) |
| `RESEND_API_KEY` `EMAIL_FROM` | Resend email sending |
| `JWT_SECRET` | Signs access tokens (≥32 chars) |
| `JWT_REFRESH_SECRET` | Signs refresh tokens (≥32 chars, **different** from `JWT_SECRET`) |
| `JWT_ACCESS_EXPIRATION` / `JWT_REFRESH_EXPIRATION` | Lifetimes in **milliseconds** (900000 = 15 min, 604800000 = 7 days) |
| `OTP_SECRET` | Key used to HMAC-hash OTPs before storing them (≥16 chars) |
| `COOKIE_SECURE` / `COOKIE_SAME_SITE` | Refresh-cookie flags. Production over HTTPS: `true`. Frontend and API on different sites: `SameSite=None` + `Secure=true` |

`OTP_SECRET`, `COOKIE_SECURE` and `COOKIE_SAME_SITE` are additions to the variables in the brief. Generate secrets with
`openssl rand -base64 48`. The app refuses to start if a secret is missing or too short.

**frontend/.env** (copy from `.env.example`): `VITE_API_URL=http://localhost:8080/api`

## 6. Run

```bash
# Backend (terminal 1)
cd backend
cp .env.example .env        # then fill in DB_PASSWORD, RESEND_API_KEY, EMAIL_FROM and the secrets
mvn spring-boot:run         # http://localhost:8080

# Frontend (terminal 2)
cd frontend
cp .env.example .env
npm install
npm run dev                 # http://localhost:5173
```

Open http://localhost:5173 → you are redirected to Login. Click **Create an account**.

## 7. API

All responses use `{ "success": true, "message": "...", "data": {...} }` or
`{ "success": false, "message": "...", "code": "INVALID_OTP" }` (plus `retryAfter` for cooldowns, `errors` for field validation).

| Endpoint | Body | Result |
|---|---|---|
| `POST /api/auth/register` | username, email, password | Creates unverified account, emails OTP |
| `POST /api/auth/verify-register-otp` | email, otp | Verifies email → access token + refresh cookie |
| `POST /api/auth/login` | email, password | Checks password, emails OTP (no tokens yet) |
| `POST /api/auth/verify-login-otp` | email, otp | Access token + refresh cookie |
| `POST /api/auth/resend-otp` | email, purpose (`REGISTER`/`LOGIN`/`RESET_PASSWORD`) | New OTP (respects cooldown) |
| `POST /api/auth/forgot-password` | email | Emails OTP; identical response for unknown emails |
| `POST /api/auth/verify-reset-otp` | email, otp | Returns one-time `resetToken` (10 min) |
| `POST /api/auth/reset-password` | email, resetToken, newPassword, confirmPassword | Sets password, revokes all sessions |
| `POST /api/auth/refresh` | refresh cookie | Rotates refresh token, returns new access token |
| `POST /api/auth/logout` | refresh cookie | Revokes the session, clears cookie |
| `GET /api/auth/me` | `Authorization: Bearer …` | Current user (protected-endpoint check) |

## 8. How authentication works

**Register:** form → `register` (validates, stores user with `emailVerified=false` and a BCrypt hash, emails OTP) → `verify-register-otp`
(marks email verified, issues tokens) → Home. Registering again with an unverified email replaces the pending account; a verified email gets `EMAIL_ALREADY_EXISTS`.

**Login:** `login` checks the password and emails an OTP — **no tokens are issued** → `verify-login-otp` issues tokens → Home.
An unverified account that logs in with the right password is routed back to the registration OTP step.

**Forgot password:** `forgot-password` (always "sent") → `verify-reset-otp` returns a random single-use reset token → `reset-password`
(token + new password + confirmation) → all refresh sessions are revoked → Login.

**Tokens:**
- Access token: JWT, 15 min, sent as `Authorization: Bearer`, kept **in memory only** (never localStorage).
- Refresh token: JWT, 7 days, sent only as an **HttpOnly, SameSite cookie** scoped to `/api/auth`, so JavaScript (and any XSS) cannot read it.
  The database stores only its SHA-256 hash, plus a `session_id` shared by all rotations of one login.
- `refresh` rotates: the used token is revoked and a new one issued. Re-using an old rotated token (after a 10 s race window) revokes the entire session.
- The frontend refreshes the access token one minute before expiry, on any 401 (then retries the request), and on page load (restores the session from the cookie).
  Concurrent refreshes are de-duplicated (one request per tab, Web Locks across tabs).
- `TokenService.revokeAllForUser()` already exists, so "logout all devices" in a later phase is just an endpoint.

**OTP rules:** exactly 4 digits from `SecureRandom`; stored as HMAC-SHA256 keyed with `OTP_SECRET` (never plaintext, never logged, never returned by the API);
expires in 5 min; max 5 wrong attempts; single-use; a new OTP invalidates older ones; 60 s resend cooldown.
For the reset flow all failures return the same generic error so emails can't be enumerated.

## 9. Known limits (intentional, later phases)

- Login password attempts are not rate-limited yet — Redis rate limiting is Phase 10 (OTP attempts and resends are already limited).
- Usernames are not unique (only emails are); uniqueness/search rules belong with profiles in Phase 2/3.
- Schema is managed by Hibernate `ddl-auto: update` until Phase 13.
- No automated tests are included in Phase 1.

---

# Phase 2 — Profile + Settings

## What was added

- **Profile page** (`/profile`): picture (upload with preview, replace, remove, default avatar), username (edit/save/cancel), email (read-only), verification status, member-since date.
- **Settings page** (`/settings`): theme (Light / Dark / System, saved across refreshes), account info, Logout, Change password.
- **Navigation**: Home · Profile · Settings · Logout in a top bar (links move to their own row on phones).
- Dark mode now applies to the whole app, including the Phase 1 login/register screens.

## Updated project structure (new or changed files only)

```text
backend/src/main/
├── resources/application.yml                 + multipart limits, app.storage.*
└── java/com/vtchat/
    ├── VtChatApplication.java                changed: headless AWT for image processing
    ├── config/AppProperties.java             changed: Storage settings
    ├── config/SecurityConfig.java            changed: GET /api/avatars/** is public
    ├── controller/UserController.java        NEW  /api/users/me/**
    ├── controller/AvatarController.java      NEW  GET /api/avatars/{file}
    ├── service/UserService.java              NEW  profile, picture and password logic
    ├── service/AvatarStorageService.java     NEW  validate, re-encode and store images
    ├── service/TokenService.java             changed: revokeOtherSessions()
    ├── service/AuthService.java              changed: returns UserResponse
    ├── dto/user/UserResponse.java            NEW  (replaces AuthResponses.UserInfo)
    ├── dto/user/UpdateProfileRequest.java    NEW
    ├── dto/user/ChangePasswordRequest.java   NEW
    ├── dto/AuthResponses.java                changed: Session.user is a UserResponse
    ├── entity/User.java                      changed: + profilePicture
    ├── repository/RefreshTokenRepository.java  changed: + revokeOtherSessions
    ├── security/JwtService.java              changed: access token carries the session id ("sid")
    ├── security/AuthenticatedUser.java       changed: + sessionId
    └── exception/GlobalExceptionHandler.java changed: upload errors (413 / missing file)

frontend/src/
├── index.css, ../index.html                  changed: class-based dark mode, no theme flash on load
├── App.jsx                                   changed: ThemeProvider
├── context/ThemeContext.jsx                  NEW
├── context/AuthContext.jsx                   changed: + updateUser()
├── hooks/useTheme.js                         NEW
├── services/userService.js                   NEW
├── pages/Profile.jsx, Settings.jsx           NEW      (Home.jsx changed)
├── routes/AppRoutes.jsx                      changed: /profile, /settings (protected)
├── layouts/AppLayout.jsx                     changed: navigation bar
├── components/profile/                       NEW  Avatar, ProfilePictureEditor, ProfileDetails, VerifiedBadge
├── components/settings/                      NEW  SettingsSection, ThemeSelector, AccountInfo, ChangePasswordForm
├── components/Icons.jsx, Button.jsx, TextField.jsx, ...   changed: dark-mode classes, more icons
└── utils/                                    NEW  assets.js, image.js, format.js   (alerts.js changed: dark-aware)
```

## Database changes

One new nullable column on the existing `users` table (no new table). Hibernate adds it automatically on startup; the manual equivalent is:

```sql
ALTER TABLE users ADD COLUMN profile_picture VARCHAR(64) NULL;
```

It stores only a generated file name such as `3f2c9a4e-....jpg` — never a path or URL.

## Environment variables (backend, both optional)

| Variable | Default | Purpose |
|---|---|---|
| `UPLOAD_DIR` | `./uploads` | Where files are stored (pictures go in `UPLOAD_DIR/avatars`). Keep this folder on persistent storage in production. |
| `AVATAR_MAX_MB` | `2` | Maximum picture size in MB. If you change it, also change `MAX_AVATAR_MB` in `frontend/src/utils/image.js` (that only affects the early client-side check). |

No new frontend variables.

## API endpoints

All `/api/users/me/**` endpoints require `Authorization: Bearer <access token>`. The user always comes from the token — no user id is accepted anywhere.

| Endpoint | Body | Result |
|---|---|---|
| `GET /api/users/me` | — | Current user |
| `PUT /api/users/me` | `{ "username": "new_name" }` | Updated user (username trimmed, 3-30 chars, letters/numbers/`.`/`-`/`_`) |
| `POST /api/users/me/profile-picture` | multipart form, field `file` (JPEG/PNG) | Updated user |
| `DELETE /api/users/me/profile-picture` | — | Updated user (default avatar again) |
| `PUT /api/users/me/password` | `{ currentPassword, newPassword, confirmPassword }` | Success message |
| `GET /api/avatars/{file}` | — | The image (public, unguessable name, cacheable) |

The user object is `{ id, username, email, emailVerified, profilePictureUrl, createdAt }`. `profilePictureUrl` is a relative URL (`/api/avatars/<id>.jpg`) or `null`.
No password hash, token or OTP data is ever returned. `GET /api/auth/me` from Phase 1 still works and returns the same shape.

New error codes: `FILE_TOO_LARGE` (413), `UNSUPPORTED_FILE_TYPE`, `INVALID_IMAGE`, `EMPTY_FILE`, `INVALID_CURRENT_PASSWORD`, `PASSWORD_UNCHANGED` (all 400 except the first).

## Frontend routes

| Route | Access |
|---|---|
| `/home` | logged in |
| `/profile` | logged in (new) |
| `/settings` | logged in (new) |
| `/login`, `/register`, `/verify-otp`, `/verify-login-otp`, `/forgot-password`, `/verify-reset-otp`, `/reset-password` | logged out only |

Unauthenticated visits to protected routes redirect to `/login` (Phase 1 `ProtectedRoute`, reused unchanged).

## How profile-picture upload works

1. The user picks a file. The browser checks type (JPEG/PNG) and size and shows a **local preview**; nothing is uploaded yet.
2. **Upload** sends it as `multipart/form-data` (with a progress bar) using the same axios instance, so the access token is attached and refreshed automatically.
3. The server never trusts the file name or `Content-Type`. `AvatarStorageService`:
   - rejects empty files, files over the size limit, and anything that is not really a JPEG or PNG (detected from the bytes);
   - checks the pixel dimensions **before** decoding (blocks "decompression bomb" images);
   - decodes the image, center-crops it to a square, scales it to at most 512×512 and **re-encodes it as JPEG** — this strips metadata and any hidden payload, and transparent PNGs are flattened onto white;
   - saves it as `<random-UUID>.jpg` in `UPLOAD_DIR/avatars` and stores only that name in `users.profile_picture`.
4. The previous file is deleted. Because every upload gets a new random name, old URLs stop working and browsers can cache pictures indefinitely.
5. Pictures are served by `GET /api/avatars/{file}`. Only names matching the generated UUID pattern are accepted, so path traversal is impossible. They are public-but-unguessable so plain `<img>` tags work (later phases will show other users' pictures in chat).
6. **Remove** deletes the file and sets the column to `NULL`; the UI shows the default avatar. The navigation bar updates immediately via `AuthContext.updateUser`.

## How password change works

1. Settings → Change password: current password, new password (same strength rules as registration, with a live checklist) and confirmation.
2. `PUT /api/users/me/password` verifies the current password against the stored BCrypt hash. A wrong one returns **400** `INVALID_CURRENT_PASSWORD` (deliberately not 401, which the frontend would treat as an expired token).
3. The new password must differ from the current one, is hashed with BCrypt and saved; no plaintext is stored or returned.
4. **All other devices are signed out**: every refresh-token session except the current one is revoked. The current session is identified by a `sid` claim that access tokens now carry, so the person changing the password stays logged in. (Tokens issued before this update have no `sid`; for those, all sessions are revoked — just log in again once after upgrading.)
5. The forgot-password OTP flow is not involved.

## Run (same as Phase 1)

```bash
cd backend && mvn spring-boot:run     # http://localhost:8080  (adds the new column and ./uploads on first start)
cd frontend && npm install && npm run dev   # http://localhost:5173
```

Then log in and open **Profile** / **Settings** from the top bar.

## Phase 2 limits (intentional)

- The current-password check is not rate-limited yet (Redis rate limiting is Phase 10).
- Pictures are stored on local disk; for multiple servers or cloud hosting, move them to object storage later (only `AvatarStorageService` would change).
- Only JPEG and PNG are accepted; animated GIFs and WebP are not.
- No automated tests are included.


---

# Phase 3 — User Search + Private One-to-One Chat

> Honest note: the build sandbox could not reach Maven Central or the npm registry, so this code was **reviewed by hand but never compiled or run**. Run the commands below once and fix anything the compiler reports.

## 1. New / changed files

```text
backend/src/main/java/com/vtchat/
  entity/Conversation.java                 NEW
  repository/ConversationRepository.java   NEW
  repository/UserRepository.java           + search query
  service/ConversationService.java         NEW  start / list / get + authorization
  service/ConversationCreator.java         NEW  find-or-create in its own small transaction
  service/UserService.java                 + searchUsers
  controller/UserSearchController.java     NEW  GET /api/users/search
  controller/ConversationController.java   NEW
  dto/user/UserSearchResponse.java         NEW
  dto/conversation/*.java                  NEW  CreateConversationRequest, ConversationResponse, ConversationSummaryResponse
  exception/ApiException.java              + forbidden(), notFound()
  exception/GlobalExceptionHandler.java    + bad query parameter -> 400
frontend/src/
  pages/Chats.jsx, pages/Chat.jsx          NEW
  components/chat/                         NEW  ChatsShell, UserSearch, UserPreviewModal, ConversationList, ChatPanel
  services/conversationService.js          NEW
  services/userService.js                  + searchUsers
  hooks/useDebounce.js                     NEW
  utils/errors.js, utils/format.js         + getChatErrorMessage, formatRelativeTime
  components/Icons.jsx                     + Chat, Search, ArrowLeft, Close, Send
  layouts/AppLayout.jsx, routes/AppRoutes.jsx, pages/Home.jsx   Chats link + routes
```

## 2. Database change

Hibernate (`ddl-auto: update`) creates one new table:

```text
conversations(id PK, user_one_id FK->users, user_two_id FK->users, created_at, updated_at)
  UNIQUE uk_conversations_pair (user_one_id, user_two_id)
  INDEX (user_one_id, updated_at), INDEX (user_two_id, updated_at)
```
`user_one_id` is always the smaller user id.

## 3. Endpoints (all need `Authorization: Bearer <access token>`)

| Method | Path | Result |
|---|---|---|
| GET | `/api/users/search?query=tam` | 200 list of users; 400 `QUERY_TOO_SHORT` / `QUERY_TOO_LONG` |
| POST | `/api/conversations` | 201 created, 200 already existed; 400 `SELF_CONVERSATION`; 404 `USER_NOT_FOUND`; 422/400 validation |
| GET | `/api/conversations` | 200 list, newest activity first |
| GET | `/api/conversations/{id}` | 200; 403 not a participant; 404 missing |

Examples:

```http
GET /api/users/search?query=tam
{ "success": true, "message": "OK",
  "data": [ { "id": 7, "username": "tamil", "email": "tamil@example.com",
              "emailVerified": true, "profilePictureUrl": "/api/avatars/3f2c....jpg" } ] }

POST /api/conversations        { "userId": 7 }
{ "success": true, "message": "Conversation created",
  "data": { "id": 12, "currentUser": { ... }, "otherUser": { "id": 7, ... },
            "createdAt": "2026-10-04T10:00:00", "updatedAt": "2026-10-04T10:00:00" } }

GET /api/conversations
{ "success": true, "message": "OK",
  "data": [ { "id": 12, "otherUser": { "id": 7, "username": "tamil", "profilePictureUrl": null },
              "updatedAt": "2026-10-04T10:00:00" } ] }

POST /api/conversations { "userId": <your own id> }
400 { "success": false, "message": "You cannot chat with yourself", "code": "SELF_CONVERSATION" }

GET /api/conversations/99      (someone else's)
403 { "success": false, "message": "You do not have access to this conversation", "code": "FORBIDDEN" }
```

## 4. Frontend routes

`/chats` (search + conversation list; on phones this is the whole screen) and `/chat/:conversationId` (the open chat; Back button on phones). Both are protected. The nav bar is Home, Chats, Profile, Settings, Logout.

## 5. Search implementation

`UserRepository.search` matches `lower(username)` or `lower(email)` with `LIKE %text%`, excludes the current user and unverified accounts, orders by username and is capped at 20 rows by `PageRequest`. Text must be 2-50 characters; `%`, `_` and `!` are escaped so they match literally. Only the public fields in `UserSearchResponse` are returned (never password hash, tokens or OTP data). The UI debounces input by 350 ms and ignores out-of-date responses.

## 6. Conversation creation and duplicate prevention

1. The current user comes from the access token; the request only contains the other user's id.
2. Self-chat -> 400. Target missing or unverified -> 404.
3. Ids are put in canonical order (smaller = `userOne`), so A->B and B->A are the same pair.
4. `ConversationCreator.findOrCreate` looks the pair up and inserts only if absent.
5. The DB unique constraint is the real guarantee. If two requests race, the loser gets a constraint violation (rolled back in the small creator transaction only), reloads the winner's row and returns it as "already exists".

## 7. Authorization

Listing only returns conversations that contain the current user. Opening one checks participation: 404 if it does not exist, 403 if it does but you are not in it. No endpoint accepts a "current user id" from the client.

## 8. Run

Same as before: set `.env` (see `backend/.env.example`), then `cd backend && mvn spring-boot:run` and `cd frontend && npm install && npm run dev`. Register and verify two accounts, sign in as one, open **Chats**, search for the other and press **Start Chat**.

## 9. Phase 3 limits (intentional)

No messages, WebSocket/STOMP, typing, presence, read receipts, media, calls, notifications, Redis, rate limiting or AI. The message box is disabled and the status is a placeholder. Phase 4 has not been started.


---

# Phase 4 — Real-Time Messaging

> Honest note: the build sandbox could not reach Maven Central or the npm registry, so this code was **reviewed by hand but never compiled or run**. Run the commands in section 12/13 once and fix anything the compiler reports. Real-time behaviour (reconnects, two browsers) especially needs a manual test.

## 1. Project structure (new / changed)

```text
backend/
  pom.xml                                          + spring-boot-starter-websocket
  src/main/java/com/vtchat/
    entity/Message.java                            NEW
    repository/MessageRepository.java              NEW
    service/MessageService.java                    NEW  history, validation, save
    service/ConversationService.java               list() adds latest-message preview
    controller/MessageController.java              NEW  GET history (REST)
    controller/ChatWebSocketController.java        NEW  /app/chat.send (STOMP)
    config/WebSocketConfig.java                    NEW  /ws, /app, /topic, limits, heartbeats
    config/SecurityConfig.java                     /ws/** transport permitted (auth happens at STOMP CONNECT)
    security/StompAuthChannelInterceptor.java      NEW  JWT + authorization for every STOMP frame
    security/StompUser.java, WebSocketAuthException.java   NEW
    security/JwtService.java                       + parseAccessTokenWithExpiry
    websocket/SafeStompErrorHandler.java           NEW  generic STOMP ERROR text
    dto/message/MessageRequest, MessageResponse, WsErrorResponse   NEW
    dto/conversation/ConversationSummaryResponse   + lastMessage
frontend/
  package.json                                     + @stomp/stompjs, sockjs-client
  vite.config.js                                   define global -> globalThis (sockjs-client)
  src/services/websocketService.js                 NEW  single shared STOMP connection
  src/hooks/useChatSocket.js                       NEW
  src/services/conversationService.js              + getMessages
  src/components/chat/  ChatPanel (rewritten), MessageList, MessageBubble, MessageInput,
                        ConnectionStatus (NEW), ConversationList + ChatsShell (live previews)
  src/utils/messages.js, format.js, alerts.js      merge/order helper, time formats, toastError
  src/context/AuthContext.jsx                      closes the socket on logout
```

## 2. Database change

New table `messages` (created by Hibernate `ddl-auto: update`):

```text
messages(id PK, conversation_id FK->conversations.id, sender_id FK->users.id,
         content varchar(2000) NOT NULL, created_at)
  INDEX idx_messages_conversation_created (conversation_id, created_at)
  INDEX idx_messages_sender (sender_id)
```
The composite index serves both "conversation X in time order" and "newest message of X". Sending a message also bumps `conversations.updated_at`, so the list stays ordered by latest activity.

## 3. REST

`GET /api/conversations/{conversationId}/messages` (Bearer token) returns the newest 200 messages, oldest first, in the usual envelope:

```json
{ "success": true, "message": "OK", "data": [
  { "id": 1, "conversationId": 10, "senderId": 2, "content": "Hello!", "createdAt": "2026-10-03T18:00:00Z" },
  { "id": 2, "conversationId": 10, "senderId": 5, "content": "Hi!",    "createdAt": "2026-10-03T18:01:00Z" } ] }
```
404 `CONVERSATION_NOT_FOUND` if missing, 403 `FORBIDDEN` if you are not a participant, 401 without a valid token. `GET /api/conversations` now also returns `lastMessage {id, senderId, content(<=100 chars), createdAt}` (absent when empty).

## 4. WebSocket / STOMP

| What | Value |
|---|---|
| Endpoint | `/ws` (SockJS, allowed origins = `CLIENT_URL`) |
| Client sends | `SEND /app/chat.send` with `{"conversationId":10,"content":"Hello!"}` (no sender field) |
| Client subscribes | `/topic/chat/{conversationId}` (members only) and `/user/queue/errors` |
| Server broadcasts | the saved `MessageResponse` to `/topic/chat/{conversationId}` |
| Failures | `{status, code, message}` to the sender only on `/user/queue/errors` (403, 404, 422, 500) |

## 5. Connection flow

1. The user opens `/chats` or `/chat/:id`; `useChatSocket` calls `websocketService.retain()`.
2. The service opens one SockJS connection to `/ws` and sends STOMP `CONNECT` with `Authorization: Bearer <access token>`.
3. The server validates the JWT, stores the user (id from the token) on the session and answers `CONNECTED`.
4. The client subscribes to `/user/queue/errors` and to the topics of the open conversations.
5. If the socket drops: status becomes *Reconnecting...*, retries with back-off (1 s doubling up to 15 s), fetches a fresh token, re-subscribes, and the chat re-loads history to fill any gap.
6. When the access token is refreshed (about every 15 minutes) the socket reconnects once with the new token. On logout the socket is closed. Leaving the chat pages closes it after 2 s unless another page needs it.

## 6. Sending / receiving flow

```text
type -> Enter -> STOMP SEND /app/chat.send
  -> interceptor: authenticated? token not expired? destination allowed?
  -> ChatWebSocketController -> MessageService.send (sender = session user):
       validate (non-empty, <=2000 chars) -> is the sender a participant? -> INSERT message, bump conversation
  -> transaction committed -> convertAndSend /topic/chat/{id}
  -> both users' tabs receive the saved message -> merged by database id -> shown right/left
```
Nothing is shown or broadcast before it is saved, and the sender sees their own message when the broadcast returns (no optimistic copy, so no duplicates; the id is the identity). History + live messages are merged by id and ordered by `createdAt`, then `id`.

## 7. Authentication and authorization

- No anonymous chat: the transport URL is open, but `CONNECT` without a valid access token is refused and every other frame requires the connected user.
- The sender is never read from the payload. `senderId`/`userId` from a client are ignored (the DTO has no such field).
- `SEND` is only accepted for `/app/chat.send`, so clients cannot publish directly to a topic.
- `SUBSCRIBE` is only accepted for `/topic/chat/{numeric id}` of a conversation the user belongs to, or their own `/user/queue/errors`. No wildcards, no global topic. A non-member gets the same refusal whether the conversation exists or not.
- `MessageService` re-checks membership on every send, so even a valid subscriber cannot send into a conversation they are not in.
- A socket is rejected once its access token expires (the client reconnects with the refreshed one).
- Error frames and error payloads contain only safe, generic text.

## 8. Frontend routes and services

Routes unchanged: `/chats`, `/chat/:conversationId`. Services: `conversationService.getMessages`, `websocketService` (retain/release, subscribeToConversation, send, onStatus, onError, refreshAuth), hook `useChatSocket`. UI: right/left bubbles with time, Enter sends, Shift+Enter new line, send disabled when disconnected/empty/over 2000 chars, auto-scroll only when near the bottom, connection pill (Connected / Connecting... / Reconnecting... / Disconnected), live preview + time + move-to-top in the conversation list. The Phase 3 "status unavailable" placeholder was removed because presence is a later phase.

## 9. Dependencies

Backend: `spring-boot-starter-websocket`. Frontend: `@stomp/stompjs`, `sockjs-client` (run `npm install`).

## 10. Environment variables

None new. `CLIENT_URL` now also controls which origins may open `/ws`. `VITE_API_URL` is reused (the socket URL is its origin + `/ws`).

## 11. Architecture in brief

REST loads data (conversation, history); STOMP carries live messages. One `/topic` per conversation; the Spring simple in-memory broker fans messages out (fine for a single server instance - scaling out would need an external broker, a later concern). MySQL is the source of truth; the socket is just delivery.

## 12. Run the backend

`cd backend && mvn spring-boot:run` (same `.env` as before).

## 13. Run the frontend

`cd frontend && npm install && npm run dev`, then sign in as two different users in two browsers (or one normal + one private window), start a chat from one and type in both.

## 14. Phase 4 limits (intentional)

No typing indicators, presence, read/delivered status, reactions, reply/edit/delete/forward, search, media, voice, calls, push notifications, unread counts, Redis, rate limiting or AI. History is capped at the newest 200 messages (no older-message paging yet). A conversation created by someone else appears in your list after a refresh. Phase 5 has not been started.


---

# Phase 5 — Advanced Message Features

> Honest note: the build sandbox could not reach Maven Central or the npm registry, so this code was **reviewed by hand but never compiled or run**. Compile both projects once and test with two browsers; the real-time paths especially need a manual check.

## 1. Project structure (new / changed)

```text
backend/src/main/java/com/vtchat/
  entity/Message.java              + replyToMessage, updatedAt, edited, deleted, deletedAt, forwarded, forwardedFromMessageId
  entity/MessageReaction.java      NEW      entity/MessageDeletion.java   NEW
  repository/MessageRepository.java (rewritten queries)   MessageReactionRepository, MessageDeletionRepository   NEW
  service/MessageService.java      history/search/send/reply/edit/delete-for-everyone/forward
  service/MessageReactionService.java, MessageDeletionService.java   NEW
  service/MessageAssembler.java    NEW  the only place that decides what a client may see
  service/MessageActionService.java NEW  run change -> (after commit) publish event; shared by REST and STOMP
  websocket/MessageEventPublisher.java NEW
  controller/MessageController.java (REST)   controller/ChatWebSocketController.java (STOMP)
  security/StompAuthChannelInterceptor.java  allows the new destinations and /user/queue/messages
  dto/message/  SendMessageRequest, EditMessageRequest, ReactionRequest, ReactionResponse, ForwardMessageRequest,
                DeleteMessageRequest, DeleteScope, DeleteMessageResponse, MessageSearchResponse, MessageResponse, MessageEvent
  util/TextUtil.java
frontend/src/
  utils/messageEvents.js           NEW  the central event handler / reducers
  services/messageService.js       NEW  REST + "STOMP first, REST fallback" actions
  services/websocketService.js     publish(), user-queue events
  components/chat/  ChatPanel (rewritten), MessageBubble, MessageList, MessageActionMenu, MessageInput,
                    ComposerBanner, SelectionBar, MessageSearchPanel, ForwardModal (NEW), ConversationList/ChatsShell (updated)
  utils/clipboard.js, alerts.js (delete dialog), messages.js, Icons.jsx
```

## 2. Database changes / migration

Hibernate `ddl-auto: update` applies everything on start; no manual migration is needed.

- `messages`: new columns `reply_to_message_id` (FK to messages), `updated_at` (nullable, so Phase 4 rows are fine), `edited`, `deleted`, `forwarded` (NOT NULL, default false), `deleted_at`, `forwarded_from_message_id`; new indexes on `created_at` and `reply_to_message_id` (existing: `(conversation_id, created_at)`, `sender_id`).
- `message_reactions(id, message_id, user_id, reaction, created_at)` with `UNIQUE(message_id, user_id)` and indexes on both ids.
- `message_deletions(id, message_id, user_id, deleted_at)` with `UNIQUE(message_id, user_id)` and indexes on both ids.

Emoji need a utf8mb4 database/table. MySQL 8 defaults to utf8mb4, and the database is created by the app; if you created it yourself with an older charset run `ALTER DATABASE vtchat CHARACTER SET utf8mb4;` and convert `message_reactions` and `messages` with `ALTER TABLE ... CONVERT TO CHARACTER SET utf8mb4;`.
To start clean instead, drop the database and let the app recreate it.

## 3. REST endpoints (Bearer token required)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/conversations/{id}/messages[?aroundId=]` | newest 200, oldest first; skips messages you deleted for yourself; `aroundId` returns a window around an older message |
| GET | `/api/conversations/{id}/messages/search?query=` | 2-100 chars, case-insensitive, max 50, newest first |
| PUT | `/api/messages/{id}` `{"content"}` | edit (sender only) |
| POST | `/api/messages/{id}/reactions` `{"reaction":"❤️"}` | add or change |
| DELETE | `/api/messages/{id}/reactions` | remove mine |
| DELETE | `/api/messages/{id}/for-me` | delete for me |
| DELETE | `/api/messages/{id}/for-everyone` | sender only |
| POST | `/api/messages/{id}/forward` `{"conversationId": dest}` | returns the new message |

Errors: 400, 401, 403 (not a participant / `CANNOT_EDIT` / `CANNOT_DELETE_FOR_EVERYONE`), 404 (`MESSAGE_NOT_FOUND`, `CONVERSATION_NOT_FOUND`), 409 (`MESSAGE_DELETED`), 422 (empty / too long / bad reaction / bad reply target). Messages are the friendly ones from the spec ("You cannot edit this message", ...).

Message JSON (history and events); a message deleted for everyone has `content` omitted, `deleted: true`, no reactions and no reply info:

```json
{ "id": 25, "conversationId": 10, "senderId": 2, "content": "Hello!", "edited": false, "deleted": false,
  "replyToMessage": { "id": 20, "senderId": 5, "content": "How are you?", "deleted": false, "unavailable": false },
  "reactions": [ { "userId": 5, "reaction": "👍" } ], "forwarded": false, "status": "SENT",
  "createdAt": "...", "updatedAt": "..." }
```

## 4. WebSocket / STOMP events

Client sends (the sender is always the authenticated session user): `/app/chat.send` `{conversationId, content, replyToMessageId?}`, `/app/message.react` `{messageId, reaction}`, `/app/message.unreact` `{messageId}`, `/app/message.edit` `{messageId, content}`, `/app/message.delete` `{messageId, scope: "ME"|"EVERYONE"}`.

Server pushes `{type, conversationId, messageId, message?, reactions?}`:

| type | destination |
|---|---|
| `MESSAGE_CREATED`, `MESSAGE_UPDATED`, `MESSAGE_DELETED_FOR_EVERYONE`, `REACTION_UPDATED` | `/topic/chat/{conversationId}` (both users) |
| `MESSAGE_DELETED_FOR_ME` | `/user/queue/messages` (only the requesting user's tabs) |
| action failures `{status, code, message}` | `/user/queue/errors` |

## 5-11. How each feature works

- **Reactions:** one row per (message, user), written with one atomic `INSERT ... ON DUPLICATE KEY UPDATE`, so a double click or two tabs can never create two. Same emoji again removes it; a different one changes it. The six emoji are whitelisted server-side (variation selector ignored). After every change the full reaction list is broadcast (`REACTION_UPDATED`), so the sender and the other user converge on the same state; the UI also ignores repeat clicks for half a second. Deleted messages reject reactions (409) and show none.
- **Reply:** `messages.reply_to_message_id` points at the original (nothing duplicated). The server checks the original exists, is in the same conversation and is not deleted, and that you belong to it. The reply carries a 150-character preview; if the original is later deleted for everyone the preview becomes "This message was deleted", and if you deleted it for yourself it shows "Original message unavailable". Clicking a preview scrolls to the original (loading a window of older messages if needed). The composer shows "Replying to John" with Cancel Reply.
- **Edit:** sender only, same row, `edited = true`, `updatedAt` set, unchanged text is a no-op, deleted messages are rejected. Broadcast as `MESSAGE_UPDATED`; the bubble shows "edited". In the UI, Edit loads the text into the message box ("Editing message", Save, Esc to cancel).
- **Delete for me:** inserts a `message_deletions` row (idempotent `INSERT IGNORE` on the unique key). The message row is untouched, history/search/list previews skip it for that user only, and `MESSAGE_DELETED_FOR_ME` goes only to that user.
- **Delete for everyone:** sender only (403 otherwise). Sets `deleted = true`, `deletedAt`, keeps the row, broadcasts the tombstone, which keeps its place and reads "This message was deleted". Note: the original text stays in the database column but is never returned by any API, search, forward, copy or reply preview.
- **Forward:** copies the text into a conversation you belong to as a new message (`forwarded = true`, `forwardedFromMessageId`), shows a "Forwarded" label, never touches the original, refuses deleted messages and ones you hid. UI: Forward dialog with conversation search, then a SweetAlert2 success message. Several selected messages are forwarded in order.
- **Copy:** Clipboard API (with a fallback), success toast; not offered for deleted messages.
- **Selection:** menu "Select" (or the checkboxes then appear on every message); tap to toggle; the bar shows "N selected - Copy, Forward, Delete, Cancel". Bulk delete offers "Delete for everyone" only if every selected message is yours and not deleted.
- **Menu:** right click, the ⋮ button, or long press (450 ms) opens it; it contains the six reactions, Reply, Copy, Forward, Edit (own messages only), Select, Delete. Delete opens a SweetAlert2 dialog with Delete for me / Delete for everyone (the latter only for your own messages).
- **Search:** header search icon; debounced; results show sender, date/time and the highlighted text; clicking scrolls to and flashes the message. Deleted-for-everyone and hidden-for-me messages never appear.
- **Status:** own messages show a single sent tick; `status: "SENT"` is in the API for later phases.

## 12. Central frontend event handler

`utils/messageEvents.js` is the only code that interprets events. `reduceMessages` (open chat) and `reduceConversations` (list preview/order) are pure; duplicates are ignored (by id), an older edit never overwrites a newer one (by `updatedAt`), tombstones replace in place, delete-for-me removes locally and remembers the id. Topic events, user-queue events and REST-fallback results all pass through the same handler.

## 13. Security

Every operation checks conversation membership first; the user always comes from the JWT/STOMP session, never the payload. Edit and delete-for-everyone additionally require `sender == current user`. STOMP SEND is limited to the five destinations above, SUBSCRIBE to your own conversations' topics and your two user queues. Original text of globally deleted messages is not in any response (`MessageAssembler`), search, forward or reply preview. Reactions on deleted messages are rejected and hidden.

## 14. Dependencies, run

No new dependencies. Backend: `cd backend && mvn spring-boot:run`. Frontend: `cd frontend && npm install && npm run dev`. Test with two different accounts in two browsers.

## 15. Phase 5 limits (intentional)

No media, voice, translation, calls, push notifications, typing, presence, delivered/read status, Redis or AI. Search jumps load a window around an older message, so after jumping very far back there may be a gap until you scroll to the newest messages (reload to reset). Edits do not keep history. Phase 6 has not been started.


---

# Phase 6 — Media + Voice Messages

> Nothing in this phase (or earlier ones) could be compiled or run in the build sandbox, because Maven Central and the npm registry are blocked there. The code was reviewed by hand and checked for syntax only. Run `mvn spring-boot:run` and `npm install && npm run dev` on your machine and report any compile error.

## 1. Project structure (new / changed)

```
backend/src/main/java/com/vtchat/
  config/AppProperties.java            + app.media.* (upload dir, size limits)
  controller/MediaController.java      NEW upload / voice / view / download / thumbnail / access
  dto/message/MediaUploadResponse.java, MessageMediaResponse.java   NEW
  dto/message/MessageResponse.java, MessageSearchResponse.java, dto/conversation/ConversationSummaryResponse.java   + media fields
  entity/MessageType.java              NEW  TEXT | IMAGE | VIDEO | FILE | VOICE
  entity/Message.java                  + message_type, media_key, media_name, media_size, mime_type, duration
  exception/GlobalExceptionHandler.java + MultipartException, unsupported media type
  repository/MessageRepository.java    + countByMediaKeyAndDeletedFalse, search also matches file names
  security/JwtService.java, JwtAuthenticationFilter.java   + signed per-message media links
  service/MediaStorageService.java     NEW storage abstraction (interface)
  service/LocalMediaStorageService.java NEW local-disk implementation
  service/MediaFileInspector.java      NEW whitelist + magic-byte validation
  service/ImageThumbnailer.java        NEW 480 px JPEG thumbnails
  service/MediaService.java, VoiceMessageService.java   NEW
  service/MessageService.java, MessageAssembler.java, MessageActionService.java   media support
frontend/src/
  services/mediaService.js             NEW upload (with progress / abort), signed-link cache, download
  hooks/useMediaUrls.js                NEW link loading + lazy loading near the viewport
  utils/media.js                       NEW type/size rules, labels, formatting
  components/chat/  MessageRenderer, ImageMessage, VideoMessage, FileMessage, VoiceMessage, MediaPreview (lightbox),
                    MediaUpload (preview, caption, progress, cancel), VoiceRecorder, AttachmentMenu, MediaStatus   NEW
  components/chat/  MessageBubble, MessageInput, ChatPanel, MessageActionMenu, ComposerBanner, ConversationList,
                    MessageSearchPanel, utils/messageEvents.js   updated
```

## 2. Database changes (Hibernate `ddl-auto: update` adds them)

`messages` gets: `message_type VARCHAR(16) NOT NULL DEFAULT 'TEXT'`, `media_key`, `media_name`, `media_size`, `mime_type VARCHAR(127)`, `duration INT`, and index `idx_messages_media_key`. Existing text messages become `TEXT` automatically. Only metadata is in MySQL, never file bytes. For a media message `content` is the optional caption (empty string when none).

## 3. Upload APIs (Bearer token required; multipart/form-data)

| Method | Path | Fields |
|---|---|---|
| POST | `/api/conversations/{id}/media` | `file` (required), `caption`, `replyToMessageId` |
| POST | `/api/conversations/{id}/voice` | `file` (required), `duration` seconds (required), `replyToMessageId` |

Response `201`: `{success, message, data: {message: <MessageResponse>}}`. The same message object is broadcast to the conversation topic as `MESSAGE_CREATED`. Errors: `400 EMPTY_FILE / INVALID_DURATION`, `403/404` (not your conversation), `413 FILE_TOO_LARGE`, `415 UNSUPPORTED_FILE_TYPE`, `422 EMPTY_FILE`.

## 4. Download APIs

| Method | Path | Notes |
|---|---|---|
| GET | `/api/media/{messageId}` | inline for image / video / voice; documents are always sent as an attachment. Supports HTTP Range (video seeking). |
| GET | `/api/media/{messageId}/download` | always `Content-Disposition: attachment` |
| GET | `/api/media/{messageId}/thumbnail` | JPEG thumbnail of an image (if one exists) |
| GET | `/api/media/{messageId}/access` | Bearer token; returns short-lived signed links (`url`, `thumbnailUrl`, `downloadUrl`, `expiresIn`) |

The three file routes accept either the Bearer header or a signed `?mt=` link (see section 11). Unknown, deleted, hidden and not-yours all answer the same `404 MEDIA_NOT_FOUND`.

## 5. Voice APIs

`POST /api/conversations/{id}/voice` (above). The container (WebM, Ogg, MP4/M4A, WAV, MP3) is detected from the file bytes, not from the browser's label. Duration must be greater than 0 and at most 3600 s.

## 6. WebSocket media flow

1. The client uploads over REST (progress, cancel and retry work normally).
2. The server writes the file, saves the message row, then broadcasts a normal `MESSAGE_CREATED` event on `/topic/chat/{conversationId}` containing **metadata only** (`messageType`, `mediaUrl` route, `mediaName`, `mediaSize`, `mimeType`, `duration`, caption).
3. The other client renders the message and fetches the bytes over HTTP when it scrolls near the screen.
No binary ever travels over STOMP. Reactions, replies, delete for me / everyone and forward use the existing Phase 5 events unchanged.

## 7. Storage configuration

`MediaStorageService` is an interface (`store`, `retrieve`, `delete`, thumbnails); `LocalMediaStorageService` keeps files under one configurable directory with sub-folders `images/ videos/ files/ voice/` (thumbnails in `images/thumbs/`). No absolute path is hard-coded. A cloud implementation (S3, GCS...) only needs to implement the interface.

## 8. Supported types and size limits

| Kind | Types | Default limit |
|---|---|---|
| Image | JPEG, PNG, WEBP, GIF | 10 MB |
| Video | MP4, WEBM, MOV | 50 MB |
| Document | PDF, DOC, DOCX, TXT, XLS, XLSX, ZIP | 25 MB |
| Voice | WebM, Ogg, MP4/M4A, WAV, MP3 recordings | 10 MB |

WEBP images are shown from the original (the JDK cannot decode WEBP to make a thumbnail).

## 9. Frontend media components

`MessageRenderer` picks the view by type: `ImageMessage` (thumbnail, click opens the `MediaPreview` lightbox with download and Esc to close), `VideoMessage` (HTML5 controls, `preload="metadata"`, never autoplays), `FileMessage` (icon, name, type, size, download), `VoiceMessage` (play / pause, seek, time; only one voice message plays at a time). `AttachmentMenu` (📎 Photo / Video / Document) feeds `MediaUpload` (preview, caption, progress bar, Cancel aborts the request, Retry after an error). Replies, forwards and deletes work on media messages; the chat list, reply previews and search show labels such as "📷 Photo". Media messages cannot be edited.

## 10. Voice recorder

`VoiceRecorder` uses `navigator.mediaDevices.getUserMedia` and `MediaRecorder` (WebM/Opus, Ogg/Opus or MP4, whichever the browser supports). Tap the microphone: recording starts after the browser permission prompt, with a timer and a Stop button; then you can listen to it and Send or discard (bin). Auto-stops at 15 minutes. Clear messages (no `alert()`) for a denied permission, no microphone, microphone in use, unsupported browser or insecure page. Microphones need `https://` or `http://localhost`.

## 11. Media security

- Whitelist by extension **and** magic-byte check of the content; the client's `Content-Type` is ignored and the server picks the MIME type. `.exe .sh .html .svg .jar` and everything else is refused.
- Stored under a server-generated `uuid.ext` key validated by a strict regex plus a path-containment check; the original file name is only display text (directories, control and right-to-left characters removed, 120 chars max). The storage key is never sent to clients.
- The upload directory is not served by any static route; every read goes through `MediaController` after a participant check.
- `<img>`, `<video>` and `<audio>` cannot send a Bearer header, so the client first calls `/access` and gets short-lived (1 hour) links signed with the server's access-token key, valid for **one message and one user** and accepted only on those GET routes.
- Responses carry `X-Content-Type-Options: nosniff` and `Content-Security-Policy: default-src 'none'; sandbox`; documents download as attachments; uploads are never executed.
- Delete for everyone removes the file once no other live message (for example a forward) uses it. If saving the row fails the stored file is removed again.

## 12. Dependencies

None new (Spring multipart and `javax.imageio` are built in).

## 13. Environment / configuration variables (backend)

`MEDIA_UPLOAD_DIR` (default `./uploads`, falls back to `UPLOAD_DIR`), `MAX_IMAGE_SIZE`, `MAX_VIDEO_SIZE`, `MAX_FILE_SIZE`, `MAX_VOICE_SIZE` (all MB), `MAX_UPLOAD_REQUEST_MB` (default 100, the overall multipart cap). Frontend (optional): `VITE_MAX_IMAGE_MB`, `VITE_MAX_VIDEO_MB`, `VITE_MAX_FILE_MB`, `VITE_MAX_VOICE_MB`. See `backend/.env.example`. If you put a reverse proxy in front, raise its body-size limit too (nginx `client_max_body_size`).

## 14. Run

Backend: `cd backend && mvn spring-boot:run`. Frontend: `cd frontend && npm install && npm run dev`. Same prerequisites as before; make sure the upload directory is writable.

## 15. Architecture in short

`MediaController` -> `MessageActionService` (not transactional) -> `MediaService` (validate with `MediaFileInspector`, write file via `MediaStorageService`, make thumbnail) -> `MessageService.sendMedia` (one transaction, row saved) -> only then `MessageEventPublisher` broadcasts metadata. Reads: `/access` hands out signed links, `MediaController` serves `ResponseEntity<Resource>` after `MediaService` checks participation, deletion and hide state.

## 16. Phase 6 limits (intentional)

No calls, WebRTC, screen sharing, translation, push notifications, typing indicators, read receipts, presence, Redis, rate limiting or AI. Phase 7 has not been started.


---

# Phase 7 — Message Translation + Thanglish Support (Python microservice, IndicTrans2)

> Not compiled or run in the build sandbox (Maven Central, the npm registry and Hugging Face are blocked there). Only the pure-Python detection / Thanglish normalization / routing logic of `translation-service` was run (16 unit tests, fake engine). **IndicTrans2 itself, FastAPI, the Spring client and the browser flow are hand-reviewed only.** Run it and report any error.

## 1. Architecture

```
React  ->  Spring Boot (TranslationService: auth, membership, cache in MySQL)
       ->  Python FastAPI (translation-service/)  ->  IndicTrans2
```

- Python does **translation only** (no auth, users, messages, WebSockets, MySQL, media, calls). The browser never talks to it.
- Exactly one translation path remains. Google Translate, LibreTranslate, the provider classes and their env vars were removed.
- The public API is unchanged: `POST /api/messages/{messageId}/translate` with `{"targetLanguage":"ta"}`; `GET /api/translation/languages`. The response keeps `sourceLanguage, sourceScript, romanized, targetLanguage, originalText, translatedText`.

## 2. Languages

en, ta, hi, ml, kn (en<->ta, ta<->hi/ml/kn, en<->hi/ml/kn). Extensible: add the language to the Python `LANGUAGES` registry (FLORES code, script) and to `translation.languages` in `application.yml`. Not supported: Romanized Hindi/other Romanized languages, and scripts outside the five (rejected with a clear message).

## 3. Run it

1. Python service (own virtualenv, see `translation-service/README.md`):
   ```bash
   cd translation-service
   python -m venv .venv && source .venv/bin/activate
   pip install -r requirements.txt
   cp .env.example .env        # set TRANSLATION_SERVICE_API_KEY
   python -m uvicorn app.main:app --host 0.0.0.0 --port 8001
   ```
   First start downloads the IndicTrans2 checkpoints (several GB) and loads them once into memory. `GET /health` shows `modelReady`.
2. Spring `.env` (same key as Python):
   ```
   TRANSLATION_SERVICE_URL=http://localhost:8001
   TRANSLATION_SERVICE_API_KEY=...
   TRANSLATION_SERVICE_TIMEOUT_MS=10000
   ```
3. Python env: `TRANSLATION_SERVICE_HOST`, `TRANSLATION_SERVICE_PORT`, `TRANSLATION_SERVICE_API_KEY`, `INDICTRANS2_MODEL` (optional single checkpoint; otherwise the three distilled ones are used).

Removed env vars: `TRANSLATION_PROVIDER`, `TRANSLATION_API_URL`, `TRANSLATION_API_KEY`, `TRANSLATION_TIMEOUT`, `TRANSLATION_ROMANIZED_STRATEGY`, `GOOGLE_TRANSLATE_API_KEY`, `LIBRETRANSLATE_*`.

## 4. Spring side

- `translation/TranslationServiceClient` — RestClient with connect/read timeouts, `X-Internal-Api-Key` header; failures become `TranslationServiceException` (UNAVAILABLE, TIMEOUT, REJECTED, AUTH, BAD_RESPONSE).
- `TranslationServiceImpl` — validates **before** calling Python: message exists, user is in the conversation, not deleted/hidden, TEXT, non-empty, length <= 2000, target supported. Then cache lookup (`message_translations`, unique message + target, keyed by a hash of the text) and a Python call only on a miss; identical concurrent requests share one call; a result for a message edited meanwhile is discarded. Edit / delete-for-everyone already clear cached translations.
- Errors (clean JSON, no Python or model details): Python down -> 503 "Translation service is temporarily unavailable."; timeout -> 504 and other failures -> 503 "Unable to translate this message right now."; Python rejecting a request -> 422.
- Logs: request (ids and length only), success, unavailable, timeout, failure. No message text, no key.
- No new WebSocket events; translation stays per-user REST.

## 5. Python side (`translation-service/`)

- `language_service` — Unicode script detection and the language registry / direction routing (en->indic, indic->en, indic->indic checkpoints).
- `tanglish_service` — detects Romanized Tamil from a lexicon + sub-word patterns (a few Latin words in an English sentence do not flip it), then transliterates to Tamil script; English words such as "office", "now", "good" are kept. This is detection and normalization only, **not** a dictionary translator.
- `translation_service` — Tamil script -> IndicTrans2; Thanglish -> Tamil script -> IndicTrans2; same language -> unchanged; Thanglish -> Tamil needs no model; no letters -> unchanged.
- Models load once in the FastAPI lifespan. The API key is checked with a constant-time compare, and the service refuses to start without one.

## 6. Testing checklist

Python unit tests: `cd translation-service && python -I -m unittest discover -s tests -t .` (passed here). End-to-end (needs the model): en->ta, ta->en, ta->hi (வணக்கம் -> नमस्ते), ta->ml, ta->kn, en->hi/ml/kn; Thanglish "epdi iruka" (How are you?), "saptiya", "enna panra", "enga pora", "romba nalla iruku", "naan office ku poren", "enna panra now", "romba good ah iruku". Also: stop the Python service and confirm the "temporarily unavailable" message; translate twice and confirm the second is served from cache; edit a message and confirm the old translation is gone; regression-check chat, calls, notifications.

## 7. Phase 7 limits (intentional / honest)

- Thanglish transliteration is a best-effort phonetic rule set plus a spelling list; unusual spellings can come out wrong, and translation quality follows.
- Distilled 200M/320M checkpoints are used by default for speed; set the `INDICTRANS2_MODEL_*` variables for larger ones.
- Pinned versions in `requirements.txt` may need adjusting for your Python/CUDA setup.
- CPU inference is slow on the first request after startup; raise `TRANSLATION_SERVICE_TIMEOUT_MS` if needed.

---

# Phase 8 — Voice + Video Calls

> **Not run here.** Maven Central and the npm registry are blocked in the build sandbox, so nothing could be compiled against Spring/MySQL, no browser ran, and **WebRTC was never exercised**. What was verified: a syntax-only `javac` and `tsc` pass, the pure call state machine and the call-record labels (run with Node). Test a real call between two browsers before relying on it.

## 1. Project structure (new / changed)

```
backend/src/main/java/com/vtchat/
  entity/      CallType, CallStatus, CallSession (new)   MessageType (+VOICE_CALL, VIDEO_CALL)   Message (+call_id, call_status)
  repository/  CallSessionRepository (new)
  dto/call/    CallOffer, CallAnswer, IceCandidate, CallAction, CallParty, CallEvent, CallResponse, IceServersResponse
  service/     CallService (signaling + state machine + sweeper), CallRegistry (in-memory active calls),
               CallSessionStore (DB, short transactions), SignalingSessions (disconnect grace), IceServerService
  websocket/   CallEventPublisher (private /user/queue/call only)
  controller/  CallWebSocketController (/app/call.*), CallController (REST)
  security/    StompAuthChannelInterceptor (allow-list: /app/call.* and /user/queue/call)
  config/      AppProperties.Call + application.yml app.call.*
frontend/src/
  components/call/  IncomingCall, OutgoingCall, VoiceCall, VideoCall, CallControls, CallStatus, LocalVideo, RemoteVideo, CallOverlay
  components/chat/  CallMessage (call record in the chat), ChatPanel (📞 🎥 header buttons), MessageActionMenu (call records: Select/Delete only)
  context/CallContext.jsx   hooks/useWebRTC.js   hooks/useCall.js   services/callService.js
  utils/  callState.js (pure state machine)  calls.js  callErrors.js  iceServers.js  ringtone.js
```
`CallRequest` from the spec is covered by `CallOffer` (the request that starts a call).

## 2. WebRTC architecture
Audio/video flow **browser to browser**; the server never sees media. Each browser creates one `RTCPeerConnection` (`useWebRTC`), adds its microphone (and camera) tracks, and exchanges an SDP offer/answer plus trickled ICE candidates through the server. STUN finds each side's public address; TURN relays only when a direct path is impossible. MediaStreams live in refs and are attached to `<video>`/`<audio>` elements through registered refs, never in React state. A negotiated data channel (`vt-state`, id 0) only tells the peer "I muted / turned my camera off". Remote candidates that arrive before the connection or remote description exists are queued and applied afterwards.

## 3. Signaling architecture
STOMP over the existing authenticated socket. The browser publishes to `/app/call.*`; the server relays to the other participant's private queue `/user/queue/call` (`convertAndSendToUser`, keyed by user id). There is no topic broadcast. The caller is always the authenticated user; clients never send a caller id.

## 4. Signaling events
| Browser sends | Meaning | Server pushes to |
|---|---|---|
| `/app/call.offer` `{callId, conversationId, callType, sdp}` | start a call | receiver: `CALL_OFFER`; caller: `CALL_RINGING` (or `CALL_BUSY`) |
| `/app/call.answer` `{callId, sdp}` | accept | caller: `CALL_ANSWER`; receiver's other tabs: `CALL_HANDLED` |
| `/app/call.ice` `{callId, candidate}` | ICE candidate | the other side: `CALL_ICE` |
| `/app/call.connected` `{callId}` | my peer connection is up (starts talk time) | — |
| `/app/call.reject` `{callId}` | decline | caller: `CALL_REJECT` |
| `/app/call.cancel` `{callId}` | caller hangs up while ringing | receiver: `CALL_CANCEL` |
| `/app/call.end` `{callId, reason?}` | hang up (`reason: "FAILED"` = broken connection) | other side: `CALL_END` / `CALL_FAILED` |

The server itself also pushes `CALL_MISSED` (ring timeout, both sides) and `CALL_FAILED` (the connection never came up), and ends calls whose user stayed disconnected past the grace period. Failures go to `/user/queue/errors` (`ALREADY_IN_CALL`, `CALL_NOT_FOUND`, `CALL_NOT_RINGING`, `INVALID_CALL`, ...).

## 5. Call flow in brief
1. A taps 📞/🎥 → the browser asks for microphone/camera, creates the offer, publishes `call.offer`.
2. The server checks A belongs to the conversation, finds B (the other member), registers the call (busy checks), saves a `RINGING` row, sends `CALL_OFFER` to B and `CALL_RINGING` to A.
3. B sees the incoming screen (ringtone). Accept → B asks for devices, applies the offer, publishes `call.answer`. Decline → `call.reject`. A may `call.cancel`.
4. A applies the answer; both sides trickle ICE through the server until the peer connection reaches `connected`. Each side reports `call.connected`; the **timer starts at CONNECTED**.
5. Either side ends with `call.end`. The server stores status and duration and writes one call record message ("Voice call · 2:31", "Missed video call", ...) that appears live in the chat.

State machine (browser): `IDLE → CALLING → RINGING → ACCEPTED → CONNECTING → CONNECTED → ENDED`, with `REJECTED`, `MISSED`, `FAILED`, `CANCELLED`, `BUSY` as other endings.

## 6. Database changes (Hibernate `ddl-auto: update` creates/updates them)
- New table **`call_sessions`**: `id, call_id (unique), conversation_id, caller_id, receiver_id, call_type, status, started_at, answered_at, connected_at, ended_at, duration, created_at`; FKs to `users` and `conversations`; indexes on `call_id`, `conversation_id`, `caller_id`, `receiver_id`, `created_at`. Statuses: RINGING, CONNECTED, COMPLETED, MISSED, REJECTED, CANCELLED, FAILED (see "Phase 8 continuation" below).
- `messages`: new nullable columns `call_id VARCHAR(36)`, `call_status VARCHAR(16)`; `message_type` also accepts `VOICE_CALL`, `VIDEO_CALL`.
- **Migration for an existing database.** Hibernate 6 can create `message_type` as a native MySQL `ENUM`, which `ddl-auto: update` will not widen. The entity now maps it as `VARCHAR`; convert the existing column once:
```sql
ALTER TABLE messages MODIFY message_type VARCHAR(16) NOT NULL DEFAULT 'TEXT';
```
A fresh database needs nothing. SDP and ICE candidates are never stored.

## 7. REST API (Bearer token)
- `GET /api/conversations/{conversationId}/calls?page=0&size=20` — paged call history of a conversation (members only), newest first.
- `GET /api/calls?page=0&size=20` — all of my calls.
- `GET /api/calls/{callId}` — one call (its two participants only; used to re-check after a reconnect).
(Response shape: see "Phase 8 continuation".)
- `GET /api/calls/ice-servers` — STUN + TURN servers with a short-lived per-user TURN credential.

## 8. Voice and video implementation
Voice: `getUserMedia({audio})` with echo cancellation/noise suppression; `VoiceCall` shows avatar, status, timer and controls. Video: audio + `getUserMedia({video})`; `VideoCall` shows the remote video full screen and my camera as a picture-in-picture (mirrored). The remote voice always plays through one hidden `<audio>` element (the remote `<video>` is muted), so the **speaker** button simply mutes/unmutes the other person on this device. **Switch camera** appears only when the device has more than one camera.

## 9. UI pieces
`IncomingCall` (accept/decline, ringtone, vibration), `OutgoingCall` (cancel, ringback, self-preview for video), `CallControls` (mute, camera, switch camera, speaker, end), `CallStatus` ("Calling…", "Ringing…", "Connecting…", "Reconnecting…", then mm:ss), `LocalVideo`/`RemoteVideo`. `CallOverlay` is mounted once above the routes, so a call rings on every page and survives navigation.

## 10. STUN / TURN configuration
- Backend (preferred): `STUN_SERVER`, `TURN_SERVER_URLS`, `TURN_SHARED_SECRET`, `TURN_CREDENTIAL_TTL_SECONDS`. coturn needs `use-auth-secret` and `static-auth-secret=<same secret>`. Credentials are `username = "<expiry>:<userId>"`, `credential = base64(HMAC-SHA1(secret, username))`; the secret never reaches the browser.
- Frontend fallback (dev, or if the REST call fails): `VITE_STUN_SERVER`, `VITE_TURN_SERVER`, `VITE_TURN_USERNAME`, `VITE_TURN_CREDENTIAL`. **These are compiled into public JavaScript — never use a production TURN secret there.**
- Without TURN, calls work on most home/office networks (STUN only) but can fail behind symmetric NAT / strict firewalls.

## 11. Browser permissions and errors
Mic/camera permission is requested when a call starts or is accepted. Denied / no device / device busy / insecure context all map to friendly messages (`utils/callErrors.js`); raw browser exceptions are never shown. If the receiver cannot start their devices, the call is declined for the caller too. Browsers require **HTTPS or `localhost`** for `getUserMedia`.

## 12. Cleanup
Every way a call ends goes through one function (`finishLocal`) that stops every camera/microphone track, closes the peer connection and data channel, clears all timers and the ringtone, and drops queued candidates. It runs on hang-up, decline, cancel, remote end, timeout, error, logout, `pagehide`/`beforeunload` (refresh or closing the tab; a best-effort `call.end` is sent) and when the provider unmounts. If the page dies before the end message is sent, the server ends the call after `CALL_DISCONNECT_GRACE_SECONDS`.

## 13. Security and authorization
- Only conversation members can call each other; the receiver is always the other member (a client cannot name one). The caller is the authenticated user.
- After an offer, every signal must come from one of the two participants, and only the right one for that action (receiver answers/declines, caller cancels). Unknown call ids and non-participants get the same "not found" answer.
- Signals go to private user queues only; the STOMP allow-list admits just `/app/call.*` and `/user/queue/call`. SDP size, candidate size and the call id format are validated; ICE and SDP are relayed and never stored.
- A user can be in one call at a time (`ALREADY_IN_CALL`); a second incoming call gets `BUSY`. Call records cannot be replied to, reacted to, forwarded, copied or translated; they can be deleted like any message.

## 14. Dependencies
None added. Backend uses the existing Spring WebSocket/Security/JPA; the frontend uses the browser's native WebRTC.

## 15. Environment variables
Backend: `CALL_RING_TIMEOUT` (30; the old name `CALL_RING_TIMEOUT_SECONDS` still works), `CALL_CONNECT_TIMEOUT_SECONDS` (45), `CALL_DISCONNECT_GRACE_SECONDS` (15), `STUN_SERVER`, `TURN_SERVER_URLS`, `TURN_SHARED_SECRET`, `TURN_CREDENTIAL_TTL_SECONDS` (see `backend/.env.example`). Frontend (optional fallback): `VITE_STUN_SERVER`, `VITE_TURN_SERVER`, `VITE_TURN_USERNAME`, `VITE_TURN_CREDENTIAL` (see `frontend/.env.example`).

## 16. Run
Same as before: backend `mvn spring-boot:run`, frontend `npm install && npm run dev`. To test, sign in as two users in **two different browsers/profiles** (each tab holds one call), open the same conversation and press 📞 or 🎥. Use two devices or HTTPS for real-world testing; browsers allow mic/camera on `localhost` only.

## 17. Phase 8 limits (intentional / honest)
- The active-call registry is in memory: one backend instance only (no Redis, no load balancing). A restart marks unfinished calls `FAILED`.
- No ICE restart / renegotiation: a short network drop usually recovers by itself ("Reconnecting…"); if it does not recover within 15 s the call ends as failed.
- If a user's socket drops, calls end only after the grace period; after the socket returns, the app re-checks the call with `GET /api/calls/{id}`.
- The call is per browser tab; a second tab of the same user rings too, and stops ringing when the first answers or declines.
- "Speaker" is a local mute of the other person, not output-device selection. Camera switching depends on the device.
- Not included: group calls, screen sharing, recording, push notifications, call-back button.


## Phase 8 continuation — Complete Call History

Extends the calling system above; WebRTC, STUN/TURN, signaling, mute/camera/end are unchanged.

### Statuses and direction
`CallStatus`: **RINGING, CONNECTED, COMPLETED, MISSED, REJECTED, CANCELLED, FAILED**; `CallType`: VOICE, VIDEO. Flow: `RINGING → CONNECTED → COMPLETED`; `RINGING → MISSED` (ring timeout), `→ REJECTED` (receiver), `→ CANCELLED` (caller); any live call can end as `FAILED`. The backend decides the final status (sweeper, signaling rules), not the browser. A call that is accepted but whose media has not connected yet is still `RINGING` in the table with `answeredAt` set. A receiver who was busy is stored as `MISSED` for that attempt (the caller still gets the immediate `CALL_BUSY`, "User is currently on another call"; the running call is never touched).

**Direction is not stored.** `CallHistoryItem.from(call, authenticatedUserId)`: the caller sees `OUTGOING` + the receiver as participant; the receiver sees `INCOMING` + the caller. The chat record uses the same rule (the sender of a call record is the caller). A caller therefore never sees their own call as an incoming missed call, and a call the caller cancelled is `CANCELLED`, never "missed", for the caller.

### Tables
The existing `call_sessions` table already holds `call_id, conversation_id, caller_id, receiver_id, call_type, status, started_at, answered_at, connected_at, ended_at, duration, created_at`; no second table was added. History therefore survives refresh, logout/login and restarts (only unfinished calls are marked FAILED at start-up, because a restart kills the media).

**Migration for a database created by the earlier Phase 8 build** (old status names):
```sql
UPDATE call_sessions SET status = 'COMPLETED' WHERE status = 'ENDED';
UPDATE call_sessions SET status = 'RINGING'   WHERE status = 'ACCEPTED';
UPDATE call_sessions SET status = 'MISSED'    WHERE status = 'BUSY';
UPDATE messages SET call_status = 'COMPLETED' WHERE call_status = 'ENDED';
```
Fresh databases need nothing.

### API (Bearer token; the user always comes from the token)
- `GET /api/conversations/{conversationId}/calls?page=0&size=20` — members only (403/404 otherwise), only that conversation, newest first. `size` is limited to 1–50.
- `GET /api/calls?page=0&size=20` — everything the user took part in (powers the Calls page).
- `GET /api/calls/{callId}` — one call, participants only.

```json
{ "success": true, "data": { "items": [ {
    "callId": "…", "conversationId": 7, "callType": "VIDEO", "direction": "INCOMING", "status": "COMPLETED",
    "participant": { "id": 2, "username": "john", "profilePictureUrl": null },
    "startedAt": "2026-10-05T17:00:00Z", "answeredAt": "2026-10-05T17:00:08Z", "endedAt": "2026-10-05T17:05:42Z", "duration": 334 } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false } }
```
A missed call has `answeredAt: null` and `duration: 0`. `duration` is talk time from the moment the WebRTC connection came up.

### UI
- **Incoming** (global, any page): "Incoming Voice/Video Call — John is calling…" with **[Reject] [Accept]**. **Outgoing**: "Calling John…" with **[Cancel]**. The receiver's screen closes immediately on cancel; the caller sees "Call rejected" on reject.
- **Calls page** (`/calls`, new "Calls" nav item): all calls with type icon, person, `Incoming · Missed`, `Today, 5:30 PM`, talk time, **Call back / Video call**, "Load more".
- **Chat → clock button**: the same list for just this conversation (uses the per-conversation endpoint).
- **In the chat timeline**: call records are drawn as a call card (`📞 Voice call / Outgoing · 2:31`, `Missed video call / Incoming · Missed`) with a **Call back** / **Video call** button on calls that were not completed. They cannot be replied to, reacted to, forwarded, copied or translated.
- **Call back** calls the same `startCall` function as the header buttons (no second calling path).

### Failed calls and cleanup
Any signaling/WebRTC failure calls one function that stops local and remote media, closes the peer connection, clears every timer and the ringtone, drops queued candidates and resets the call state, and tells the server (`call.end` with reason FAILED) so the history row becomes `FAILED`. The STOMP call listeners are application-wide (needed so a call can ring on any page), so they stay registered while signed in and are removed on logout.

### Ring timeout
`CALL_RING_TIMEOUT=30` (seconds; `CALL_RING_TIMEOUT_SECONDS` also accepted). The backend sweeper marks the call `MISSED` and notifies both sides.


---

# Phase 8.5 — Presence + Last Seen + Typing Indicator

> **Not run here.** Maven Central and npm are blocked in the build sandbox: nothing was compiled against Spring/MySQL and no browser or second user was available. Verified: syntax-only `javac`/`tsc`; the in-memory connection registry (multi-tab logic) was compiled and run on its own; the last-seen formatter and the frontend presence store were run with Node. Do the two-user test at the end before relying on it.

## 1. Project structure (new / changed)
```
backend/src/main/java/com/vtchat/
  entity/      UserPresence (new)   User (+show_online_status, show_last_seen)
  repository/  UserPresenceRepository (new)   ConversationRepository (+findPeerIds)
  dto/presence/ PresenceResponse, PresenceEvent, TypingRequest, TypingEvent, PrivacyRequest
  service/     PresenceRegistry (live sessions, memory), PresenceService, TypingService, MessageActionService (clears typing on send)
  websocket/   PresenceEventPublisher (private queues only)
  controller/  PresenceController (REST), PresenceWebSocketController (/app/chat.typing), UserController (+PUT /api/users/me/privacy)
  security/    StompAuthChannelInterceptor (allow-list)     config/ AppProperties.Presence + application.yml
frontend/src/
  services/    websocketService (+presence/typing queues, onReconnect)   presenceService.js
  context/     PresenceContext.jsx (external store + provider)
  hooks/       usePresence.js   useTypingIndicator.js
  components/  PresenceStatus.jsx  PresenceDot.jsx  chat/TypingIndicator.jsx  settings/PrivacySettings.jsx
  utils/       presence.js (formatLastSeen, describePresence)
  changed:     ChatPanel (header status, typing), ConversationList (avatar dot), UserPreviewModal, Settings page, App.jsx
```

## 2. How presence works
- **One WebSocket** is reused (no extra connections). `PresenceService` listens to Spring's `SessionConnectedEvent` / `SessionDisconnectEvent`; `PresenceRegistry` keeps `userId -> set of session ids` in memory.
- **Online** = at least one authenticated session. A second tab/device just adds a session. **Offline** = the last session is gone for `PRESENCE_OFFLINE_GRACE_SECONDS` (default 5). The grace period means a page refresh or the automatic reconnect after a token refresh never flashes "offline" or writes anything.
- **Unexpected disconnects**: the STOMP heartbeat (10 s both ways, server and client) lets the broker close dead sessions (closed browser, sleep, network change); that raises the same disconnect event. Nothing depends on the browser saying goodbye.
- **Last seen**: when the user becomes offline the server writes `lastSeenAt = now` (server clock) to table `user_presence` (one row per user, updated only on that transition). MySQL is never touched by heartbeats or connects. ONLINE/OFFLINE is live in-memory state (a stored "ONLINE" would be false after a crash). After a server restart everyone is offline until they reconnect, and users who were online at that moment keep their previous `lastSeenAt`.

## 3. Presence API (Bearer token)
- `GET /api/users/{userId}/presence` → `{ "userId": 15, "status": "ONLINE", "lastSeenAt": null }` or `{ "status": "OFFLINE", "lastSeenAt": "2026-10-05T20:30:15Z" }`. Only for people who share a conversation with you (otherwise 404, nothing leaks).
- `GET /api/presence` → presence of your conversation partners only (fills the chat list). There is no global "who is online" list.
- `PUT /api/users/me/privacy` `{ "showOnlineStatus": true, "showLastSeen": true }` → updated user. `/api/users/me` and login responses now include both flags.
- There is **no REST call to set online/offline**; it only comes from real authenticated sockets.

## 4. Privacy (enforced on the server)
Two simple switches (Settings → Privacy), both default ON:
| Setting | OFF means |
|---|---|
| Show online status | others get `status: "HIDDEN"`, and **no presence events are pushed** for this user |
| Show last seen | `lastSeenAt` is `null` in API answers and events |
The browser never receives hidden data, so hiding in React is not what protects it. When a user changes a switch, their connected partners get the new filtered state immediately. Users always see their own presence unfiltered. (With online status hidden but last seen shown, partners see the last recorded "last seen" time, which only changes when it is reloaded; with both hidden they see nothing.) Typing indicators are not affected by these switches because typing is something the user is actively doing in a shared conversation.

## 5. Real-time delivery
Private user queues only: `/user/queue/presence` → `{ "type": "PRESENCE_ONLINE" | "PRESENCE_OFFLINE" | "PRESENCE_HIDDEN", "userId", "status", "lastSeenAt" }`, sent to partners (people sharing a conversation) who are currently connected. The STOMP allow-list admits only these two new queues and `/app/chat.typing`; clients still cannot publish to topics. After every reconnect the browser reloads `GET /api/presence`, so missed events cannot leave stale state.

## 6. Typing indicator
- Browser → `/app/chat.typing` `{ "conversationId": 10, "typing": true|false }` (one event with a boolean). The server takes the typer from the authenticated session, checks the conversation exists and the user is a member (403/404 otherwise; such signals are dropped quietly), and sends `{ conversationId, userId, typing }` to the **other member's** `/user/queue/typing`.
- **Never persisted**: typing lives only in memory (no table, no message).
- **Strategy** (`useTypingIndicator`): `typing=true` is sent once when the draft becomes non-empty, then at most one refresh every 3 s while the user keeps typing (never per keystroke); `typing=false` is sent after `VITE_TYPING_IDLE_MS` (default 2500, clamped 1500–3000) without typing, when the draft is emptied, and just before a message is sent.
- **Cleanup**: the indicator disappears when the sender stops, sends a message (the server also clears it when it saves a text/media/voice message), leaves the chat or changes conversation, hides/closes the tab, or loses the socket; the receiver also clears it when its own socket drops, and expires it after 6 s without a refresh. The server expires entries after `TYPING_TTL_SECONDS` (7) and clears them when the user goes offline.

## 7. UI
Chat header: "● Online" or "Last seen 5 minutes ago / today at 8:30 PM / yesterday at 9:15 PM / Oct 3 at 7:20 PM" (shown in the viewer's own timezone; "Connecting…/Reconnecting…" replaces it while your own socket is down). "Arun is typing…" with three softly bouncing dots appears above the message box (no motion for reduced-motion users). Chat list: a small green dot on the avatar when online. Search preview: the same status line when you already share a conversation. `utils/presence.js` holds the reusable formatter.

## 8. Configuration and migration
Backend env: `PRESENCE_OFFLINE_GRACE_SECONDS` (5), `TYPING_TTL_SECONDS` (7). Frontend env: `VITE_TYPING_IDLE_MS` (optional). No new dependencies and no Redis.
Hibernate (`ddl-auto: update`) creates `user_presence` and adds `users.show_online_status` / `users.show_last_seen` (existing users default to ON). If your MySQL version rejects the column definition, run:
```sql
ALTER TABLE users ADD COLUMN show_online_status TINYINT(1) NOT NULL DEFAULT 1,
                  ADD COLUMN show_last_seen TINYINT(1) NOT NULL DEFAULT 1;
```

## 9. Existing features
Messaging, media, translation, calls and call history are untouched, with these small hooks: the three message-send paths also clear typing; the chat header status line moved from the connection label to presence (the connection label still appears when your socket is not connected). Presence is informational only: a call can still be refused (`BUSY`, already in a call) while someone shows as online.

## 10. Test with two users (two different browsers/profiles)
1. **Online**: A logs in → B (chat open) sees "● Online" without refreshing.
2. **Offline**: A closes the tab → within ~5 s B sees "Last seen just now", later "5 minutes ago".
3. **Multiple connections**: A opens two browsers; closing one keeps A online; closing the second makes A offline after the grace period. (Also try an unplugged network/laptop sleep: offline appears after the heartbeat timeout, roughly 20–30 s.)
4. **Typing**: A types → B sees "A is typing…"; A stops → it disappears after ~2.5 s; A sends → it disappears at once and the message appears.
5. **Privacy**: A turns off Show online status / Show last seen → B (after reload and live) no longer sees "Online" / the time.
6. **Reconnect**: disconnect A's network, reconnect → A becomes online again; B's view corrects itself.

## 11. Phase 8.5 limits (intentional)
In-memory presence on one server instance (Redis/distributed presence is Phase 10); no "Everyone / contacts / nobody" privacy levels; one-to-one only (no group typing); no push or notification features.

---

# Phase 8.7 — PDF Download State + Open PDF in New Tab

> **Not compiled or run in the build sandbox** (Maven Central and npm are blocked there). Only syntax checks and pure-logic tests of the icon rules were executed. Please build and try it locally.

## 1. What changed
- New table `media_downloads(id, message_id, user_id, downloaded_at)` with unique `(message_id, user_id)`. Hibernate (`ddl-auto: update`) creates it; no manual migration. Only the **receiver's** downloads are stored; there is no global flag on `messages`.
- `GET /api/media/{messageId}/view` — serves PDFs inline (`Content-Type: application/pdf`, not `attachment`). Does **not** record a download. Non-PDF files: `415 NOT_VIEWABLE`.
- `GET /api/media/{messageId}/download` — for FILE messages: the sender gets `403 DOWNLOAD_NOT_ALLOWED`; the receiver gets the file as an attachment and the download is recorded (server time, idempotent) once the request is validated and the file is being served. Failed requests record nothing.
- `MessageResponse` gains `downloadableByCurrentUser` and `downloadedByCurrentUser` (FILE messages only). History/REST responses are built per viewer, so they are accurate after refresh and logout/login. WebSocket broadcast payloads are shared by both users, so these are `null` there; the UI then falls back to "sender → no icon, receiver → icon".
- `/api/media/{id}/access` also returns `viewUrl`; the signed `?mt=` link filter accepts `/view`.

## 2. Frontend
`FileMessage.jsx` now shows a **View** action for PDFs (new tab, chat tab untouched) and the download icon only for the receiver until their first successful download. Download uses an authenticated blob request, so success/failure is real: on success the icon disappears instantly (`utils/downloadState.js`), on failure a SweetAlert2 error is shown and the icon stays. The message menu hides "Download" for the sender's own files.

## 3. Security
Membership, existence, deleted/hidden and receiver checks are server-side; files are resolved only from the stored path of the message (no client-supplied paths or filenames); changing the messageId to someone else's file gives 404/403; MIME comes from the stored, inspected type. The PDF `/view` response omits `Content-Security-Policy: sandbox` because it breaks browser PDF viewers; it keeps `nosniff` and private caching.

## 4. Test with two users
1. A sends a PDF: A sees name/size and **View**, no download icon (also after refresh).
2. B sees View + download icon. B clicks View: opens a new tab, icon remains.
3. B clicks download: file saves, icon vanishes at once.
4. Refresh / logout / login as B: icon stays hidden. A second PDF still shows its icon.
5. A calls `/api/media/{id}/download` directly: 403. A third user: 404.
6. Stop the backend and click download as B: error dialog, icon stays.

## 5. Limits
- The download is recorded when the server starts serving; an aborted transfer cannot be detected, so a rare mismatch is possible (state is stored though the client saw a failure; it corrects on reload).
- Live (WebSocket) messages have null per-user flags until the history is next loaded.
- No Redis, rate limiting, notifications or other later-phase features were added.

---

# Phase 8.8 — Message Delivery + Read Receipts

> **Not compiled or run in the build sandbox** (Maven Central and npm are blocked there). Only syntax checks and a Node test of the pure tick rules were executed. Please build and test locally.

## 1. Ticks
| State | Meaning | UI |
|---|---|---|
| `SENT` | stored, receiver has no open connection | one tick |
| `DELIVERED` | receiver is online (Phase 8.5 connection registry) | two grey ticks |
| `READ` | receiver's client reported the message as visible | two blue ticks |

Only the sender sees ticks (`MessageStatus.jsx`, the single place that draws them). Online ≠ read: being online only gives two grey ticks.

## 2. Backend
- `messages.delivery_status` (`SENT|DELIVERED|READ`) — one value per message, enough for one-to-one chat. New table `message_read_receipts(message_id, user_id, read_at)` with unique `(message_id, user_id)`. Hibernate creates both. Existing rows get `READ` so old history shows no misleading single ticks.
- **On send:** status is `DELIVERED` if the receiver is connected (via `PresenceRegistry`), else `SENT`. Works for text, image, video, file, voice and forwards.
- **On receiver connect:** `PresenceService` calls `DeliveryService.deliverPendingTo`, which moves everything waiting for them to `DELIVERED` and sends `MESSAGE_DELIVERED` to the senders in real time (also after a quick page-refresh reconnect).
- **Read:** `/app/chat.read {conversationId, lastReadMessageId}` (REST fallback: `PUT /api/conversations/{id}/read`, same service). One watermark marks every earlier incoming message `READ`, inserts receipts with `INSERT IGNORE`, and broadcasts `MESSAGE_READ {userId, upToMessageId}` on `/topic/chat/{id}`.
- **Security:** reader = authenticated user; conversation must exist and include the reader (404/403); the message must belong to that conversation (403, nothing created). Only messages sent by the *other* participant ever change.
- **No downgrade:** every UPDATE is conditioned (`<> READ`, `= SENT`), so `READ → DELIVERED` cannot happen; duplicate read events change nothing and publish nothing.
- `MessageResponse.status` (always "SENT" before) is now `deliveryStatus`, accurate in history after refresh, logout/login and backend restart.

## 3. Frontend
- `MessageList` watches unread incoming messages with `IntersectionObserver` (≥50% visible, tab in front) and reports only the highest visible id; messages scrolled out of view or in a hidden tab stay unread until seen. Both users in the same chat → blue ticks automatically.
- `utils/receipts.js` + `messageEvents.js` apply `MESSAGE_DELIVERED/READ`; history merges never lower a status.

## 4. Test with two users
1. B offline, A sends: one tick. 2. B logs in: A's tick becomes two grey, no refresh. 3. B online in another chat: still grey. 4. B opens the chat: blue. 5. Both in the same chat: A's new message goes blue on its own. 6. B scrolls up, A sends: grey until B scrolls down. 7. A sends 4 messages: one `chat.read` request, all blue. 8–9. Refresh / logout-login: blue stays. 10. Check image/video/PDF/voice. 11. Send `chat.read` with a message id from another conversation: 403, no receipt. 12. Reconnect/resend races: never goes back to grey.

## 5. Limits
- A message saved in the instant the receiver connects can stay `SENT` until their next connection (tiny race; no queue added).
- Delivery uses the real connection, so ticks reveal "online" even if the user hid their online status in privacy settings.
- A watermark may be the reader's own message id; it only ever affects incoming messages.
- The conversation list preview shows no ticks. No Redis, notifications or other later-phase features.

---

# Phase 9 — Notifications + Chat Management

> **Not compiled or run in the build sandbox** (Maven Central and npm are blocked there). Only syntax checks and Node tests of the pure notification / chat-list rules were executed. Please build and test locally. All new tables are created by Hibernate (`ddl-auto: update`): `notifications`, `notification_settings`, `conversation_participant_state`.

## 1. Notifications (backend)
- `Notification` (recipient, type `NEW_MESSAGE | MISSED_CALL | INCOMING_CALL | SYSTEM`, title, message, conversation, message ref, call ref, `is_read`, `created_at`). Unique keys `(recipient, type, message)` and `(recipient, type, call)` prevent duplicates; indexes on recipient / read / created / conversation.
- **When a message is saved** the server (never the browser) decides: no notification for the sender; none if the receiver's *Messages* switch is off, the chat is muted, or the receiver has that very chat open and in front (`/app/chat.active`). With *Message preview* off the stored text is "You have a new message".
- **Calls** (Phase 8 hooks, best-effort, the ringing UI is untouched): a ringing call stores an `INCOMING_CALL` notification (if enabled); when it ends that one is closed, and a `MISSED` call (incl. receiver busy) stores `MISSED_CALL` ("Missed voice/video call – From X") linked to the call row. Notification failures never break messaging or calls.
- **Real-time:** saved first, then pushed to `/user/queue/notifications` (that user only). Events: `NOTIFICATION_CREATED`, `NOTIFICATIONS_UPDATED`.
- **API (authenticated user only; someone else's id = 404):** `GET /api/notifications?page&size`, `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`, `PUT /api/notifications/read-all`, `DELETE /api/notifications/{id}`.
- **Settings:** one row per user (unique `user_id`, created ON): `GET/PUT /api/users/me/notification-settings`.

## 2. Chat management (backend)
- `conversation_participant_state` per (conversation, user): `unread_count`, `last_read_message_id`, `muted`, `pinned`, `archived`, `deleted`, `cleared_at`. Nothing is global, so A's pin/mute/archive/unread never affects B.
- **Unread:** incremented atomically when a message arrives for you (not for the sender, not for finished calls, not while you view that chat; missed calls count). Reset by `PUT /api/conversations/{id}/read` (no body = whole chat; or `{lastReadMessageId}`), which is the same endpoint/`/app/chat.read` event as Phase 8.8 ticks: it now also resets your unread count, marks that chat's notifications read and returns your state.
- `PUT /pin`, `/mute` (body `{"enabled": false}` undoes), `PUT /archive`, `/unarchive`, `DELETE /api/conversations/{id}/messages` (clear chat), `DELETE /api/conversations/{id}` (delete conversation).
- **Mute** only silences notifications; messages still arrive, are stored and still count as unread. **Archive** keeps a chat archived when new messages arrive (unread grows, notifications follow your settings).
- **Clear / delete** are per user: they hide the messages for you (same mechanism as "delete for me"); the other person keeps everything. A deleted conversation reappears when a new message arrives or you start the chat again.
- `GET /api/conversations` returns your flags and is **ordered by the server**: pinned, then normal, then archived; newest activity first. Chat-state changes are pushed to your own tabs on `/user/queue/messages` (`CONVERSATION_STATE`).

## 3. Frontend
- Notification bell with unread badge in the header, dropdown panel and `/notifications` page (loading / error / empty states, mark read, mark all read, delete, click → chat; call notifications open the chat with its call history for call back).
- Settings → **Notifications** switches. Browser permission is requested only when you turn *Browser notifications* ON (and never again after "denied"); pop-ups show only while the tab is in the background.
- Chats list: unread badge and bold unread rows, mute / pin / archive indicators, **Chats / Archived** tabs, a context menu (⋮ or right-click) showing only valid actions, SweetAlert2 confirmations for clear/delete, live reordering without refresh.

## 4. Test with two users
1. A messages B: B gets a notification, bell +1, chat badge +1. B opens the chat: badge 0, notification read. 2. B has the chat open: A's message creates no unread/notification. 3. A calls B, B does not answer: *Missed call* notification, click → chat + call history. A calls, B rejects: no missed notification. 4. B mutes: messages still arrive and count, no notification. 5. B pins / archives / unarchives: A's list is unchanged. 6. B clears: B's history is empty, A's intact. 7. B deletes: gone from B's list; a new message from A brings it back. 8. Turn *Messages* off: no more message notifications. 9. Preview off: notifications say "You have a new message". 10. Call `PUT /api/notifications/{id}/read` with another user's id: 404.

## 5. Limits
- "Chat open" is reported by the browser; if it is hidden or disconnected the message counts as unread.
- An archived-chat badge is shown only in the Archived tab. No Redis, push (FCM), rate limiting or later-phase features.
- A message saved while its receiver's open-chat report is in flight may count as unread once; the next read corrects it.

---

# Phase 10 — Redis + Rate Limiting + Scalability

> **Not compiled or run in the build sandbox** (Maven Central and the npm registry are blocked there, and there is no Redis server). I could only run `javac` as a static check with the libraries missing, filtering for mistakes in project code. Nothing was exercised against a real Redis, MySQL or browser, and the Lua scripts were never executed. Please build, run two instances against one Redis and test with section 8. Report any error.

## 1. What changed in one picture

```text
React ── load balancer ──► Spring Boot instance 1 ┐
                           Spring Boot instance 2 ├─► MySQL  (source of truth, unchanged)
                           Spring Boot instance 3 ┘
                                      │
                                    Redis  = cache + temporary shared state + Pub/Sub (never the source of truth)
```

- **MySQL** still holds users, conversations, messages, reactions, media metadata, translations, call history, notifications, settings and chat state. Redis never holds the only copy of anything.
- **Redis** holds: caches (profile, search, conversation metadata, notification badge), shared WebSocket sessions and "chat is open" state, the owner of each running call, rate-limit counters, and Pub/Sub channels.

## 2. Run it

1. Redis, either yours or `docker compose up -d redis` (the file in the project root; Redis is bound to 127.0.0.1 and takes `REDIS_PASSWORD` from the environment).
2. Backend `.env` (see `backend/.env.example`):
   ```
   REDIS_HOST=localhost  REDIS_PORT=6379  REDIS_PASSWORD=  REDIS_DATABASE=0
   WEBSOCKET_INSTANCE_ID=instance-1          # unique per instance
   ```
3. A second instance on the same machine: same MySQL and Redis, a different port and instance id, e.g. `SERVER_PORT=9091 WEBSOCKET_INSTANCE_ID=instance-2 mvn spring-boot:run`. Point two browsers at different instances (e.g. two frontends with different `VITE_API_URL`, or a load balancer).
4. `GET /api/health` shows `{"status":"UP","mysql":"UP","redis":"UP","websocket":"available","instance":"instance-1"}`. Redis down gives `DEGRADED` (the app keeps working), MySQL down gives HTTP 503.
5. `REDIS_ENABLED=false` runs a single instance without Redis (everything falls back to local behaviour). Production: set a Redis password, keep Redis on a private network and never expose it to the internet.

New settings are in `application.yml` under `spring.data.redis`, `vtchat` and `rate-limit`; every value has an environment variable.

## 3. Redis infrastructure (`com.vtchat.redis`, `config/RedisConfig`)

- One pooled Lettuce connection factory (commons-pool2), short timeouts and `REJECT_COMMANDS` while disconnected, so a Redis outage fails fast instead of hanging requests. Values are plain strings / JSON written with the application's `ObjectMapper` (no Java serialization).
- `RedisKeyService` is the only place keys and channels are named. `RedisService` is a failure-tolerant wrapper: every call returns "empty/false" when Redis is down and logs it (throttled, no values).
- Keys: `cache:user:{id}`, `cache:search:{userId}:{hash}`, `cache:conversation:{id}`, `unread:user:{id}:notifications`, `ws:user:{id}`, `ws:active:{userId}:{conversationId}`, `call:owner:{callId}`, `call:user:{userId}`, `rate-limit:{bucket}:{subject}`.

## 4. Caching and invalidation

| Cache | TTL | Invalidated when |
|---|---|---|
| `cache:user:{id}` — the same `UserResponse` the API already returns to that user (no password hash, OTP data or tokens exist in it) | `REDIS_CACHE_TTL_SECONDS` (300) | profile name / picture / privacy changes (after the DB commit) |
| `cache:search:{userId}:{hash}` — user-search results | 30 s | TTL only (kept short on purpose) |
| `cache:conversation:{id}` — participant ids + last update | 300 s | a message bumps the conversation (after commit) |
| `unread:user:{id}:notifications` — the bell badge count | 60 s | +1 atomically (Lua, only if cached) on a new notification; deleted on read / delete / clear |

- A miss, an unreadable value or a Redis outage reads MySQL. Invalidation runs **after the transaction commits** so a reader cannot re-fill the cache with the old row.
- **Authorization never uses the cache** for anything that reads or changes messages: membership is verified in MySQL. The conversation cache is used only to route the typing indicator.
- **Per-conversation unread counts stay in MySQL** (`unread_count = unread_count + 1`, an atomic SQL update, already correct with many instances). The chat list needs that row anyway, so a Redis copy would only add a second value to keep consistent; I did not cache it on purpose. The shared "chat is open" state (below) is what keeps unread counts correct across instances.

## 5. Real-time across instances (Pub/Sub)

- Every outgoing STOMP frame (messages, receipts and chat state, notifications, call signaling, presence, typing) now goes through `RealtimeEventBus`: it **delivers locally at once**, then **publishes the same frame on Redis** (`vtchat:messages`, `vtchat:read-state`, `vtchat:notifications`, `vtchat:calls`, `vtchat:presence`; names configurable). Other instances deliver it to **their** connected users; the origin ignores its own echo, so nothing is delivered twice. If Redis is down, users on the same instance still get everything.
- The message is **saved once in MySQL before** any event is published; Pub/Sub only distributes. Events are not stored.
- Envelope (`DistributedEvent`): kind, origin instance, target (user queue or `/topic/chat/{id}`), destination and the frame body. For messages this is the same JSON the browser would receive (the spec's example shows only ids; I kept the full frame because each viewer's message is assembled per user and re-reading it on every instance would add database work). Pub/Sub never persists it, and Redis must be private and password protected.
- The subscriber **validates every event** (size, shape, channel/kind match and a destination allow-list: only the known user queues and `/topic/chat/{number}`), so a malformed message cannot be routed anywhere else.
- **Multiple devices:** `ws:user:{id}` is a sorted set of `instance:session` members (score = expiry time). A user can have many entries on many instances; each frame goes to all of their sessions. Each instance refreshes its entries every 30 s (TTL 90 s), so a crashed instance's sessions expire on their own; nothing relies on a disconnect event. `PresenceRegistry` (online/offline, delivered ticks) and `ActiveChatRegistry` ("chat is open", which suppresses unread counts and notifications) use this shared state, with local memory first and a local-only fallback when Redis is down.
- **Calls** keep signaling only (media never touches the server or Redis). The instance that receives the offer **owns** the call; `call:user:{id}` / `call:owner:{callId}` (atomic claim, 60 s TTL refreshed by the owner) make "already in a call" work across instances. If another instance receives an answer / ICE / reject / cancel / end / connected, it forwards the action with the **authenticated user id** over `vtchat:calls`; the owner runs it through the same checks (participant, state) as a local action. A user who disconnects from every instance for the grace period gets their call ended. Orphans (owner crashed) are marked failed by a once-a-minute sweep.

## 6. Rate limiting (`com.vtchat.ratelimit`)

Fixed windows counted in Redis (one atomic Lua call), shared by all instances; every limit and window is an environment variable (`*_RATE_LIMIT`, `*_RATE_WINDOW_SECONDS`, `RATE_LIMIT_ENABLED`; 0 turns a bucket off).

| Bucket | Applies to | Key | Default |
|---|---|---|---|
| auth | login, register, forgot-password, reset-password | IP + endpoint | 10 / 15 min |
| otp | verify-register/login/reset-otp, resend-otp | IP + endpoint | 20 / 15 min |
| search | `GET /api/users/search` | user | 30 / min |
| message | STOMP `/app/chat.send`, forward | user | 60 / min |
| media | image / video / file / voice upload | user | 20 / min |
| translation | `POST /api/messages/{id}/translate` | user | 20 / min |
| call-signal | STOMP offer / answer / reject / cancel / end / connected | user | 60 / min |
| call-ice | STOMP ICE candidates (many per call) | user | 600 / min |
| notification | `/api/notifications/**` | user | 120 / min |
| chat-action | react / edit / delete / read / active, REST edit / reactions / delete | user | 240 / min |
| typing | typing frames | user | 240 / min |

- HTTP: a filter right after JWT authentication answers **429** with `Retry-After` and `{"success":false,"message":"Too many requests. Please try again later.","code":"RATE_LIMITED","retryAfter":45,"retryAfterSeconds":45}`. STOMP: the frame is dropped and the sender gets `{status:429, code:"RATE_LIMITED", retryAfterSeconds}` on their private error queue; the connection stays open.
- The existing **OTP rules stay in MySQL**: 5 wrong attempts per OTP and the 60 s resend cooldown (shared by all instances through the database). The per-IP limits above are an extra layer.
- Behind a load balancer set `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true`: the LAST `X-Forwarded-For` entry (appended by your balancer) is used as the client IP; leave it off otherwise, because the header is client-controlled.
- Upload validation (size, type, extension, filename, membership) from Phase 6 is unchanged; rate limiting only supplements it.

## 7. When Redis is unavailable

| Area | Behaviour |
|---|---|
| Caches | read MySQL (a warning is logged once per 30 s) |
| Rate limiting | **not skipped**: a per-instance in-memory counter with the same limit takes over (protection becomes per instance instead of global) |
| Pub/Sub | delivery still works for users on the same instance; others miss events until Redis returns (the app already reloads state on reconnect). Publish failures are logged |
| Sessions, active chat, calls | local memory only (single-instance behaviour) |
| `/api/health` | `DEGRADED`, MySQL stays authoritative |

Logged (never passwords, OTPs, tokens, Redis password or message text): Redis configured / unavailable / restored, Pub/Sub publish and processing errors, rate limit exceeded (bucket and wait time only), WebSocket routing at debug level.

## 8. Frontend

Only what is needed: Axios handles **429** globally (toast with the wait time, no automatic retry, and further calls to that same endpoint are held back until the wait is over; login/OTP screens still show the server's message themselves), the chat shows the same message for a rate-limited STOMP send and gives the typed text back, and a new `CALL_UNAVAILABLE` call error is mapped. No UI redesign.

## 9. Test with two instances

1. Start two instances (section 2), log in as A on instance 1 and B on instance 2, open the same chat: messages, edits, reactions, delete, ticks (sent / delivered / read) and typing appear live on both sides.
2. Notifications: B in another page gets a notification and the bell badge from A's message; B opens the chat: unread 0 on both instances; with the chat open on instance 2, A's message creates no unread / notification.
3. Open B in two browsers / tabs on different instances: both receive each frame once. Close one: B stays online.
4. Call from A (instance 1) to B (instance 2): ringing, answer, ICE, end and busy handling work in both directions; a second call to B while in a call gets "busy". Stop instance 1 during a call: after a minute the call is marked failed.
5. Edit your profile name on one instance: the other shows it after at most a moment (cache invalidated).
6. Rate limits: more than 10 wrong logins in 15 minutes from one IP returns 429 on **either** instance; sending more than 60 messages in a minute shows the toast; search more than 30 times per minute.
7. Stop Redis: `/api/health` says `DEGRADED`, chatting on one instance, login and search keep working, logs show the warnings; start Redis again: logs say it was restored.
8. `redis-cli monitor` while chatting: keys have no tokens or passwords.

## 10. Limits (honest)

- Not compiled or run here (see the note at the top); Lua scripts and the call hand-off between instances are the parts most worth testing first.
- Pub/Sub is at-most-once: an instance that is disconnected from Redis misses events published meanwhile.
- Fixed windows allow up to twice the limit around a window boundary.
- While Redis is down, rate limits are per instance.
- If an instance crashes, its users' "went offline" transition and last-seen stamp are not written (their sessions expire from Redis within about 90 s), and presence grace timers and typing timers are per instance.
- Calls are owned by one instance; if it dies the call ends for good (it cannot be moved to another instance) and is marked failed by the orphan sweep within about two minutes.
- Search results are cached for 30 s, so a brand-new or renamed user may appear in someone's results slightly late.
- While adding Phase 10 I found and fixed an older gap: `CallSessionStore` called `CallSessionRepository.findByCallIdWithUsers`, which did not exist in the repository, so calls could not compile or run. It is now defined. Because no phase could ever be compiled in the sandbox, other problems of that kind may still exist; the first local `mvn compile` will show them.
- Not included (later phases): Python AI / LLM, RAG, group chat, push notifications (FCM), a new auth system.


# Phase 11 — Python AI + LLM Assistant

> **Not compiled or run end to end in the build sandbox** (Maven Central, PyPI and the npm registry are blocked there, so no Spring Boot, FastAPI or Vite run was possible). What WAS executed: the Python context / error-mapping / settings / mock-provider logic (10 unit tests, `ai-service/tests`), the frontend production bundle step (esbuild resolved every import and compiled all JSX), and Node tests of the Markdown parser (incl. `javascript:` links, raw HTML, half-streamed code fences) and of the browser-side streaming client (SSE split across chunks, mid-stream error, cut stream, 429, 401 refresh-and-retry, Stop, network failure) against a mocked `fetch`. The Java is checked only with `javac` (library/Lombok errors aside). **FastAPI, the Spring code and the browser flow are hand-reviewed only: run them and report any error.**

## 1. Architecture

```text
React (/ai)  ->  Spring Boot /api/ai/*  ->  Python AI service (ai-service/, :8002)  ->  LLM provider
                   JWT auth, validation,        stateless, holds the LLM key,
                   rate limit, MySQL            model, system prompt
Translation stays separate:  Spring Boot -> translation-service (:8001) -> IndicTrans2   (not touched)
```

* React only ever calls Spring Boot. `LLM_API_KEY`, `LLM_BASE_URL` and `AI_SERVICE_API_KEY` exist only in `ai-service/.env` and `backend/.env`.
* Spring Boot authenticates to Python with `X-Internal-Api-Key`. **The user's JWT is never forwarded** to Python or the LLM, and neither are emails, usernames or passwords.
* Python is stateless (restart it any time). Conversations live in MySQL: tables `ai_conversations` and `ai_messages` (`user_id`/`conversation_id` indexed), with no link to the private-chat tables, so AI messages never appear in `/chats` and private chats never reach the AI.
* **The AI only sees** the text typed into the AI chat and earlier messages of the same AI conversation. Nothing from private chats, files, voice, calls, profile or auth is read or sent.

## 2. Run it

1. `ai-service/`: see `ai-service/README.md` (Python 3.11+, venv, `pip install -r requirements.txt`, `cp .env.example .env`, `python -m app`). It starts with `LLM_PROVIDER=mock` (no key needed), so you can try the whole flow first. For a real model set `LLM_PROVIDER`, `LLM_MODEL`, `LLM_API_KEY` (+ `LLM_BASE_URL` if needed) in `ai-service/.env` and restart only that service. Providers: `openai`, `anthropic`, `groq`, `openrouter`, `gemini`, `ollama`, `openai-compatible`, `mock`.
2. Put the same random value in `AI_SERVICE_API_KEY` of `ai-service/.env` and `backend/.env` (`openssl rand -base64 48`).
3. Backend `.env` (see `backend/.env.example`): `AI_SERVICE_URL`, `AI_SERVICE_API_KEY`, `AI_SERVICE_TIMEOUT_MS`, `AI_RATE_LIMIT`, `AI_RATE_LIMIT_WINDOW_SECONDS`, `AI_MAX_MESSAGE_LENGTH`, `AI_MAX_CONTEXT_MESSAGES`. Tables are created by `ddl-auto: update`.
4. Frontend: nothing to configure (optional `VITE_AI_MAX_MESSAGE_LENGTH`, a UI hint only). Open **AI** in the top navigation.

## 3. Spring Boot API (Bearer token; `{success, message, data}` envelope)

| Endpoint | |
|---|---|
| `POST /api/ai/chat` `{message}` | one-shot answer, nothing stored, no context. Rate limited. |
| `POST /api/ai/conversations` `{title?}` | create (title defaults to "New chat") |
| `GET /api/ai/conversations` | the caller's chats, newest activity first |
| `GET /api/ai/conversations/{id}` | one chat |
| `DELETE /api/ai/conversations/{id}` | **hard delete** of the chat and its messages (owner only; the data belongs to the user, so nothing is kept) |
| `GET /api/ai/conversations/{id}/messages?before=&limit=` | history, chronological (limit <= 200, default 100) |
| `POST /api/ai/conversations/{id}/messages` `{message}` | save question -> LLM -> save answer; returns both. Rate limited. |
| `POST /api/ai/conversations/{id}/messages/stream` | same, answer streamed as Server-Sent Events (`start`, `delta`, `done`, `error`). Rate limited. |

A conversation that is not yours is `404 AI_CONVERSATION_NOT_FOUND` (ownership is part of every query, so ids cannot be probed).

## 4. Message flow

validate (length, not blank) -> one answer at a time per chat (409 `AI_GENERATION_IN_PROGRESS`; Redis `SET NX` with a TTL, local fallback) -> **read the last `AI_MAX_CONTEXT_MESSAGES` messages, then save the user message** -> Python (system prompt + context limits) -> LLM -> save the assistant message. No database transaction is open while the model works.

* **Context**: Spring selects the newest N messages of that conversation in order; Python additionally caps total characters (`AI_MAX_CONTEXT_CHARS`), starts with a user turn and merges consecutive same-role messages (what a failed generation leaves). A new chat has no earlier context.
* **System prompt**: configured in the Python service only (`AI_SYSTEM_PROMPT` / `_FILE`). The request model accepts only `user` / `assistant` history roles, so no client can inject a system message.
* **Titles**: a still-untitled chat is named from the first message by a deterministic rule (no extra LLM call).
* **Failure**: the user message stays saved, **no assistant message is created**, a clean error is returned. **Stop** (browser disconnect): what was already generated is saved as the answer (it is real output the user saw); nothing if empty.

## 5. Streaming and Stop

Python streams SSE; Spring reads it with `HttpURLConnection` and re-emits SSE to the browser on the request thread (no WebSocket). React uses `fetch` + `ReadableStream` (EventSource cannot send the `Authorization` header), renders Markdown progressively and shows "AI is thinking…" then "AI is typing…". **Stop** aborts the fetch -> Spring's next write fails -> Spring closes the upstream connection -> Python's generator is closed -> the provider HTTP request is closed. A failure *before* the first token (bad key, provider down, timeout) is an ordinary HTTP error, because Python waits for the first chunk before answering. Browsers without streaming support fall back to the non-streamed endpoint.

## 6. Errors (all user-facing text is fixed; keys, URLs, provider bodies and stack traces are never returned)

| Situation | HTTP | `code` | Message |
|---|---|---|---|
| Python down / provider down / bad provider key / provider error | 503 | `AI_UNAVAILABLE` | AI service is temporarily unavailable. Please try again later. |
| LLM or service timeout | 504 | `AI_TIMEOUT` | The AI took too long to respond. Please try again. |
| Provider rate limit | 429 | `AI_BUSY` (+`retryAfterSeconds`) | The AI is busy right now. Please try again shortly. |
| Context too large | 413 | `AI_CONTEXT_TOO_LARGE` | This conversation is too long for the AI. Please start a new chat. |
| Message too long / empty | 400 | `AI_MESSAGE_TOO_LONG` / `AI_INVALID_REQUEST` | |
| Our per-user limit | 429 | `RATE_LIMITED` | Too many requests. Please try again later. |

Provider failures never crash Spring Boot and do not touch private messaging, calls, translation, notifications or authentication. A wrong `AI_SERVICE_API_KEY` / `LLM_API_KEY` is logged loudly (server side only).

## 7. Cost protection

Per-user rate limit (bucket `ai`, Phase 10 Redis counters, per-instance fallback when Redis is down) on the three LLM endpoints; `AI_MAX_MESSAGE_LENGTH`; `AI_MAX_CONTEXT_MESSAGES` + `AI_MAX_CONTEXT_CHARS`; `LLM_MAX_OUTPUT_TOKENS`; one answer at a time per chat; **no automatic retries anywhere** (the JDK is told not to replay POSTs, connections are `Connection: close`); `AI_MAX_CONVERSATIONS_PER_USER`; timeouts everywhere (`AI_SERVICE_TIMEOUT_MS`, `LLM_TIMEOUT_MS`, `AI_STREAM_MAX_MS`). The frontend also stops sending for the `Retry-After` time and ignores a second send while one is running, but none of that is a control: the server enforces everything.

## 8. Request id and logging

`X-Request-Id` (generated if absent, accepted only as 8-64 chars of `[A-Za-z0-9_-]`) is put in the Spring log pattern (`[req=...]`), forwarded to Python (logged as `requestId=...`) and returned in the response header. Logs contain ids, counts, durations and error codes only: never prompts, answers, keys, JWTs, passwords or OTPs (the `httpx` logger is silenced).

## 9. Frontend (`/ai`, nav item "AI")

`pages/AIChat.jsx`, `components/ai/*`, `hooks/useAIChat.js`, `services/aiService.js`, `utils/markdown.js`. List with New chat / open / delete (confirmation), empty / loading / error states, mobile list-or-chat layout, suggestion chips. "New chat" does not create anything until the first message is sent. Markdown is parsed into data and rendered as React elements (no `innerHTML`): HTML in an answer is shown as text, only `http(s)`/`mailto` links are links (`rel="noopener noreferrer nofollow"`). Code blocks have a language label and a **Copy** button ("Copied"); there is no syntax colouring.

## 10. Test checklist (spec section 45/46)

1. `LLM_PROVIDER=mock`: ask "What is Java inheritance?" -> streamed answer. Ask a follow-up: the mock says how many earlier questions it can see.
2. New chat -> follow-up shows 0 earlier questions. Refresh the page -> history is restored from MySQL.
3. Delete a chat -> gone from the list (and `ai_messages` rows gone). Private chats unaffected.
4. Log out -> `/api/ai/*` returns 401.
5. Send more than `AI_RATE_LIMIT` requests in a window -> 429, input shows the countdown.
6. Stop `ai-service` -> "AI service is temporarily unavailable..."; chats, calls, translation, notifications keep working. Start a message with `[[fail:timeout]]`, `[[fail:unavailable]]`, `[[fail:busy]]`, `[[fail:context]]` (mock) to see each error. Press Stop mid-answer -> request ends, partial answer is kept after reload.
7. Two users: user B calling `GET/DELETE /api/ai/conversations/{A's id}` or `/messages` gets 404. Inspect network/`dist`: no LLM or service key anywhere.

## 11. Limits (honest)

* Not compiled/run here (see the note at the top).
* Spring notices that the browser left only when it next writes, so Stop takes effect at the next token; while waiting for the *first* token nothing can be cancelled. A streamed answer holds one servlet thread for its duration.
* Context is limited by message count and characters, not model tokens; a provider may still refuse a prompt (`AI_CONTEXT_TOO_LARGE`).
* The "one answer at a time" lock expires after `AI_STREAM_MAX_MS + AI_SERVICE_TIMEOUT_MS + 5 s` if an instance dies mid-answer.
* Spring's and Python's timeouts default to the same 30 s, so either may fire first; both give a clean message. Set the Spring value a little higher if you want Python's error to win.
* Requests that fail still count against the per-user rate limit.
* No syntax highlighting, no message editing/regenerate, no AI access to private chats, files or documents: that and RAG/embeddings belong to Phase 12.

---

# Phase 12 — Embeddings + Vector Database + RAG ("My Knowledge")

> **Not compiled or run in the build environment** (no Maven, PyPI or npm registry access, no Qdrant). What *was* executed: the
> Python unit tests (`python -m unittest discover tests` in `ai-service/`: 33 pass, no network), an esbuild bundle of the
> React page, a node check of the upload validation helpers, and a `javac` pass on the Spring Boot sources that showed no
> project-level errors (only missing-library errors, because the jars are absent). Expect to fix small compile issues on first build.

## 1. Architecture

```
React ──(Bearer JWT)──> Spring Boot ──(X-Internal-Api-Key)──> Python AI service ──> Qdrant (vectors)
                         MySQL (documents,                        embedding model     LLM (grounded answer)
                         AI chats) + file storage
```
React never talks to Qdrant, the embedding model or the AI service. Spring Boot owns users, documents (MySQL
`knowledge_documents`), the stored file and the status; the AI service owns extraction, chunking, embeddings, Qdrant and the
grounded prompt. Nothing else changed: auth, private chats, calls, read receipts, translation (IndicTrans2), Redis and the
Phase 11 AI chat work as before. Private chats are never ingested.

## 2. Run it

1. `docker compose up -d qdrant` (and `redis` as before). Qdrant listens on `127.0.0.1:6333`.
2. `ai-service/.env`: see the Phase 12 block of `ai-service/.env.example`. Defaults run with `EMBEDDING_PROVIDER=mock` (no
   download; **keyword-like, not semantic**, only to try the plumbing). For real answers use a real multilingual model:
   * `EMBEDDING_PROVIDER=local`, `EMBEDDING_MODEL=intfloat/multilingual-e5-small`, `EMBEDDING_DIMENSION=384`, and
     `pip install -r requirements-embeddings.txt` (downloads the model on first use); or
   * `EMBEDDING_PROVIDER=openai-compatible` with `EMBEDDING_BASE_URL`, `EMBEDDING_API_KEY`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSION`.
   `pip install -r requirements.txt` now also installs `pypdf`.
3. `backend/.env`: the `RAG_*` block of `backend/.env.example` (limits and rate limits). `frontend/.env.example`: two optional hints.
4. Start the services as before. Open **AI** -> **Knowledge** tab -> **Upload**.

The embedding model is dedicated and configurable; the collection (`QDRANT_COLLECTION`, default `vtchat_documents`) is created
with `EMBEDDING_DIMENSION`. If the existing collection has another size the service refuses to use it (`VECTOR_STORE_MISMATCH`)
instead of corrupting it: use a new collection name or re-process everything after changing the model.

## 3. Spring Boot API (Bearer token; `{success, message, data}`)

| Endpoint | |
|---|---|
| `POST /api/ai/knowledge/documents` (multipart `file`) | validate (extension whitelist, magic bytes, size, per-user count, duplicate hash), store, status `UPLOADED`, process in the background |
| `GET /api/ai/knowledge/documents`, `GET .../{id}` | the caller's own documents |
| `DELETE .../{id}` | hides + never searches it at once; removes vectors, file and row |
| `POST .../{id}/reprocess` | new processing version; old vectors are replaced only when the new run succeeds |
| `GET .../{id}/access` -> `/view?mt=`, `/download?mt=` | short-lived links signed for exactly that document (same idea as chat media); DOC/DOCX can only be downloaded |
| `POST /api/ai/conversations/{id}/rag` and `.../rag/stream` | `{message, documentIds?}`; the My Knowledge answer (complete / SSE with the same events as Phase 11) |

The normal `/messages` API is unchanged. Each `AiMessage` now also stores `mode` (NORMAL/RAG) and, for grounded answers, `sources`.
History (Phase 11, `AI_MAX_CONTEXT_MESSAGES`) is combined with the retrieved context.

## 4. Processing and statuses

`UPLOADED` (queued) -> `PROCESSING` -> `READY` or `FAILED` (+ `DELETED` while being removed).
* The slow work runs on a dedicated small thread pool, never in the request and never inside a DB transaction.
* `claim` is an atomic conditional `UPDATE` that also bumps `processingVersion`: two workers can never process one document, and
  only the run holding the current version can finish it (so a stale run, or a delete, can not be overwritten).
* READY is set only after the AI service reports that **all** vectors were stored. On failure the AI service deletes the vectors
  of that run, and Spring Boot stores a fixed, user-safe reason (no OCR: a scanned PDF says so; password-protected PDF, corrupt
  file, too many pages, ...).
* A job that dies with the server is repaired by `KnowledgeRecoveryScheduler` (every minute): stuck `PROCESSING` -> `FAILED`
  (so you can reprocess), never-started `UPLOADED` -> queued again, half-finished deletes completed.
* Only READY documents are ever searched.

## 5. Ownership (the important part)

* Every document query includes the owner id; someone else's id is "not found".
* The `userId` sent to the AI service is the authenticated user, never a client value. Every Qdrant search and delete carries a
  mandatory `userId` filter executed **inside Qdrant**; the optional `documentId` filter only narrows it further.
* Spring Boot sends only the caller's own READY document ids (the browser's selection is intersected with that).
* Sources shown to the user are re-checked against MySQL and the file name is taken from MySQL.
* Qdrant, vector ids, storage paths and credentials are never in any API response, log line or the React bundle.

## 6. Retrieval and answers

Query -> embedding (query prefix for e5 models) -> Qdrant top `RAG_TOP_K` (+ optional `RAG_SIMILARITY_THRESHOLD`) -> at most
`RAG_MAX_CONTEXT_CHUNKS` chunks and `RAG_MAX_CONTEXT_CHARS` characters (budget) -> a dedicated RAG system prompt. Excerpts are
delimited and labelled as untrusted data (a document cannot give the model instructions). If nothing relevant is found, or the model
answers with the `[[NO_ANSWER_IN_DOCUMENTS]]` marker, the user gets *"I couldn't find relevant information about that in your uploaded
documents."* and **no sources**. Sources (`documentId`, `fileName`, `pageNumber`) come only from the chunks actually sent to the model.
English, Tamil and Hindi work through the multilingual embedding model; the translation service is not involved.

## 7. Rate limits (Redis, Phase 10; per user)

`rag-upload` (`RAG_UPLOAD_RATE_LIMIT`, 5/min), `rag-reprocess` (`RAG_REPROCESS_RATE_LIMIT`, 5/min), `rag-query`
(`RAG_QUERY_RATE_LIMIT`, 20/min). Also `RAG_MAX_DOCUMENT_SIZE_MB` (20), `RAG_MAX_DOCUMENTS_PER_USER`, `RAG_MAX_QUERY_LENGTH`.
Chunking and retrieval knobs are in `ai-service/.env`: `RAG_CHUNK_SIZE`, `RAG_CHUNK_OVERLAP`, `RAG_TOP_K`, `RAG_MAX_CONTEXT_CHUNKS`.

## 8. Failure behaviour

If Qdrant or the embedding model is down, uploads stay `FAILED`/queued with a clear reason and a My Knowledge question answers
"Searching your documents is temporarily unavailable. You can still chat in Normal AI mode."; **normal AI chat, private chats and
everything else keep working** (the RAG runtime starts lazily and retries). The question you typed is still saved.

## 9. Frontend

AI page: sidebar tabs **Chats | Knowledge**. Knowledge: upload (button or drag and drop, progress), list with Queued / Processing /
Ready / Failed (auto-refreshing only while something is processing), open, reprocess, delete (confirmation), tick boxes to narrow
the search. The chat has a **Normal AI | My Knowledge** switch (locked while an answer is generated); My Knowledge answers carry a
badge and clickable **Sources** that open the document in a tab (PDFs jump to the cited page) or download it (DOC/DOCX).

## 10. Test checklist

1. `docker compose up -d qdrant`; start `ai-service` and Spring Boot. `GET :8002/health` -> `rag.status` becomes `ready` after the first RAG request (or the start-up warm-up).
2. Upload a PDF / TXT / MD / DOCX: Queued -> Processing -> Ready. Upload the same file again -> "already uploaded". Upload a `.exe` renamed to `.pdf` -> refused (content check).
3. Scanned (image-only) PDF -> Failed with the "no OCR" message; a password-protected PDF -> Failed with its own message.
4. My Knowledge mode: ask something in the document -> answer + Sources; click a source -> opens (page for PDFs). Ask something unrelated -> the "couldn't find" text, no sources.
5. Tamil / Hindi document and question (use a real multilingual embedding model, not `mock`).
6. Reprocess a Ready document -> Processing -> Ready, and answers still work. Delete it -> gone; asking no longer finds it.
7. Two users: B calling `GET/DELETE/reprocess/access` on A's document id gets 404; B's questions never return A's text, even when A's documents contain the answer.
8. Stop Qdrant: My Knowledge reports "temporarily unavailable"; Normal AI works. Start it again: works without restarting.
9. Exceed `RAG_UPLOAD_RATE_LIMIT` / `RAG_QUERY_RATE_LIMIT` -> 429 with the countdown.

## 11. Limits (honest)

* Not compiled or run (see the note at the top). The Python side is unit-tested only for its pure logic (chunking, cleaning, DOCX
  extraction, filters, context selection, stream gate, settings); Qdrant and real embedding models were not exercised.
* `mock` embeddings are not semantic. Real quality depends on the model; **tune `RAG_SIMILARITY_THRESHOLD`** (default 0 for real models).
* e5 models truncate at about 512 tokens; Tamil/Hindi text uses more tokens per character, so consider a smaller `RAG_CHUNK_SIZE`.
* Retrieval uses only the current question: a very short follow-up ("and the second one?") may retrieve poorly.
* No OCR. `.doc` needs `antiword` or `catdoc` installed next to the AI service, otherwise it fails with "save as .docx".
* Scanned/encrypted PDFs are only detected during processing (Failed status), not at upload.
* "No relevant information" also relies on the model following the prompt for the case where chunks were retrieved but do not answer.
* The duplicate-upload check is a service-level check (two simultaneous identical uploads could both pass). A run that is declared
  stuck but later finishes can leave stale vectors of an older version until the next reprocess.
* Stopping a My Knowledge answer keeps the partial text but no sources.
* Deleting a document does not edit past answers that quoted it.
* No hybrid/keyword search, reranking, query rewriting, per-document sharing, OCR, or automatic ingestion of private chats (by design).



---

# Cloudinary media storage (replaces Cloudflare R2)

Chat media, voice messages, documents, profile pictures and My Knowledge files can be stored in **Cloudinary** in production. Local development keeps using the `uploads/` folder and needs no account. Cloudflare R2 support was removed (code, SDK dependencies, variables). Authentication, chat, WebSocket, calls, translation, AI and RAG are unchanged.

## 1. How it works

- `MEDIA_STORAGE_PROVIDER=local` (default): files live under `MEDIA_LOCAL_ROOT` (default `uploads`).
- `MEDIA_STORAGE_PROVIDER=cloudinary`: **new** uploads go to Cloudinary. Anything already stored locally (old `images/…`, `videos/…`, `files/…`, `voice/…` keys, and keys of the earlier `chat-media/…`, `knowledge/…`, `avatars/…` layout) is still read from `MEDIA_LOCAL_ROOT`, so old and new media coexist. Nothing is uploaded or deleted automatically.
- **What is stored in MySQL.** The existing column (`messages.media_key`, `users.profile_picture`, `knowledge_documents.storage_path`) now holds a reference such as `cld:ia:vtchat/chat-media/42/<uuid>.jpg`. It encodes the provider, the Cloudinary **public ID** (the stable identifier), the resource type (`i`mage, `v`ideo, `r`aw), the delivery type (`a`uthenticated or `u`pload) and the format. Message ids, display names, MIME types and sizes stay in their existing columns, so no new columns were needed. Delivery URLs (`secure_url`) are **not** stored: they are derived on demand, because URLs can change. The API secret is never stored. Public IDs are generated by the server (`{folder}/{area}/{conversationId or userId}/{uuid}`); a client can never supply one, and every reference read from the database is re-validated before it reaches the SDK.
- **Resource types.** Images → `image`. Video, voice messages and audio files → `video` (Cloudinary keeps audio under the video resource type). PDFs, Word/Excel/other documents and knowledge files → `raw` (the extension is part of the public ID). Avatars → `image`.
- **Upload order.** Authenticate → validate size, type and file signature → check conversation membership → spool to a temporary file (bounded memory) → upload → verify the size Cloudinary reports → save the reference and metadata and create the message → broadcast over STOMP. If the database write fails after the upload, the asset is deleted; if that cleanup fails, the reference is recorded in the `media_orphans` table (never in a log). No message is created for a failed upload.

## 2. Private delivery (important)

An HTTPS Cloudinary URL is **not** private by itself. This app therefore uploads chat media and documents with delivery type `authenticated`, which has no public URL: every delivery URL needs a signature that only this server (holding the API secret) can create.

- **Images, video, audio, voice:** the existing `/api/media/{id}` endpoints still authenticate and check conversation membership on every request, then answer `302` to a signed Cloudinary CDN URL (`Cache-Control: no-store`, `Referrer-Policy: no-referrer`). Thumbnails are derived by Cloudinary on delivery. **Limitation:** Cloudinary does not time-limit these signatures; a signed URL stays valid for whoever holds it (expiring URLs need token-based authentication, which Cloudinary offers on advanced plans). The URL contains a random UUID and is only issued after the authorization check, but treat it like a bearer link. The frontend asks for a fresh authorized link whenever one fails.
- **PDFs, documents, knowledge files:** never given to the browser as a URL. The backend fetches them server-side through Cloudinary's time-limited (60 s) private download API and streams them, so every view/download is authorized and the receiver-only download rule and download tracking work as before. Filenames (including Tamil/Hindi) come from `Content-Disposition`.
- **Avatars** are public by design (visible to anyone with the link, as before; the link holds a random UUID that changes with every upload). They are uploaded with delivery type `upload`, and `/api/avatars/c/{userId}/{uuid}.jpg` redirects to the CDN. This does not affect private chat media. The profile response still returns `profilePictureUrl` as a relative `/api/avatars/…` URL.
- For documents, in the Cloudinary Console enable **Settings → Security → "Allow delivery of PDF and ZIP files"** if PDFs fail to load (Cloudinary can restrict PDF/ZIP delivery on some accounts). I could not test this against a live account.

## 3. Get Cloudinary credentials

1. Create an account at cloudinary.com. Your **cloud name** is shown in the Console dashboard.
2. Console → **Settings → API Keys** (Programmable Media): copy the API key and API secret. Use a key dedicated to this app so it can be rotated independently.
3. Set the variables on the backend host (not in the repo, not in the frontend): `MEDIA_STORAGE_PROVIDER=cloudinary`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_FOLDER` (default `vtchat`; **do not change it after go-live**, avatar URLs are rebuilt from it). Locally the app also reads `backend/.env` (`spring.config.import: optional:file:.env[.properties]`).
4. Production: `SPRING_PROFILES_ACTIVE=prod`. The app refuses to start if the provider is `local` (unless `MEDIA_ALLOW_LOCAL_IN_PRODUCTION=true`), if Cloudinary settings are missing (the error lists variable **names**, never values), if the provider is the removed `r2`, or if `CLIENT_URL` is not https, the refresh cookie is not Secure or JWT secrets are blank. A reachability check runs at startup and logs a clear hint (it does not block startup).
5. Production media does not depend on a persistent local disk. Keep `MEDIA_LOCAL_ROOT` only while old local files still need to be served.

## 4. Deleting and failures

- Deleting a message for everyone removes the asset only if no other live message still references the same reference (forwards share one asset). Deleting uses the correct public ID, resource type and delivery type; a missing asset counts as deleted; a Cloudinary failure is never reported as success and is recorded in `media_orphans` (provider, coarse category and the reference, kept in the table only). Retry by removing the listed assets in the Cloudinary Console (or rerunning a delete) and then setting `resolved_at`.
- Avatar replacement: validate → upload → compare-and-swap the database reference → delete the previous asset only after that succeeds and only if no other user row references it. On a database failure, or if another request replaced the picture first (409 `AVATAR_CHANGED`), the new asset is removed instead.
- Errors: missing → 404; timeout → 504; Cloudinary unavailable, throttled or credentials/permission problem → 503 with a generic message (no reference, URL or SDK text). Database transactions cannot roll back Cloudinary calls; the cleanup and `media_orphans` reconciliation above are best effort, not atomic.
- Logs carry only an operation name, a coarse category, exception **class names** (never messages) and a short id. `LogSanitizer` redacts URLs, signatures (`s--…--`), `cld:` references, tokens and secrets from any free text. Presigned/signed URLs, the API secret, JWTs and Authorization headers are never logged. **Existing logs from earlier phases were not cleaned.** If they were shipped anywhere they may contain bucket names, object keys or signed URLs: restrict access, delete or rotate them per your retention policy, and rotate any credential you ever logged (R2 token, Cloudinary secret, JWT secrets).

## 5. Fewer provider requests

Generating a signed URL is local and costs no request, and no existence check is made before it. Thumbnails need no check either (a missing original shows as a failed load and the UI falls back). A document view or download makes exactly one Cloudinary request. The only metadata request (Admin API `resource`) is made when a download link is issued for a document, so a missing file fails as an API error instead of a broken download; the Admin API is rate limited, so it is used sparingly.

## 6. Old media and migration (optional, never automatic)

Old messages keep working while their files stay in `MEDIA_LOCAL_ROOT`. To move them to Cloudinary:

1. Back up `uploads/` and the database.
2. `MEDIA_STORAGE_PROVIDER=cloudinary`, `MEDIA_MIGRATION_ENABLED=true`, `MEDIA_MIGRATION_DRY_RUN=true`: start once (logs only).
3. `MEDIA_MIGRATION_DRY_RUN=false`: each distinct local file is uploaded under a deterministic public ID derived from its uuid (a retry overwrites, never duplicates), the size Cloudinary reports is verified, and only then are the messages repointed (ids and metadata unchanged). Results are in `media_migration_items` (`DONE`/`FAILED` with an error class); `MEDIA_MIGRATION_RETRY_FAILED=true` retries; `MEDIA_MIGRATION_LIMIT=N` limits a run. Avatars: the same with `AVATAR_MIGRATION_*` (compare-and-swap, a newer upload is never overwritten).
4. Local originals are **never deleted** by the tools. Verify in the app, keep the backup, then remove them yourself. Set the `*_ENABLED` flags back to `false`.

**Historical Cloudflare R2 assets.** The old R2 code is gone, so this app can no longer read an R2 bucket. Messages whose files were only ever in R2 answer "file not available" until you restore them: download the bucket (for example with `rclone` or the AWS CLI against the account's S3 endpoint) into `MEDIA_LOCAL_ROOT`, keeping the key paths (`chat-media/…`, `knowledge/…`, `avatars/…`), then they are served from local disk and the migration above can move them. If the bucket is gone, those files cannot be recovered. My Knowledge documents uploaded before this change are not migrated by the tools; they stay readable from local disk, or re-upload them.

## 7. Troubleshooting

- *Won't start, lists `CLOUDINARY_*` names*: set those variables, or use `local`.
- *Startup says the Cloudinary check failed (access denied)*: wrong cloud name, key or secret, or the key is disabled.
- *Uploads answer 503*: check the log line `Cloudinary upload failed category=…` (`ACCESS_DENIED` = credentials, `TIMEOUT`/`UNAVAILABLE` = network or Cloudinary, `FAILED` = rejected request, for example a file over your plan's size limit).
- *Images/video load but a PDF does not*: see the PDF/ZIP delivery setting in section 2; confirm the file is within your plan's raw-file size limit.
- *An asset 404s*: it was deleted, or `CLOUDINARY_FOLDER` was changed after upload (avatars), or the reference points to a file that only existed in R2.
- *Delete failed*: look in `media_orphans` for unresolved rows and remove those assets in the Console.

## 8. Cloudinary plan limits and costs (verify before launch)

I consulted Cloudinary's pages and third-party pricing summaries while writing this; some official pages could not be fetched in full, so **check Cloudinary's current pricing and "upload limits" documentation before relying on any number below.**

- The Free plan works on a monthly **credit** allowance (reported as 25 credits). One credit can be spent on about 1,000 transformations, or 1 GB of managed storage, or 1 GB of delivery bandwidth, in any mix. Video, large downloads and many thumbnails consume it quickly. Cloudinary describes soft usage limits on Free (warnings around 90%, no billing for overage), but if usage stays high you are asked to reduce it or upgrade, and a restricted or disabled account can block uploads and delivery. Treat the service as neither unlimited nor permanently free.
- Upload size limits depend on the plan and resource type. A Cloudinary staff answer states the free-plan **video** limit is 100 MB, and the SDK's normal upload handles files up to about 100 MB (larger needs chunked upload). I could not confirm the image and raw-file limits from an official page; they are commonly lower (around 10 MB on Free), so a document above your plan's raw limit will be rejected by Cloudinary even though this app allows `MAX_FILE_SIZE` (default 25 MB). Set `MAX_FILE_SIZE`, `MAX_IMAGE_SIZE`, `MAX_VIDEO_SIZE` to values your plan accepts. This phase did not change any app limit.
- Thumbnails are generated by transformations (counted against the allowance once per distinct derived image). Nothing here enables a paid add-on.
- Expiring/token-based delivery and some security options are advanced-plan features; not used here.

## 9. Not verified here

The code and its unit tests (mocked SDK, real URL signing with fake credentials) were written without Maven, npm or a Cloudinary account available, so nothing was compiled or run, and no live Cloudinary check was performed. Points to confirm on first run: the SDK calls `Cloudinary#privateDownload` and `Transformation#flags`, delivery of `.webm` voice messages as the `video` resource type, and PDF delivery on your account.
