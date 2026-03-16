package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.NotificationService;
import mr.gov.finances.sgci.web.dto.NotificationDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NotificationDto> getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        return service.findByUser(user.getUserId());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public long getUnreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        return service.countUnread(user.getUserId());
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    public NotificationDto markAsRead(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        return service.markAsRead(id, user.getUserId());
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void markAllAsRead(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        service.markAllAsRead(user.getUserId());
    }
}
