package com.openecosystem.os.demo;

import java.time.Instant;

public record DemoTimelineStepResponse(
    String key, String label, String status, String detail, String href, Instant occurredAt) {}
