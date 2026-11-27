package com.smartteach.assignment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly, and once shortly after startup: a free-plan server sleeps when idle, so a fixed clock
 * time could be missed for days. Nothing is lost by a late run, because the queries look at state
 * ("ended and not finished yet"), not at what happened since the last run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentDeadlineJob {

    private final AssignmentDeadlineService deadlineService;

    @Scheduled(initialDelayString = "${app.jobs.deadline-initial-delay:2m}",
            fixedDelayString = "${app.jobs.deadline-interval:1h}")
    @SchedulerLock(name = "assignment-deadlines", lockAtMostFor = "PT30M")
    public void run() {
        // One failing assignment (bad data) must not stop the others.
        for (Long id : deadlineService.endedWithWorkLeft()) {
            try {
                deadlineService.finishIfEnded(id);
            } catch (RuntimeException e) {
                log.error("Could not finish assignment {}", id, e);
            }
        }
        for (Long id : deadlineService.dueForReminder()) {
            try {
                deadlineService.remindIfDue(id);
            } catch (RuntimeException e) {
                log.error("Could not queue the reminder of assignment {}", id, e);
            }
        }
    }
}
