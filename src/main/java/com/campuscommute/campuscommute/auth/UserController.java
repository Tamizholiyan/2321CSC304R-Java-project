package com.campuscommute.campuscommute.auth;

import com.campuscommute.campuscommute.auth.dto.UpdateRoleRequest;
import com.campuscommute.campuscommute.auth.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal Long userId) {
        UserProfileResponse profile = userService.getCurrentUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PatchMapping("/me/role")
    public ResponseEntity<UserProfileResponse> updateRole(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        UserProfileResponse profile = userService.updateUserRole(userId, request.role());
        return ResponseEntity.ok(profile);
    }
}
