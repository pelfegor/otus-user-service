package ru.otus.notificationservice.api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.common.security.AuthenticatedUser;
import ru.otus.notificationservice.service.NotificationService;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return notifications.findForUser(user.userId()).stream().map(NotificationResponse::from).toList();
    }
}
