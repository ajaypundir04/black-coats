# Black-Coat API Specification

Base URL: `/api/v1`

---

## 1. Authentication & Account Lifecycle (`/api/v1/auth`)

### `POST /auth/register`
Creates a new user account with Argon2 password hashing.
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "StrongPassword123!",
    "displayName": "Ajay Pundir",
    "langPref": "en"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d8f1e2...",
    "expiresIn": 900
  }
  ```

### `POST /auth/login`
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "StrongPassword123!"
  }
  ```
- **Response (200 OK):** Access and refresh tokens.

---

## 2. Streaming Legal Chat (`/api/v1/chat`)

### `POST /chat/stream`
Grounded streaming chat over Server-Sent Events (SSE).
- **Headers:** `Authorization: Bearer <JWT>`, `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "conversationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "question": "What is the legal definition and punishment for cheating under BNS?"
  }
  ```
- **SSE Stream Events:**
  - `event: token`
    ```json
    {"text": "Under Section 318 "}
    ```
  - `event: citations`
    ```json
    {
      "citations": [
        {
          "tag": "[S1]",
          "actShortName": "BNS",
          "sectionNo": "318",
          "heading": "Cheating",
          "textSnippet": "Whoever, by deceiving any person, fraudulently...",
          "verified": true
        }
      ],
      "ungrounded": []
    }
    ```
  - `event: done`
    ```json
    {
      "provider": "gemini",
      "latencyMs": 540,
      "language": "en"
    }
    ```
  - `event: refusal` (Triggered when no sources pass similarity threshold)
    ```json
    {
      "message": "I could not find relevant statutory provisions in the corpus. Quota refunded."
    }
    ```

---

## 3. Privacy & DPDP Act 2023 (`/api/v1/me`)

### `GET /me/export`
Exports all personal data, conversations, and feedback associated with the user in standard JSON format.

### `DELETE /me`
Permanently soft-deletes the user profile and revokes all active refresh tokens.
