package com.fptu.math_master.service.impl;

import com.fptu.math_master.entity.User;
import com.fptu.math_master.dto.request.CreateLessonRequest;
import com.fptu.math_master.dto.request.ReorderLessonsRequest;
import com.fptu.math_master.dto.request.UpdateLessonRequest;
import com.fptu.math_master.dto.response.LessonResponse;
import com.fptu.math_master.entity.Lesson;
import com.fptu.math_master.constant.PredefinedRole;
import com.fptu.math_master.enums.LessonStatus;
import com.fptu.math_master.exception.AppException;
import com.fptu.math_master.exception.ErrorCode;
import com.fptu.math_master.repository.ChapterRepository;
import com.fptu.math_master.repository.LessonRepository;
import com.fptu.math_master.repository.UserRepository;
import com.fptu.math_master.service.LessonService;
import com.fptu.math_master.util.SecurityUtils;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class LessonServiceImpl implements LessonService {

  LessonRepository lessonRepository;
  ChapterRepository chapterRepository;
  UserRepository userRepository;

  @Override
  @Transactional
  public LessonResponse createLesson(CreateLessonRequest request) {
    UUID currentUserId = SecurityUtils.getCurrentUserId();
    validateAdminRole(currentUserId);

    // Validate chapter exists
    chapterRepository
        .findById(request.getChapterId())
        .filter(c -> c.getDeletedAt() == null)
        .orElseThrow(() -> new AppException(ErrorCode.CHAPTER_NOT_FOUND));

    int orderIndex =
        request.getOrderIndex() != null
            ? request.getOrderIndex()
            : lessonRepository.countByChapterIdAndNotDeleted(request.getChapterId()).intValue() + 1;

    Lesson lesson =
        Lesson.builder()
            .chapterId(request.getChapterId())
            .title(request.getTitle())
            .learningObjectives(request.getLearningObjectives())
            .lessonContent(
                request.getLessonContent() != null ? request.getLessonContent() : "")
            .summary(request.getSummary())
            .orderIndex(orderIndex)
            .durationMinutes(request.getDurationMinutes())
            .difficulty(request.getDifficulty())
            .status(LessonStatus.DRAFT)
            .build();
              lesson.setCreatedBy(currentUserId);

    return toResponse(lessonRepository.save(lesson));
  }

  @Override
  public LessonResponse getLessonById(UUID id) {
    return toResponse(findActiveLesson(id));
  }

  @Override
  public List<LessonResponse> getLessonsByChapterId(UUID chapterId) {
    return searchLessonsByChapterId(chapterId, null);
  }

  @Override
  public List<LessonResponse> searchLessonsByChapterId(UUID chapterId, String name) {
    chapterRepository
        .findById(chapterId)
        .filter(c -> c.getDeletedAt() == null)
        .orElseThrow(() -> new AppException(ErrorCode.CHAPTER_NOT_FOUND));

    List<Lesson> lessons;
    if (name == null || name.isBlank()) {
      lessons = lessonRepository.findByChapterIdAndNotDeleted(chapterId);
    } else {
      lessons = lessonRepository.findByChapterIdAndTitleContainingAndNotDeleted(chapterId, name.trim());
    }

    return lessons.stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public LessonResponse updateLesson(UUID id, UpdateLessonRequest request) {
    validateAdminRole(SecurityUtils.getCurrentUserId());

    Lesson lesson = findActiveLesson(id);

    if (request.getTitle() != null) lesson.setTitle(request.getTitle());
    if (request.getLearningObjectives() != null)
      lesson.setLearningObjectives(request.getLearningObjectives());
    if (request.getLessonContent() != null) {
      lesson.setLessonContent(request.getLessonContent());
    }
    if (request.getSummary() != null) lesson.setSummary(request.getSummary());
    if (request.getOrderIndex() != null) lesson.setOrderIndex(request.getOrderIndex());
    if (request.getDurationMinutes() != null)
      lesson.setDurationMinutes(request.getDurationMinutes());
    if (request.getDifficulty() != null) lesson.setDifficulty(request.getDifficulty());
    if (request.getStatus() != null) lesson.setStatus(request.getStatus());

    return toResponse(lessonRepository.save(lesson));
  }

  @Override
  @Transactional
  public void deleteLesson(UUID id) {
    Lesson lesson = findActiveLesson(id);
    lesson.setDeletedAt(Instant.now());
    lessonRepository.save(lesson);
  }

  @Override
  @Transactional
  public LessonResponse restoreLesson(UUID id) {
    Lesson lesson = lessonRepository
        .findById(id)
        .orElseThrow(() -> new AppException(ErrorCode.LESSON_NOT_FOUND));
    lesson.setDeletedAt(null);
    return toResponse(lessonRepository.save(lesson));
  }

  @Override
  @Transactional(readOnly = true)
  public List<LessonResponse> getLessonsIncludingDeleted(UUID chapterId) {
    chapterRepository
        .findById(chapterId)
        .orElseThrow(() -> new AppException(ErrorCode.CHAPTER_NOT_FOUND));
    return lessonRepository.findByChapterIdIncludingDeleted(chapterId).stream()
        .map(l -> toResponse(l, true))
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public List<LessonResponse> reorderLessons(UUID chapterId, ReorderLessonsRequest request) {
    chapterRepository
        .findById(chapterId)
        .filter(c -> c.getDeletedAt() == null)
        .orElseThrow(() -> new AppException(ErrorCode.CHAPTER_NOT_FOUND));

    List<Lesson> lessons = lessonRepository.findByChapterIdAndNotDeleted(chapterId);
    Map<UUID, Lesson> lessonMap = lessons.stream()
        .collect(Collectors.toMap(Lesson::getId, l -> l));

    Set<UUID> chapterLessonIds = lessonMap.keySet();
    for (ReorderLessonsRequest.LessonOrder order : request.getOrders()) {
      if (!chapterLessonIds.contains(order.getLessonId())) {
        throw new AppException(ErrorCode.LESSON_NOT_FOUND);
      }
      lessonMap.get(order.getLessonId()).setOrderIndex(order.getOrderIndex());
    }

    List<Lesson> saved = lessonRepository.saveAll(lessons);
    return saved.stream().map(this::toResponse).collect(Collectors.toList());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private Lesson findActiveLesson(UUID id) {
    return lessonRepository
        .findByIdAndNotDeleted(id)
        .orElseThrow(() -> new AppException(ErrorCode.LESSON_NOT_FOUND));
  }

  private LessonResponse toResponse(Lesson l) {
    return toResponse(l, false);
  }

  private LessonResponse toResponse(Lesson l, boolean includeDeletedFlag) {
    return LessonResponse.builder()
        .id(l.getId())
        .chapterId(l.getChapterId())
        .title(l.getTitle())
        .learningObjectives(l.getLearningObjectives())
        .lessonContent(l.getLessonContent())
        .summary(l.getSummary())
        .orderIndex(l.getOrderIndex())
        .durationMinutes(l.getDurationMinutes())
        .difficulty(l.getDifficulty())
        .status(l.getStatus())
        .deleted(includeDeletedFlag && l.getDeletedAt() != null)
        .createdAt(l.getCreatedAt())
        .updatedAt(l.getUpdatedAt())
        .build();
  }

  private void validateAdminRole(UUID userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

    boolean isAdmin =
        user.getRoles() != null
            && user.getRoles().stream().anyMatch(role -> PredefinedRole.ADMIN_ROLE.equals(role.getName()));
    if (!isAdmin) {
      throw new AppException(ErrorCode.UNAUTHORIZED);
    }
  }
}
