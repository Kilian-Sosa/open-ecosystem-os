package com.openecosystem.os.flows;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowStepDefinition(String id, String name, String actionType, JsonNode action) {}
