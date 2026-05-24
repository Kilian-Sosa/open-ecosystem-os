package com.openecosystem.os.flows;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowSaveRequest(
    String name, String description, String status, JsonNode definition) {}
