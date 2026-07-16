package mr.gov.finances.sgci.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.AuthService;
import mr.gov.finances.sgci.service.PasswordResetService;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.dto.CheckEmailRequest;
import mr.gov.finances.sgci.web.dto.CheckEmailResponse;
import mr.gov.finances.sgci.web.dto.LoginRequest;
import mr.gov.finances.sgci.web.dto.LoginResponse;
import mr.gov.finances.sgci.web.dto.PasswordResetRequestBody;
import mr.gov.finances.sgci.web.dto.PasswordResetRequestResponse;
import mr.gov.finances.sgci.web.dto.RegisterRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final PermissionService permissionService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/password-reset/check-email")
    public CheckEmailResponse checkEmailForPasswordReset(@Valid @RequestBody CheckEmailRequest request) {
        return passwordResetService.checkEmail(request.getEmail());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetRequestResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequestBody request) {
        return passwordResetService.submitRequest(request.getEmail());
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal AuthenticatedUser user,
            Authentication authentication
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        java.util.List<String> authorities = authentication != null && authentication.getAuthorities() != null
                ? authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList())
                : java.util.List.of();
        java.util.List<String> permissions = new ArrayList<>(permissionService.findPermissionCodesByRole(user.getRole()));
        permissions.sort(String::compareTo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getUserId());
        body.put("username", user.getUsername());
        body.put("role", user.getRole().name());
        body.put("authorities", authorities);
        body.put("permissions", permissions);
        return ResponseEntity.ok(body);
    }

    /** Permissions à jour depuis la base (utile après ajout de droits sans se reconnecter). */
    @GetMapping("/permissions")
    public ResponseEntity<java.util.List<String>> currentPermissions(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        java.util.List<String> permissions = new ArrayList<>(permissionService.findPermissionCodesByRole(user.getRole()));
        permissions.sort(String::compareTo);
        return ResponseEntity.ok(permissions);
    }
}
