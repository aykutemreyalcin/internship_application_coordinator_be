# Internship Application Coordinator — Backend

Java Spring Boot API with Vertex AI (Gemini) agents for internship application processing.

## Documentation

| Document | Description |
|----------|-------------|
| [PROJECT_OVERVIEW.md](./PROJECT_OVERVIEW.md) | Architecture, API contract, local setup (shared with frontend) |
| [BACKEND_TASKS.md](./BACKEND_TASKS.md) | Task plan for **Aykut** |
| [clickup_backend_tasks.csv](./clickup_backend_tasks.csv) | Backend tasks for ClickUp import |

## Quick start (local)

```bash
docker compose up -d   # when added
./mvnw spring-boot:run # http://localhost:8080
```

See `PROJECT_OVERVIEW.md` for GCP / Vertex AI credentials.

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Stable / release-ready |
| `dev` | Active development |

## Team

- **Aykut** — backend + Vertex/Gemini agents
- **Nizamettin** & **Alvin** — frontend ([FE repo](https://github.com/aykutemreyalcin/internship_application_coordinator_fe))
