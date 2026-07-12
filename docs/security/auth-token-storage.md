# Auth Token Storage

Browser authentication uses an HttpOnly cookie. Login and registration responses do not serialize the JWT, and the frontend never persists it in `localStorage` or another JavaScript-readable store.

The enforced contract is:

- Login and registration set the authentication cookie with `HttpOnly`, `SameSite`, a fixed path, and `Secure` by default in production.
- Cookie-authenticated unsafe requests require the `XSRF-TOKEN` cookie and matching `X-XSRF-TOKEN` header.
- `GET /api/auth/csrf` bootstraps the CSRF token and `GET /api/auth/me` restores the browser session.
- Logout revokes the server-side token and expires the authentication cookie.
- Legacy `token` and `auth-storage` values are deleted during frontend authentication bootstrap.
- Non-browser API clients may still use an explicit `Authorization: Bearer` header; those requests are not subject to browser CSRF because browsers do not attach the header automatically.

The local HTTP development profile may disable the cookie `Secure` flag. This exception must not be used for an Internet-facing deployment.
