# SecureVault

SecureVault is a full-stack credential manager built with React and Spring Boot. It provides a dashboard for organizing website, email, banking, social, application, API-key, and secure-note records, together with password utilities, collaboration, security monitoring, and reports.

> **Security notice:** This project is not currently a zero-knowledge vault. Credential passwords and secure notes are encrypted by the backend before database storage, then decrypted by the backend for authenticated API responses. Other fields (such as site name, URL, and username) are not encrypted at rest. The default development configuration also contains secrets that must not be used in production. Do not store real banking or email credentials in a public/untrusted deployment.

## Features

### Credential vault
- Create, view, edit, favorite, and delete vault records.
- Organize and filter records by credential type and category; search and sort the unlocked list.
- Store site names, URLs, account usernames, passwords, categories, notes, and tags.
- Credential types: Website Login, Email Account, Banking, Social Media, Application, API Key, and Secure Note.
- Reveal or copy saved values from the dashboard.
- Existing password and secure-note fields use AES-GCM encryption on the backend before persistence.

### Accounts and authentication
- Register and sign in with username/email and password.
- JWT-based API authentication; account passwords are stored as BCrypt hashes.
- Forgot/reset-password flow. In the current development implementation, the reset link is simulated and printed by the backend rather than delivered by a configured email provider.
- Role-aware administration for users, team members, and administrators.

### Password tools
- Generate passwords with adjustable length and character options.
- Generate passphrases and multiple passwords in bulk.
- Check a password's strength and view password-health information.

### Sharing and teams
- Share credentials with other users and manage/revoke outgoing shares.
- Create team vaults, add or remove members, and view team-vault credentials.
- Sharing and access are handled by the authenticated backend APIs.

### Security monitoring and notifications
- Security dashboard with account/security analytics.
- Review login attempts, device information, risk/anomaly indicators, security alerts, and audit events.
- Resolve alerts and export audit information.
- View notifications, unread counts, and mark notifications as read.

### Administration and reporting
- Admin user directory and role management.
- System metrics and compliance checklist.
- Report previews for security, password health, audit trail, user activity, and threat monitoring.
- Export report data as CSV and an HTML document suitable for printing.

## Technology

### Frontend
- React 19, Vite, React Router, and CSS.
- Node.js 18 or later.

### Backend
- Java 17, Spring Boot 3, Spring Security, Spring Data JPA, and JWT.
- H2 is the default local database; PostgreSQL can be configured for a deployment.
- Maven wrapper is included.

## Project structure

- `frontend/` — React/Vite web application.
- `securevault-backend/` — Spring Boot REST API and persistence layer.

## Run locally

Open two terminals from the project directory.

### Backend

```powershell
cd securevault-backend
.\mvnw.cmd spring-boot:run
```

The backend listens on `http://localhost:8080`. By default, it uses a persistent H2 database under `securevault-backend/data/`.

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL shown in the terminal (normally `http://localhost:5173`), register an account, and sign in.

## Configuration

The application defaults are intended for local development:

- Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_DRIVER`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` to use another database.
- Configure the JWT signing secret and backend encryption key through deployment-managed secrets before deploying; do not use source-controlled development values.
- Set `cors.allowed-origins` to the exact trusted frontend origin(s).
- Use HTTPS, disable development consoles, and review authentication, account recovery, logging, authorization, and database backups before handling real credentials.

The backend H2 console is a development facility and must not be exposed in production. The frontend currently targets the local API URL (`http://localhost:8080`), which must be changed/configured for a deployed environment.

## API areas

- `/api/auth` — registration, login, and password recovery.
- `/api/vault/credentials` and `/api/credentials` — credential CRUD, favorites, search, and filters.
- `/api/vault/share` — sharing and team-vault operations.
- `/api/vault/password` — password generation, passphrases, and health checks.
- `/api/security` — dashboards, alerts, audit logs, login attempts, and analytics.
- `/api/notifications` — notification retrieval and read status.
- `/api/admin` — administrative user and system operations.
- `/api/reports` — report analytics and exports.

## Validation

```powershell
cd frontend
npm run build
npm run lint
```

```powershell
cd securevault-backend
.\mvnw.cmd test
```
