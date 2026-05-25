package com.openecosystem.os.notifications;

import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import org.springframework.stereotype.Service;

@Service
public class NotificationQueryService {

  private final AuthenticationContext authenticationContext;
  private final JdbcNotificationRepository notificationRepository;

  public NotificationQueryService(
      AuthenticationContext authenticationContext,
      JdbcNotificationRepository notificationRepository) {
    this.authenticationContext = authenticationContext;
    this.notificationRepository = notificationRepository;
  }

  public NotificationListResponse listNotifications(String correlationId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return new NotificationListResponse(
        notificationRepository.listByWorkspace(principal.workspaceId(), correlationId).stream()
            .map(this::toResponse)
            .toList());
  }

  private NotificationResponse toResponse(NotificationRecord notification) {
    return new NotificationResponse(
        notification.notificationId(),
        notification.title(),
        notification.body(),
        notification.severity(),
        notification.status(),
        notification.sourceType(),
        notification.sourceId(),
        notification.correlationId(),
        notification.createdAt(),
        notification.readAt());
  }
}
