package com.openecosystem.os.identity;

public record SessionActorResponse(
    String actorId, String displayName, String email, String avatarInitials) {}
