package com.edushift.modules.users.controller;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.files.dto.FileObjectResponse;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.exception.FileNotFoundException;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service endpoints for the authenticated user (path prefix {@code /users/me}).
 *
 * <p>Currently exposes avatar upload/delete; this is the home for any
 * {@code /users/me/*} surface that should not be admin-only.</p>
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>User self-service endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>POST   </td><td>/users/me/avatar</td><td>isAuthenticated</td><td>201 + FileObjectResponse</td></tr>
 *   <tr><td>DELETE </td><td>/users/me/avatar</td><td>isAuthenticated</td><td>204 No Content</td></tr>
 * </table>
 *
 * <p>The uploaded file lands in the {@code lms_file_objects} row keyed
 * under {@code module="avatars"} and the {@code users.avatar_url} column
 * is set to the {@code FileObject.publicUuid} as a string. The download
 * URL is resolved via the existing
 * {@code GET /api/v1/files/{publicUuid}/download} endpoint.</p>
 *
 * <p>Multi-tenant safety: the user id is taken from the JWT via
 * {@link CurrentUserProvider#currentUserId()} so a user can never upload
 * an avatar on behalf of another account.</p>
 */
@Slf4j
@RestController
@RequestMapping("/users/me")
@Validated
@RequiredArgsConstructor
@Tag(name = "Users — self-service",
        description = "Endpoints operated by the authenticated user on their own profile")
public class UserSelfController {

    private final CurrentUserProvider currentUserProvider;
    private final FileObjectService fileObjectService;
    private final UserRepository userRepository;

    @PostMapping(
            value = "/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Upload / replace the authenticated user's avatar",
            description = "Accepts an image (image/png, image/jpeg, image/webp) up to "
                    + "5 MB. The bytes land in the active storage provider (LOCAL_FS in "
                    + "dev, FIREBASE in prod) under module='avatars'. The previous avatar, "
                    + "if any, is soft-deleted and its bytes are removed from the provider.")
    public ResponseEntity<ApiResponse<FileObjectResponse>> uploadAvatar(
            @RequestPart("file") MultipartFile file) throws IOException {

        UUID tenantId = requireTenant();
        UUID userId = requireUser();

        // 1. Persist the new file (BE-proxied upload for LOCAL_FS path; the
        //    Firebase provider also accepts this — bytes are streamed by the BE).
        FileObject fileObject = fileObjectService.store(tenantId, "avatars", file);
        log.info("[users/me/avatar] user={} uploaded avatar file_uuid={} size={}B",
                userId, fileObject.getPublicUuid(), fileObject.getSizeBytes());

        // 2. Update users.avatar_url on the authenticated user.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_NOT_FOUND",
                        "Authenticated user no longer exists"));
        String previousAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(fileObject.getPublicUuid().toString());
        userRepository.save(user);

        // 3. Drop the previous avatar (best effort — failures are logged,
        //    not surfaced, because the user already saw the new avatar
        //    successfully uploaded).
        if (previousAvatarUrl != null && !previousAvatarUrl.isBlank()) {
            try {
                UUID previousUuid = UUID.fromString(previousAvatarUrl);
                fileObjectService.delete(previousUuid);
            }
            catch (IllegalArgumentException | FileNotFoundException e) {
                log.warn("[users/me/avatar] previous avatar uuid={} could not be "
                                + "parsed or already gone — leaving as-is",
                        previousAvatarUrl, e);
            }
        }

        FileObjectResponse response = FileObjectResponse.fromEntity(
                fileObject, "/api/v1/files/" + fileObject.getPublicUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @DeleteMapping("/avatar")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Remove the authenticated user's avatar",
            description = "Clears the avatar_url column and removes the file from "
                    + "the active storage provider. Idempotent: 204 even when no "
                    + "avatar was set.")
    public ResponseEntity<Void> deleteAvatar() {
        UUID userId = requireUser();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_NOT_FOUND",
                        "Authenticated user no longer exists"));

        String current = user.getAvatarUrl();
        if (current == null || current.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        user.setAvatarUrl(null);
        userRepository.save(user);
        try {
            UUID currentUuid = UUID.fromString(current);
            fileObjectService.delete(currentUuid);
        }
        catch (IllegalArgumentException | FileNotFoundException e) {
            log.warn("[users/me/avatar] avatar uuid={} could not be parsed or "
                    + "already gone — leaving the column NULL but skipping "
                    + "storage delete", current, e);
        }
        return ResponseEntity.noContent().build();
    }

    private UUID requireTenant() {
        return currentUserProvider.currentTenantId()
                .orElseThrow(() -> new UnauthorizedException(
                        "NO_TENANT",
                        "Authenticated user has no tenant binding"));
    }

    private UUID requireUser() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new UnauthorizedException(
                        "NO_USER",
                        "Authenticated principal is anonymous"));
    }
}