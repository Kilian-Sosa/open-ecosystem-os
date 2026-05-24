package com.openecosystem.os.flows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.CorrelationContext;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WorkflowService {

  private final AuthenticationContext authenticationContext;
  private final WorkflowDefinitionValidator definitionValidator;
  private final JdbcWorkflowRepository workflowRepository;
  private final JdbcWorkflowExecutionRepository executionRepository;
  private final WorkflowRunner workflowRunner;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;

  public WorkflowService(
      AuthenticationContext authenticationContext,
      WorkflowDefinitionValidator definitionValidator,
      JdbcWorkflowRepository workflowRepository,
      JdbcWorkflowExecutionRepository executionRepository,
      WorkflowRunner workflowRunner,
      TransactionTemplate transactionTemplate,
      ObjectMapper objectMapper) {
    this.authenticationContext = authenticationContext;
    this.definitionValidator = definitionValidator;
    this.workflowRepository = workflowRepository;
    this.executionRepository = executionRepository;
    this.workflowRunner = workflowRunner;
    this.transactionTemplate = transactionTemplate;
    this.objectMapper = objectMapper;
  }

  public WorkflowListResponse listWorkflows() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return new WorkflowListResponse(
        workflowRepository.listByWorkspace(principal.workspaceId()).stream()
            .map(this::toSummaryResponse)
            .toList());
  }

  public WorkflowDetailResponse getWorkflow(String workflowId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    WorkflowWithVersion workflowWithVersion =
        workflowRepository
            .findByIdForWorkspace(workflowId, principal.workspaceId())
            .orElseThrow(() -> notFound("Workflow was not found"));
    return toDetailResponse(workflowWithVersion);
  }

  public CreatedWorkflowResponse createWorkflow(WorkflowSaveRequest request) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    WorkflowDefinition definition = definitionValidator.validate(request.definition());
    WorkflowStatus status = requestStatus(request.status(), WorkflowStatus.DRAFT);
    String name = requiredText(request.name(), "name");
    String description = requiredText(request.description(), "description");
    Instant now = Instant.now();
    String workflowId = Ids.newId("flow");
    String versionId = Ids.newId("wfv");

    transactionTemplate.executeWithoutResult(
        transactionStatus -> {
          workflowRepository.insertWorkflow(
              new Workflow(
                  workflowId,
                  principal.workspaceId(),
                  name,
                  description,
                  status,
                  null,
                  1,
                  principal.actorId(),
                  principal.actorId(),
                  now,
                  now));
          workflowRepository.insertVersion(
              new WorkflowVersion(
                  versionId,
                  workflowId,
                  principal.workspaceId(),
                  1,
                  definition.json(),
                  principal.actorId(),
                  now,
                  publishedAt(status, now)));
          workflowRepository.updateCurrentVersion(
              workflowId, name, description, status, versionId, 1, principal.actorId(), now);
        });

    WorkflowDetailResponse response = getWorkflow(workflowId);
    return new CreatedWorkflowResponse(URI.create("/api/flows/workflows/" + workflowId), response);
  }

  public WorkflowDetailResponse updateWorkflow(String workflowId, WorkflowSaveRequest request) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    WorkflowWithVersion existing =
        workflowRepository
            .findByIdForWorkspace(workflowId, principal.workspaceId())
            .orElseThrow(() -> notFound("Workflow was not found"));
    WorkflowDefinition definition = definitionValidator.validate(request.definition());
    WorkflowStatus status = requestStatus(request.status(), existing.workflow().status());
    String name = requiredText(request.name(), "name");
    String description = requiredText(request.description(), "description");
    int versionNumber = existing.workflow().currentVersionNumber() + 1;
    String versionId = Ids.newId("wfv");
    Instant now = Instant.now();

    transactionTemplate.executeWithoutResult(
        transactionStatus -> {
          workflowRepository.insertVersion(
              new WorkflowVersion(
                  versionId,
                  workflowId,
                  principal.workspaceId(),
                  versionNumber,
                  definition.json(),
                  principal.actorId(),
                  now,
                  publishedAt(status, now)));
          workflowRepository.updateCurrentVersion(
              workflowId,
              name,
              description,
              status,
              versionId,
              versionNumber,
              principal.actorId(),
              now);
        });

    return getWorkflow(workflowId);
  }

  public WorkflowExecutionDetailResponse runWorkflowManually(String workflowId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    WorkflowWithVersion workflowWithVersion =
        workflowRepository
            .findByIdForWorkspace(workflowId, principal.workspaceId())
            .orElseThrow(() -> notFound("Workflow was not found"));
    String runId = Ids.newId("run");
    WorkflowExecution execution =
        workflowRunner.run(
            new WorkflowRunCommand(
                workflowWithVersion,
                WorkflowTriggerType.MANUAL,
                principal.actorId(),
                CorrelationContext.currentOrCreate(),
                null,
                null,
                "flows:" + workflowId + ":manual:" + runId + ":v1",
                null));
    return toExecutionDetailResponse(execution);
  }

  public WorkflowExecutionListResponse listExecutions() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return new WorkflowExecutionListResponse(
        executionRepository.listByWorkspace(principal.workspaceId()).stream()
            .map(this::toExecutionSummaryResponse)
            .toList());
  }

  public WorkflowExecutionDetailResponse getExecution(String executionId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    WorkflowExecution execution =
        executionRepository
            .findByIdForWorkspace(executionId, principal.workspaceId())
            .orElseThrow(() -> notFound("Workflow execution was not found"));
    return toExecutionDetailResponse(execution);
  }

  private WorkflowSummaryResponse toSummaryResponse(WorkflowWithVersion workflowWithVersion) {
    Workflow workflow = workflowWithVersion.workflow();
    WorkflowDefinition definition =
        definitionValidator.validate(workflowWithVersion.version().definition());
    return new WorkflowSummaryResponse(
        workflow.workflowId(),
        workflow.name(),
        workflow.description(),
        workflow.status().value(),
        workflow.currentVersionNumber(),
        definition.trigger().type(),
        definition.trigger().eventType(),
        definition.steps().size(),
        workflow.updatedAt());
  }

  private WorkflowDetailResponse toDetailResponse(WorkflowWithVersion workflowWithVersion) {
    Workflow workflow = workflowWithVersion.workflow();
    WorkflowVersion version = workflowWithVersion.version();
    return new WorkflowDetailResponse(
        workflow.workflowId(),
        workflow.name(),
        workflow.description(),
        workflow.status().value(),
        version.versionId(),
        version.versionNumber(),
        jsonValue(version.definition()),
        workflow.createdAt(),
        workflow.updatedAt());
  }

  private WorkflowExecutionSummaryResponse toExecutionSummaryResponse(WorkflowExecution execution) {
    return new WorkflowExecutionSummaryResponse(
        execution.executionId(),
        execution.workflowId(),
        workflowName(execution),
        execution.workflowVersionNumber(),
        execution.triggerType().value(),
        execution.sourceEventType(),
        execution.sourceEventId(),
        execution.status().value(),
        execution.retryCount(),
        execution.failureReason(),
        execution.correlationId(),
        execution.startedAt(),
        execution.completedAt(),
        execution.failedAt(),
        execution.updatedAt());
  }

  private WorkflowExecutionDetailResponse toExecutionDetailResponse(WorkflowExecution execution) {
    List<WorkflowStepExecutionResponse> steps =
        executionRepository.listSteps(execution.executionId()).stream()
            .map(this::toStepResponse)
            .toList();
    return new WorkflowExecutionDetailResponse(
        execution.executionId(),
        execution.workflowId(),
        workflowName(execution),
        execution.workflowVersionNumber(),
        execution.triggerType().value(),
        execution.sourceEventType(),
        execution.sourceEventId(),
        execution.status().value(),
        execution.retryCount(),
        execution.failureReason(),
        execution.correlationId(),
        execution.startedAt(),
        execution.completedAt(),
        execution.failedAt(),
        execution.updatedAt(),
        steps);
  }

  private WorkflowStepExecutionResponse toStepResponse(WorkflowStepExecution step) {
    return new WorkflowStepExecutionResponse(
        step.stepExecutionId(),
        step.stepKey(),
        step.stepName(),
        step.actionType(),
        step.status().value(),
        step.retryCount(),
        step.failureReason(),
        jsonValue(step.input()),
        jsonValue(step.output()),
        step.startedAt(),
        step.completedAt(),
        step.failedAt(),
        step.updatedAt());
  }

  private String workflowName(WorkflowExecution execution) {
    return workflowRepository
        .findWorkflowByIdForWorkspace(execution.workflowId(), execution.workspaceId())
        .map(Workflow::name)
        .orElse("Unknown workflow");
  }

  private WorkflowStatus requestStatus(String value, WorkflowStatus fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return WorkflowStatus.fromValue(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          ApiErrorCode.VALIDATION_FAILED,
          "Workflow status is not supported",
          Map.of("status", value));
    }
  }

  private String requiredText(String value, String fieldName) {
    if (value != null && value.isBlank()) return value.trim();
    throw new ApiException(
        HttpStatus.BAD_REQUEST,
        ApiErrorCode.VALIDATION_FAILED,
        "Workflow " + fieldName + " is required",
        Map.of("field", fieldName));
  }

  private Instant publishedAt(WorkflowStatus status, Instant now) {
    return status == WorkflowStatus.ACTIVE ? now : null;
  }

  private Object jsonValue(JsonNode jsonNode) {
    try {
      return objectMapper.readValue(jsonNode.toString(), Object.class);
    } catch (JsonProcessingException exception) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ApiErrorCode.INTERNAL_ERROR,
          "Workflow JSON could not be serialized");
    }
  }

  private ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, message);
  }

  public record CreatedWorkflowResponse(URI location, WorkflowDetailResponse workflow) {}
}
