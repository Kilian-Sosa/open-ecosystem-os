package com.openecosystem.os.flows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.openecosystem.os.common.events.EventConsumptionRepository;
import com.openecosystem.os.drive.DriveSourceLifecycle;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OcrCompletedWorkflowTriggerServiceTest {
  @Test
  void delayedCompletionIsConsumedWithoutLoadingWorkflowsOrRetainedContent() {
    var lifecycle = mock(DriveSourceLifecycle.class);
    var definitions = mock(WorkflowDefinitionValidator.class);
    var workflows = mock(JdbcWorkflowRepository.class);
    var runner = mock(WorkflowRunner.class);
    var consumed = mock(EventConsumptionRepository.class);
    var service =
        new OcrCompletedWorkflowTriggerService(lifecycle, definitions, workflows, runner, consumed);
    var event = event();
    service.trigger(event);
    verify(lifecycle).lockActiveFile("wrk_test", "file_test");
    verifyNoInteractions(definitions, workflows, runner);
    verify(consumed).save(anyString(), eq(event.idempotencyKey()), eq(event.eventId()), any());
  }

  static OcrCompletedEvent event() {
    return new OcrCompletedEvent(
        "evt_test",
        1,
        Instant.EPOCH,
        "wrk_test",
        "usr_test",
        "corr_test",
        "evt_prior",
        "test:completed",
        "ocr_test",
        "file_test",
        "test",
        1,
        0,
        Instant.EPOCH);
  }
}
