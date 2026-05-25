package com.openecosystem.os.flows;

import java.util.List;

public record WorkflowExecutionListResponse(List<WorkflowExecutionSummaryResponse> executions) {}
