package com.openecosystem.os.flows;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.events.JdbcEventOutboxRepository;
import com.openecosystem.os.drive.DriveSourceLifecycle;
import com.openecosystem.os.invoice.InvoiceExtractionPort;
import com.openecosystem.os.invoice.JdbcInvoiceExtractionRepository;
import com.openecosystem.os.knowledge.JdbcKnowledgeItemRepository;
import com.openecosystem.os.media.JdbcOcrResultRepository;
import com.openecosystem.os.media.OcrJobRepository;
import com.openecosystem.os.notifications.JdbcNotificationRepository;
import com.openecosystem.os.search.JdbcSearchDocumentRepository;
import com.openecosystem.os.search.SearchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class WorkflowRunnerTest {
  @Mock DriveSourceLifecycle lifecycle;
  @Mock WorkflowDefinitionValidator definitions;
  @Mock JdbcWorkflowExecutionRepository executions;
  @Mock JdbcNotificationRepository notifications;
  @Mock JdbcKnowledgeItemRepository knowledge;
  @Mock InvoiceExtractionPort extractor;
  @Mock JdbcInvoiceExtractionRepository invoices;
  @Mock OcrJobRepository jobs;
  @Mock JdbcOcrResultRepository results;
  @Mock JdbcSearchDocumentRepository search;
  @Mock JdbcAuditRecordRepository audits;
  @Mock JdbcEventOutboxRepository outbox;
  @Mock SearchProperties properties;
  @Mock ObjectMapper mapper;
  @Mock TransactionTemplate transactions;
  @InjectMocks WorkflowRunner runner;

  @Test
  void inactiveSourceIsRejectedBeforeAnyRichReadOrPersistence() {
    when(transactions.execute(any()))
        .thenAnswer(
            invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0))
                    .doInTransaction(
                        mock(org.springframework.transaction.TransactionStatus.class)));
    var workflow = mock(Workflow.class);
    when(workflow.workspaceId()).thenReturn("wrk_test");
    var command =
        new WorkflowRunCommand(
            new WorkflowWithVersion(workflow, null),
            WorkflowTriggerType.EVENT,
            "usr_test",
            "corr_test",
            "evt_test",
            "OcrCompleted",
            "test:key",
            OcrCompletedWorkflowTriggerServiceTest.event());
    assertThatThrownBy(() -> runner.run(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Workflow source is unavailable");
    verify(lifecycle).lockActiveFile("wrk_test", "file_test");
    verifyNoInteractions(
        definitions,
        executions,
        notifications,
        knowledge,
        extractor,
        invoices,
        jobs,
        results,
        search,
        audits,
        outbox);
  }
}
