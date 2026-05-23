package com.openecosystem.os.worker.common.events;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventConsumptionRepository {

  private final JdbcTemplate jdbcTemplate;

  public EventConsumptionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean exists(String consumerName, String idempotencyKey) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from event_consumptions
            where consumer_name = ? and idempotency_key = ?
            """,
            Integer.class,
            consumerName,
            idempotencyKey);
    return count != null && count > 0;
  }

  public boolean save(
      String consumerName, String idempotencyKey, String eventId, Instant consumedAt) {
    try {
      jdbcTemplate.update(
          """
          insert into event_consumptions (
            consumer_name,
            idempotency_key,
            event_id,
            consumed_at
          ) values (?, ?, ?, ?)
          """,
          consumerName,
          idempotencyKey,
          eventId,
          Timestamp.from(consumedAt));
      return true;
    } catch (DuplicateKeyException exception) {
      return false;
    }
  }
}
