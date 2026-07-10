package com.openecosystem.os.identity;

import java.util.Optional;

public interface SeededSessionRepository {

  Optional<SeededSession> findActiveSession(String actorId, String workspaceId);
}
