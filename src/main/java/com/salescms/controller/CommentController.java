package com.salescms.controller;
import com.salescms.repository.CommentRepository;
import com.salescms.entity.Comment;

import com.salescms.entity.User;
import com.salescms.repository.UserRepository;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.service.NotificationService;
import com.salescms.entity.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private static final Set<String> TYPES = Set.of("ACCOUNT", "CONTACT", "LEAD", "OPPORTUNITY");
    private static final Pattern MENTION = Pattern.compile("@([a-zA-Z][a-zA-Z0-9._-]{1,40})");

    public record CommentRequest(
            @NotBlank String relatedObjectType,
            @NotNull UUID relatedObjectId,
            @NotBlank String body) {
    }

    public record CommentView(UUID id, String body, UUID authorUserId, String authorName,
                              Instant createdAt) {
    }

    private final CommentRepository comments;
    private final UserRepository users;
    private final NotificationService notifications;

    public CommentController(CommentRepository comments, UserRepository users,
                             NotificationService notifications) {
        this.comments = comments;
        this.users = users;
        this.notifications = notifications;
    }

    @GetMapping("/by-record")
    @Transactional(readOnly = true)
    @PreAuthorize("@permissionService.hasPermission('COMMENT_VIEW')")
    public List<CommentView> byRecord(@RequestParam String objectType, @RequestParam UUID objectId) {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, String> names = nameMap(tenantId);
        return comments
                .findByTenantIdAndRelatedObjectTypeAndRelatedObjectIdAndSoftDeletedAtIsNullOrderByCreatedAtAsc(
                        tenantId, objectType, objectId)
                .stream()
                .map(c -> new CommentView(c.getId(), c.getBody(), c.getAuthorUserId(),
                        names.getOrDefault(c.getAuthorUserId(), "Unknown"), c.getCreatedAt()))
                .toList();
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('COMMENT_CREATE')")
    public CommentView create(@Valid @RequestBody CommentRequest request) {
        if (!TYPES.contains(request.relatedObjectType())) {
            throw new BadRequestException("Invalid object type: " + request.relatedObjectType());
        }
        UUID tenantId = TenantContext.requireTenantId();
        UUID authorId = TenantContext.requireUserId();
        Comment comment = comments.save(new Comment(tenantId, request.relatedObjectType(),
                request.relatedObjectId(), request.body(), authorId));

        notifyMentions(tenantId, authorId, request);

        String authorName = users.findById(authorId).map(User::getFullName).orElse("Unknown");
        return new CommentView(comment.getId(), comment.getBody(), authorId, authorName,
                comment.getCreatedAt());
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('COMMENT_DELETE')")
    public void delete(@PathVariable UUID id) {
        Comment comment = comments.findByIdAndTenantIdAndSoftDeletedAtIsNull(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Comment", id));
        comment.softDelete();
        comments.save(comment);
    }

    /** Notifies tenant users whose first name is @mentioned in the comment body. */
    private void notifyMentions(UUID tenantId, UUID authorId, CommentRequest request) {
        Matcher m = MENTION.matcher(request.body());
        Set<String> handles = new java.util.HashSet<>();
        while (m.find()) {
            handles.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        if (handles.isEmpty()) {
            return;
        }
        for (User user : users.findByTenantIdOrderByFirstNameAsc(tenantId)) {
            if (user.getId().equals(authorId)) {
                continue;
            }
            String first = user.getFirstName().toLowerCase(Locale.ROOT);
            if (handles.contains(first)) {
                notifications.notify(tenantId, user.getId(), "MENTION", "You were mentioned",
                        request.body(), request.relatedObjectType(), request.relatedObjectId(),
                        "mention-" + user.getId() + "-" + System.currentTimeMillis());
            }
        }
    }

    private Map<UUID, String> nameMap(UUID tenantId) {
        return users.findByTenantIdOrderByFirstNameAsc(tenantId).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }
}
