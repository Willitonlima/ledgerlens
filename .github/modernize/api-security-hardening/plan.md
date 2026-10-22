# Implementation Plan: API Security and Workspace Isolation

**Project**: LedgerLens

## Technical Framework

- **Language**: Java 25
- **Framework**: Spring Boot 3.5.0
- **Build Tool**: Maven
- **Database**: PostgreSQL 16
- **Key Dependencies**: Spring Web, Spring Data JPA, Flyway, Testcontainers

## Overview

This work hardens the LedgerLens backend API and enforces isolation between
workspaces while preserving the existing application behavior outside the
requested scope. The implementation will:

- Ensure database access and schema support consistently scope data by
  workspace.
- Add controlled workspace deletion and configurable retention cleanup.
- Apply request, upload, CORS, response-header, error-response, logging, and
  actuator protections.
- Provide production environment configuration and integration coverage for
  the specified security and tenancy cases.

Changes are limited to the backend, its database migrations/configuration, and
related tests. Do not alter `.copilot-instructions.md` or unrelated frontend
behavior.

## Implementation Impact Summary

| Area | Intended outcome |
|---|---|
| Workspace data | Required workspace context, isolated queries, safe deletion |
| API protection | Configurable limits, origin policy, headers, safe errors/logs |
| Runtime config | Production datasource, origins, and port from environment |
| Verification | Integration coverage for tenancy, limits, content type, cleanup |

## Validation Approach

Run backend build and existing tests, then execute integration tests covering
absent and invalid workspace headers, cross-workspace isolation, general and
upload rate limits, incorrect upload content type, and scheduled cleanup.
Confirm rate-limited responses include `Retry-After`, production CORS rejects
wildcard origins, and error responses do not expose internal exception details.

## Constraints

- Preserve the existing Spring Boot, JPA, PostgreSQL, and Flyway patterns.
- Include schema changes in versioned database migrations and add appropriate
  tenant-aware indexes/constraints.
- Keep cleanup retention configurable with a 24-hour default.
- Trust forwarded proxy headers only according to explicit proxy configuration.
- Do not broaden the work into unrelated dependency upgrades, UI changes, or
  instruction-file edits.
