# ADR 0004 — RabbitMQ first for async jobs/events

## Status

Proposed

## Context

The ecosystem needs queues for OCR, workflow execution, notifications, indexing, retries, and dead-letter handling. RabbitMQ integrates well with Spring Boot and has clear DLQ patterns.

## Decision

Use RabbitMQ for the MVP. Keep the event envelope generic so NATS JetStream or Kafka can be evaluated later.

## Consequences

Positive:

- pragmatic worker queue
- clear DLQ/retry model
- good Spring ecosystem support

Negative:

- not as stream/replay-oriented as Kafka/NATS JetStream
- operational tuning still required
