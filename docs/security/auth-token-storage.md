# Auth Token Storage

The frontend still stores the bearer JWT in `localStorage` because the backend contract currently returns tokens in JSON and authenticates `Authorization: Bearer ...` headers.

Moving to an httpOnly cookie should be done as a backend + frontend contract change, not as a frontend-only swap. The migration needs:

- `Set-Cookie` login/register responses with `HttpOnly`, `Secure`, `SameSite`, and a clear domain/path policy.
- CSRF protection for cookie-authenticated state-changing requests.
- Logout and token revocation behavior that clears the cookie and server-side token state together.
- Frontend request changes from bearer headers to credentialed requests.

Until that contract exists, the localStorage token remains centralized in `frontend/src/services/apiBase.ts` so the later migration has one primary read path.
