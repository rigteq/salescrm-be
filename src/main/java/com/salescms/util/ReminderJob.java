package com.salescms.util;
import com.salescms.service.NotificationService;

import com.salescms.entity.TaskItem;
import com.salescms.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodically turns tasks that are due-soon or overdue into in-app notifications
 * for their owner. De-duplicated per task so a given task reminds at most once.
 */
@Component
public class ReminderJob {

    private static final Duration DUE_SOON_WINDOW = Duration.ofHours(24);

    private final TaskRepository tasks;
    private final NotificationService notifications;

    public ReminderJob(TaskRepository tasks, NotificationService notifications) {
        this.tasks = tasks;
        this.notifications = notifications;
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 120_000)
    public void run() {
        Instant now = Instant.now();
        Instant threshold = now.plus(DUE_SOON_WINDOW);
        List<TaskItem> due = tasks.findDueOpenTasks(threshold);
        for (TaskItem task : due) {
            boolean overdue = task.getDueAt().isBefore(now);
            String title = overdue ? "Task overdue" : "Task due soon";
            notifications.notify(
                    task.getTenantId(),
                    task.getOwnerUserId(),
                    overdue ? "TASK_OVERDUE" : "TASK_DUE",
                    title,
                    task.getTitle(),
                    task.getRelatedObjectType(),
                    task.getRelatedObjectId(),
                    (overdue ? "task-overdue-" : "task-due-") + task.getId());
        }
    }
}
