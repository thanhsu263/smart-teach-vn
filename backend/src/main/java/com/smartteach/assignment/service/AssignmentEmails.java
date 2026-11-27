package com.smartteach.assignment.service;

import com.smartteach.assignment.dto.AnalyticsResponse;
import com.smartteach.assignment.dto.QuestionStatsResponse.QuestionStat;
import com.smartteach.assignment.entity.Assignment;
import com.smartteach.classroom.entity.Classroom;
import com.smartteach.classroom.repository.ClassroomRepository;
import com.smartteach.common.mail.MailProperties;
import com.smartteach.common.mail.MailQueue;
import com.smartteach.user.entity.User;
import com.smartteach.user.repository.UserRepository;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Texts of the mails sent to a teacher about an assignment (students have no email address).
 * Times are stored in UTC and shown in Vietnam time, where the teachers are.
 */
@Component
@RequiredArgsConstructor
class AssignmentEmails {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZONE);
    private static final String FOOTER = """

            --
            Bạn nhận email này vì đang bật thông báo email trong SmartTeach.
            Tắt tại: %s/account
            """;

    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final AnalyticsService analyticsService;
    private final QuestionStatsService questionStatsService;
    private final MailQueue mailQueue;
    private final MailProperties mailProperties;

    /** Results after the assignment ended. Nothing is queued if the teacher turned mails off. */
    void queueSummary(Assignment assignment, int autoSubmitted) {
        Optional<User> owner = optedInOwner(assignment);
        if (owner.isEmpty()) {
            return;
        }
        AnalyticsResponse stats = analyticsService.analytics(assignment.getOwnerId(), assignment.getId());
        StringBuilder text = new StringBuilder("""
                Xin chào %s,

                Bài tập "%s" của lớp %s đã kết thúc lúc %s (giờ Việt Nam).

                - Đã nộp: %d/%d học sinh (%d chưa nộp)
                """.formatted(owner.get().getFullName(), assignment.getTitle(), className(assignment),
                TIME.format(assignment.endedAt()), stats.submittedCount(), stats.studentCount(),
                stats.studentCount() - stats.submittedCount()));
        if (autoSubmitted > 0) {
            text.append("  trong đó %d bài làm dở được tự động nộp khi hết giờ\n".formatted(autoSubmitted));
        }
        if (stats.averageScore() == null) {
            text.append("- Chưa có học sinh nào nộp bài\n");
        } else {
            text.append("- Điểm trung bình: %s/100\n".formatted(oneDecimal(stats.averageScore())));
        }
        hardestQuestion(assignment).ifPresent(q -> {
            text.append("- Câu sai nhiều nhất: câu %d \"%s\", %s%% học sinh làm đúng\n"
                    .formatted(q.position() + 1, shorten(q.prompt()), oneDecimal(q.correctRate())));
            if (q.mostCommonWrongAnswer() != null) {
                text.append("  Đáp án sai hay gặp nhất: \"%s\" (%d học sinh)\n".formatted(
                        shorten(q.mostCommonWrongAnswer().answer()), q.mostCommonWrongAnswer().count()));
            }
        });
        text.append("\nXem chi tiết: ").append(link(assignment)).append('\n');
        text.append(FOOTER.formatted(mailProperties.linkBaseUrl()));
        mailQueue.enqueue(owner.get().getEmail(), "Kết quả bài tập: " + assignment.getTitle(), text.toString());
    }

    /** 24 hours before the deadline, only if someone in the class has not submitted yet. */
    void queueReminder(Assignment assignment) {
        Optional<User> owner = optedInOwner(assignment);
        if (owner.isEmpty()) {
            return;
        }
        AnalyticsResponse stats = analyticsService.analytics(assignment.getOwnerId(), assignment.getId());
        int notSubmitted = stats.studentCount() - stats.submittedCount();
        if (notSubmitted == 0) {
            return;
        }
        String text = """
                Xin chào %s,

                Bài tập "%s" của lớp %s hết hạn lúc %s (giờ Việt Nam).
                Còn %d/%d học sinh chưa nộp: %d chưa bắt đầu, %d đang làm dở.

                Mã bài tập để nhắc học sinh: %s
                Bài đang làm dở sẽ được tự động nộp khi hết hạn.

                Xem tiến độ: %s
                """.formatted(owner.get().getFullName(), assignment.getTitle(), className(assignment),
                TIME.format(assignment.getDueAt()), notSubmitted, stats.studentCount(),
                notSubmitted - stats.inProgressCount(), stats.inProgressCount(), assignment.getPublicCode(),
                link(assignment)) + FOOTER.formatted(mailProperties.linkBaseUrl());
        mailQueue.enqueue(owner.get().getEmail(), "Sắp hết hạn: " + assignment.getTitle(), text);
    }

    private Optional<User> optedInOwner(Assignment assignment) {
        return userRepository.findById(assignment.getOwnerId()).filter(User::isEmailNotifications);
    }

    /** Lowest correct rate among questions that someone answered. */
    private Optional<QuestionStat> hardestQuestion(Assignment assignment) {
        return questionStatsService.questionStats(assignment.getOwnerId(), assignment.getId()).questions().stream()
                .filter(q -> q.correctRate() != null)
                .min(Comparator.comparingDouble(QuestionStat::correctRate));
    }

    private String className(Assignment assignment) {
        return classroomRepository.findById(assignment.getClassId()).map(Classroom::getName).orElse("");
    }

    private String link(Assignment assignment) {
        return mailProperties.linkBaseUrl() + "/assignments/" + assignment.getId();
    }

    private static String oneDecimal(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private static String shorten(String text) {
        String flat = Objects.requireNonNullElse(text, "").replaceAll("\\s+", " ").strip();
        return flat.length() <= 80 ? flat : flat.substring(0, 77) + "...";
    }
}
