package com.openecosystem.os.flows;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flows")
public class WorkflowController {

  private final WorkflowService workflowService;

  public WorkflowController(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @GetMapping("/workflows")
  public WorkflowListResponse listWorkflows() {
    return workflowService.listWorkflows();
  }

  @GetMapping("/workflows/{workflowId}")
  public WorkflowDetailResponse getWorkflow(@PathVariable String workflowId) {
    return workflowService.getWorkflow(workflowId);
  }

  @PostMapping("/workflows")
  public ResponseEntity<WorkflowDetailResponse> createWorkflow(
      @RequestBody WorkflowSaveRequest request) {
    WorkflowService.CreatedWorkflowResponse response = workflowService.createWorkflow(request);
    return ResponseEntity.created(response.location()).body(response.workflow());
  }

  @PutMapping("/workflows/{workflowId}")
  public WorkflowDetailResponse updateWorkflow(
      @PathVariable String workflowId, @RequestBody WorkflowSaveRequest request) {
    return workflowService.updateWorkflow(workflowId, request);
  }

  @PostMapping("/workflows/{workflowId}/runs")
  public WorkflowExecutionDetailResponse runWorkflow(@PathVariable String workflowId) {
    return workflowService.runWorkflowManually(workflowId);
  }

  @GetMapping("/executions")
  public WorkflowExecutionListResponse listExecutions() {
    return workflowService.listExecutions();
  }

  @GetMapping("/executions/{executionId}")
  public WorkflowExecutionDetailResponse getExecution(@PathVariable String executionId) {
    return workflowService.getExecution(executionId);
  }
}
