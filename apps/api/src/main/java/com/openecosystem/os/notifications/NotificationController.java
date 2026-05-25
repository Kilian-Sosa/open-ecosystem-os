package com.openecosystem.os.notifications;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationQueryService notificationQueryService;

  public NotificationController(NotificationQueryService notificationQueryService) {
    this.notificationQueryService = notificationQueryService;
  }

  @GetMapping
  public NotificationListResponse listNotifications(
      @RequestParam(name = "correlationId", required = false) String correlationId) {
    return notificationQueryService.listNotifications(correlationId);
  }
}
