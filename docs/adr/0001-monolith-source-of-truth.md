# ADR 0001: Monolith Is the Source of Truth

## Status

Accepted

## Context

The repository previously contained a monolith and an experimental
`ainote-microservices/` tree. The microservices tree had diverged from the
monolith and was not part of the actual AutoDL deployment path.

## Decision

The backend monolith is the only source of truth for application behavior,
schema changes, deployment, and security fixes. The experimental
`ainote-microservices/` tree has been removed.

## Consequences

All fixes and migrations must target the monolith unless a future ADR replaces
this decision. Microservice-only audit items are out of scope after this
removal.
