# MIG — TechLab AI Integration

> **Not Applicable — MIG consumes no AI capability (recorded decision).**
>
> Per `_brief.md` §7 and doc 04 (Hub Service Dependencies): `ai.hasAi = false`
> for NeuroAssist AI, and MIG depends on **no** TechLab AI bounded context
> (orchestration / document_composition / knowledge / analytics) regardless.
> It is an infrastructure DDL runner that talks only to PostgreSQL.
>
> This file defines **no Feign client, no port, no consumed AI event**. The
> scaffold must not add `spring-cloud-starter-openfeign` or any `techlab-ai`
> dependency for this service.

`THE SYSTEM SHALL depend on no TechLab AI bounded context.`
`THE SYSTEM SHALL declare no Feign client toward techlab-ai.`
