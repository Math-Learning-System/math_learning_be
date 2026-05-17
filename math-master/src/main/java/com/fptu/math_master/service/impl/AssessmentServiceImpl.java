package com.fptu.math_master.service.impl;

import com.fptu.math_master.constant.PredefinedRole;
import com.fptu.math_master.dto.request.AddQuestionToAssessmentRequest;
import com.fptu.math_master.dto.request.AutoDistributePointsRequest;
import com.fptu.math_master.dto.request.BatchAddQuestionsRequest;
import com.fptu.math_master.dto.request.BatchUpdatePointsRequest;
import com.fptu.math_master.dto.request.AssessmentRequest;
import com.fptu.math_master.dto.request.DistributeAssessmentPointsRequest;
import com.fptu.math_master.dto.request.GenerateAssessmentByPercentageRequest;
import com.fptu.math_master.dto.request.GenerateAssessmentQuestionsRequest;
import com.fptu.math_master.dto.request.PointsOverrideRequest;
import com.fptu.math_master.dto.response.AssessmentGenerationResponse;
import com.fptu.math_master.dto.response.AssessmentQuestionResponse;
import com.fptu.math_master.configuration.properties.MinioProperties;
import com.fptu.math_master.dto.request.UpdateAssessmentPdfImportDocumentRequest;
import com.fptu.math_master.dto.request.UpdateAssessmentPdfImportMetadataRequest;
import com.fptu.math_master.dto.response.AssessmentPdfImportDocumentResponse;
import com.fptu.math_master.dto.response.AssessmentResponse;
import com.fptu.math_master.dto.response.AssessmentSourcePdfUrlResponse;
import com.fptu.math_master.dto.response.pdfimport.PdfImportPageDto;
import com.fptu.math_master.entity.SchoolGrade;
import com.fptu.math_master.service.AssessmentImportConfigService;
import com.fptu.math_master.service.AssessmentPdfImportDocumentHelper;
import com.fptu.math_master.service.AssessmentPdfImportMetadataHelper;
import com.fptu.math_master.dto.response.AssessmentSummary;
import com.fptu.math_master.dto.response.CognitiveLevelDistributionResponse;
import com.fptu.math_master.dto.response.DistributeAssessmentPointsResponse;
import com.fptu.math_master.dto.response.PagedDataResponse;
import com.fptu.math_master.dto.response.PercentageBasedGenerationResponse;
import com.fptu.math_master.dto.response.QuestionResponse;
import com.fptu.math_master.entity.Answer;
import com.fptu.math_master.entity.Assessment;
import com.fptu.math_master.entity.AssessmentLesson;
import com.fptu.math_master.entity.AssessmentQuestion;
import com.fptu.math_master.entity.Chapter;
import com.fptu.math_master.entity.ExamMatrix;
import com.fptu.math_master.entity.ExamMatrixBankMapping;
import com.fptu.math_master.entity.ExamMatrixRow;
import com.fptu.math_master.entity.Lesson;
import com.fptu.math_master.entity.Question;
import com.fptu.math_master.entity.QuestionBank;
import com.fptu.math_master.entity.Subject;
import com.fptu.math_master.entity.User;
import com.fptu.math_master.enums.AssessmentStatus;
import com.fptu.math_master.enums.AttemptScoringPolicy;
import com.fptu.math_master.enums.CognitiveLevel;
import com.fptu.math_master.enums.MatrixStatus;
import com.fptu.math_master.enums.QuestionType;
import com.fptu.math_master.exception.AppException;
import com.fptu.math_master.exception.ErrorCode;
import com.fptu.math_master.repository.*;
import com.fptu.math_master.service.AssessmentService;
import com.fptu.math_master.service.QuestionSelectionService;
import com.fptu.math_master.service.UploadService;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.fptu.math_master.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AssessmentServiceImpl implements AssessmentService {

  private static final String UNKNOWN_NAME = "Unknown";

  ObjectMapper objectMapper;
  AssessmentImportConfigService assessmentImportConfigService;
  com.fptu.math_master.repository.SchoolGradeRepository schoolGradeRepository;
  AssessmentRepository assessmentRepository;
  AssessmentLessonRepository assessmentLessonRepository;
  AssessmentQuestionRepository assessmentQuestionRepository;
  UserRepository userRepository;
  ExamMatrixRepository examMatrixRepository;
  ExamMatrixBankMappingRepository examMatrixBankMappingRepository;
  ExamMatrixRowRepository examMatrixRowRepository;
  ExamMatrixPartRepository examMatrixPartRepository;
  LessonRepository lessonRepository;
  ChapterRepository chapterRepository;
  SubjectRepository subjectRepository;
  QuestionBankRepository questionBankRepository;
  QuestionSelectionService questionSelectionService;
  QuestionRepository questionRepository;
  com.fptu.math_master.service.GradingService gradingService;
  UploadService uploadService;
  MinioProperties minioProperties;

  @Override
  @Transactional
  public AssessmentResponse createAssessment(AssessmentRequest request) {
    log.info("Creating assessment: {}", request.getTitle());

    UUID currentUserId = getCurrentUserId();
    validateTeacherRole(currentUserId);
    validateDates(request.getStartDate(), request.getEndDate());
    ExamMatrix matrix = validateAndGetAccessibleMatrix(request.getExamMatrixId(), currentUserId);
    // Lessons are now auto-populated from matrix, no need for manual selection

    Assessment assessment =
        Assessment.builder()
            .teacherId(currentUserId)
            .title(request.getTitle())
            .description(request.getDescription())
            .assessmentType(request.getAssessmentType())
            .timeLimitMinutes(request.getTimeLimitMinutes())
            .passingScore(request.getPassingScore())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .randomizeQuestions(Boolean.TRUE.equals(request.getRandomizeQuestions()))
            .showCorrectAnswers(Boolean.TRUE.equals(request.getShowCorrectAnswers()))
            .assessmentMode(request.getAssessmentMode())
            .examMatrixId(matrix.getId())
            .allowMultipleAttempts(
              Boolean.TRUE.equals(request.getAllowMultipleAttempts()))
            .maxAttempts(request.getMaxAttempts())
            .attemptScoringPolicy(
                request.getAttemptScoringPolicy() != null
                    ? request.getAttemptScoringPolicy()
                    : AttemptScoringPolicy.BEST)
            .showScoreImmediately(
              request.getShowScoreImmediately() == null || request.getShowScoreImmediately())
            .status(AssessmentStatus.DRAFT)
            .build();

    assessment = assessmentRepository.save(assessment);
    
    // BUG FIX #4: Auto-populate lessons from matrix instead of manual selection
    autoPopulateLessonsFromMatrix(assessment.getId(), matrix.getId());

    // Auto-map questions right after creating assessment when matrix already has mappings.
    autoMapQuestionsFromMatrixOnCreate(assessment.getId(), matrix.getId());

    log.info("Assessment created successfully with id: {}", assessment.getId());
    return mapToResponse(assessment);
  }

  @Override
  @Transactional
  public AssessmentResponse updateAssessment(UUID id, com.fptu.math_master.dto.request.UpdateAssessmentRequest request) {
    log.info("Updating assessment with id: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    UUID currentUserId = getCurrentUserId();
    validateOwnerOrAdmin(assessment.getTeacherId(), currentUserId);

    if (assessment.getStatus().isTerminal()) {
      throw new AppException(ErrorCode.ASSESSMENT_IS_CLOSED);
    }
    if (assessment.getStatus() == AssessmentStatus.PUBLISHED) {
      throw new AppException(ErrorCode.ASSESSMENT_ALREADY_PUBLISHED);
    }

    if (request.getStartDate() != null && request.getEndDate() != null) {
      validateDates(request.getStartDate(), request.getEndDate());
    } else if (request.getStartDate() != null) {
      validateDates(request.getStartDate(), assessment.getEndDate());
    } else if (request.getEndDate() != null) {
      validateDates(assessment.getStartDate(), request.getEndDate());
    }

    if (request.getTitle() != null) assessment.setTitle(request.getTitle());
    if (request.getDescription() != null) assessment.setDescription(request.getDescription());
    if (request.getAssessmentType() != null) assessment.setAssessmentType(request.getAssessmentType());
    if (request.getTimeLimitMinutes() != null) assessment.setTimeLimitMinutes(request.getTimeLimitMinutes());
    if (request.getPassingScore() != null) assessment.setPassingScore(request.getPassingScore());
    if (request.getStartDate() != null) assessment.setStartDate(request.getStartDate());
    if (request.getEndDate() != null) assessment.setEndDate(request.getEndDate());

    if (request.getRandomizeQuestions() != null) {
      assessment.setRandomizeQuestions(request.getRandomizeQuestions());
    }
    if (request.getShowCorrectAnswers() != null) {
      assessment.setShowCorrectAnswers(request.getShowCorrectAnswers());
    }
    if (request.getAssessmentMode() != null) {
      assessment.setAssessmentMode(request.getAssessmentMode());
    }
    
    ExamMatrix matrix = null;
    if (request.getExamMatrixId() != null) {
      matrix = validateAndGetAccessibleMatrix(request.getExamMatrixId(), currentUserId);
      assessment.setExamMatrixId(matrix.getId());
    }
    
    if (request.getAllowMultipleAttempts() != null) {
      assessment.setAllowMultipleAttempts(request.getAllowMultipleAttempts());
    }
    if (request.getMaxAttempts() != null) {
      assessment.setMaxAttempts(request.getMaxAttempts());
    }
    if (request.getAttemptScoringPolicy() != null) {
      assessment.setAttemptScoringPolicy(request.getAttemptScoringPolicy());
    }
    if (request.getShowScoreImmediately() != null) {
      assessment.setShowScoreImmediately(request.getShowScoreImmediately());
    }

    assessment = assessmentRepository.save(assessment);
    
    // BUG FIX #4: Auto-populate lessons from matrix when matrix changes
    if (matrix != null) {
      autoPopulateLessonsFromMatrix(assessment.getId(), matrix.getId());
    }
    
    log.info("Assessment updated successfully: {}", id);
    return mapToResponse(assessment);
  }

  @Override
  @Transactional
  public AssessmentResponse setPointsOverride(UUID assessmentId, PointsOverrideRequest request) {
    log.info(
        "Setting points override for assessment: {}, question: {}",
        assessmentId,
        request.getQuestionId());

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    // Per-question points are explicitly editable on every assessment regardless
    // of status / matrix / bank linkage. The teacher owns the final answer key
    // and may need to rebalance points after the fact (typo fix, weight tweak,
    // etc.) without going through a clone/republish cycle.

    AssessmentQuestion aq =
        assessmentQuestionRepository
            .findByAssessmentIdAndQuestionId(assessmentId, request.getQuestionId())
            .orElseThrow(() -> new AppException(ErrorCode.ASSESSMENT_QUESTION_NOT_FOUND));

    aq.setPointsOverride(request.getPointsOverride());
    assessmentQuestionRepository.save(aq);

    // Only process matrix if Assessment has an exam matrix linked
    if (assessment.getExamMatrixId() != null) {
      examMatrixRepository
          .findByIdAndNotDeleted(assessment.getExamMatrixId())
          .ifPresent(
              matrix -> {
                if (matrix.getStatus() == MatrixStatus.APPROVED) {
                  matrix.setStatus(MatrixStatus.DRAFT);
                  examMatrixRepository.save(matrix);
                  log.warn(
                      "Matrix {} demoted to DRAFT because points override invalidates its cached distribution",
                      matrix.getId());
                }
              });
    }

    log.info("Points override set successfully");
    return mapToResponse(assessment);
  }

  @Override
  @Transactional(readOnly = true)
  public AssessmentResponse getAssessmentPreview(UUID id) {
    log.info("Getting assessment preview: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());
    return mapToResponse(assessment);
  }

  @Override
  @Transactional(readOnly = true)
  public com.fptu.math_master.dto.response.PreviewSubmitResponse previewSubmit(
      UUID assessmentId, com.fptu.math_master.dto.request.PreviewSubmitRequest request) {
    log.info("Preview-submit assessment: {}", assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    List<AssessmentQuestion> assessmentQuestions =
        assessmentQuestionRepository.findByAssessmentIdOrderByOrderIndex(assessmentId);
    if (assessmentQuestions.isEmpty()) {
      throw new AppException(ErrorCode.ASSESSMENT_NO_QUESTIONS);
    }

    Set<UUID> questionIds = new HashSet<>();
    for (AssessmentQuestion aq : assessmentQuestions) questionIds.add(aq.getQuestionId());
    Map<UUID, Question> questionById = new HashMap<>();
    for (Question q : questionRepository.findAllById(questionIds)) questionById.put(q.getId(), q);

    Map<UUID, Object> submittedAnswers =
        request.getAnswers() != null ? request.getAnswers() : java.util.Collections.emptyMap();

    java.math.BigDecimal totalScore = java.math.BigDecimal.ZERO;
    java.math.BigDecimal maxScore = java.math.BigDecimal.ZERO;
    int correctCount = 0;
    List<com.fptu.math_master.dto.response.PreviewAnswerResult> answerResults =
        new ArrayList<>(assessmentQuestions.size());

    for (AssessmentQuestion aq : assessmentQuestions) {
      Question question = questionById.get(aq.getQuestionId());
      if (question == null) continue;

      java.math.BigDecimal effectivePoints =
          aq.getPointsOverride() != null
              ? aq.getPointsOverride()
              : (question.getPoints() != null ? question.getPoints() : java.math.BigDecimal.ONE);
      maxScore = maxScore.add(effectivePoints);

      Object rawAnswer = submittedAnswers.get(question.getId());
      Answer transientAnswer = buildTransientAnswerForPreview(rawAnswer);

      gradingService.gradeAnswerInMemory(transientAnswer, question, effectivePoints);

      java.math.BigDecimal earned =
          transientAnswer.getPointsEarned() != null
              ? transientAnswer.getPointsEarned()
              : java.math.BigDecimal.ZERO;
      totalScore = totalScore.add(earned);
      boolean isCorrect = Boolean.TRUE.equals(transientAnswer.getIsCorrect());
      if (isCorrect) correctCount++;

      answerResults.add(
          com.fptu.math_master.dto.response.PreviewAnswerResult.builder()
              .questionId(question.getId())
              .orderIndex(aq.getOrderIndex() != null ? aq.getOrderIndex() : 0)
              .questionType(
                  question.getQuestionType() != null ? question.getQuestionType().name() : null)
              .questionText(question.getQuestionText())
              .options(question.getOptions())
              .explanation(question.getExplanation())
              .tags(question.getTags())
              .studentAnswer(rawAnswerToString(rawAnswer))
              .correctAnswer(question.getCorrectAnswer())
              .isCorrect(isCorrect)
              .pointsEarned(earned)
              .maxPoints(effectivePoints)
              .scoringDetail(transientAnswer.getScoringDetail())
              .build());
    }

    return com.fptu.math_master.dto.response.PreviewSubmitResponse.builder()
        .totalScore(totalScore)
        .maxScore(maxScore)
        .totalQuestions(assessmentQuestions.size())
        .correctCount(correctCount)
        .answers(answerResults)
        .build();
  }

  /**
   * Build a transient {@link Answer} to feed into the in-memory grader. The
   * shape of {@code raw} mirrors what the FE TakeAssessment flow stores in
   * Redis: a string (MCQ key, TF csv, SA text) or a list/object whose
   * {@code value}/{@code selected} field carries the same.
   */
  private Answer buildTransientAnswerForPreview(Object raw) {
    Answer answer = new Answer();
    Map<String, Object> data = new HashMap<>();
    if (raw == null) {
      // leave data empty — grader treats as not answered → 0 points
    } else if (raw instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (e.getKey() != null) data.put(e.getKey().toString(), e.getValue());
      }
    } else {
      data.put("value", raw);
      data.put("selected", raw);
    }
    answer.setAnswerData(data);
    if (raw != null && !(raw instanceof Map<?, ?>) && !(raw instanceof java.util.Collection<?>)) {
      answer.setAnswerText(raw.toString());
    }
    return answer;
  }

  private String rawAnswerToString(Object raw) {
    if (raw == null) return null;
    if (raw instanceof String s) return s;
    if (raw instanceof java.util.Collection<?> coll) {
      List<String> parts = new ArrayList<>();
      for (Object o : coll) if (o != null) parts.add(o.toString());
      return String.join(",", parts);
    }
    return raw.toString();
  }

  @Override
  @Transactional(readOnly = true)
  public AssessmentSummary getPublishSummary(UUID id) {
    log.info("Getting publish summary for assessment: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    Long totalQuestions = assessmentRepository.countQuestionsByAssessmentId(id);
    BigDecimal totalPoints = safeTotalPoints(assessmentRepository.calculateTotalPoints(id));

    boolean canPublish = true;
    String validationMessage = "";

    if (totalQuestions == 0) {
      canPublish = false;
      validationMessage = "Assessment must have at least one question";
    } else if (totalPoints.compareTo(BigDecimal.ZERO) <= 0) {
      canPublish = false;
      validationMessage = "Total points must be greater than 0";
    } else if (assessment.getStartDate() != null
        && assessment.getStartDate().isBefore(Instant.now())) {
      canPublish = false;
      validationMessage = "Start date cannot be in the past";
    }

    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

    return AssessmentSummary.builder()
        .totalQuestions(totalQuestions)
        .totalPoints(totalPoints)
        .timeLimitMinutes(assessment.getTimeLimitMinutes())
        .startDate(
            assessment.getStartDate() != null ? formatter.format(assessment.getStartDate()) : null)
        .endDate(assessment.getEndDate() != null ? formatter.format(assessment.getEndDate()) : null)
        .hasSchedule(assessment.getStartDate() != null || assessment.getEndDate() != null)
        .canPublish(canPublish)
        .validationMessage(validationMessage)
        .build();
  }

  @Override
  @Transactional
  public AssessmentResponse publishAssessment(UUID id) {
    log.info("Publishing assessment: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    if (assessment.getStatus() == AssessmentStatus.PUBLISHED) {
      throw new AppException(ErrorCode.ASSESSMENT_ALREADY_PUBLISHED);
    }
    if (assessment.getStatus().isTerminal()) {
      throw new AppException(ErrorCode.ASSESSMENT_IS_CLOSED);
    }

    Instant now = Instant.now();

    Long totalQuestions = assessmentRepository.countQuestionsByAssessmentId(id);
    if (totalQuestions == 0) {
      throw new AppException(ErrorCode.ASSESSMENT_NO_QUESTIONS);
    }

    BigDecimal totalPoints = safeTotalPoints(assessmentRepository.calculateTotalPoints(id));
    if (totalPoints.compareTo(BigDecimal.ZERO) <= 0) {
      throw new AppException(ErrorCode.ASSESSMENT_ZERO_TOTAL_POINTS);
    }

    if (assessment.getStartDate() != null && assessment.getStartDate().isBefore(now)) {
      throw new AppException(ErrorCode.ASSESSMENT_START_DATE_PAST);
    }

    assessment.setStatus(AssessmentStatus.PUBLISHED);
    assessment = assessmentRepository.save(assessment);
    log.info("Assessment published successfully: {}", id);
    return mapToResponse(assessment);
  }

  @Override
  @Transactional
  public AssessmentResponse unpublishAssessment(UUID id) {
    log.info("Unpublishing assessment: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    if (assessment.getStatus().isTerminal()) {
      throw new AppException(ErrorCode.ASSESSMENT_IS_CLOSED);
    }
    if (assessment.getStatus() != AssessmentStatus.PUBLISHED) {
      throw new AppException(ErrorCode.ASSESSMENT_NOT_PUBLISHED);
    }

    Long submissionCount = assessmentRepository.countSubmissionsByAssessmentId(id);
    if (submissionCount > 0) {
      throw new AppException(ErrorCode.ASSESSMENT_HAS_SUBMISSIONS);
    }

    assessment.setStatus(AssessmentStatus.DRAFT);
    assessmentRepository.save(assessment);

    if (assessment.getExamMatrixId() != null) {
      examMatrixRepository
          .findByIdAndNotDeleted(assessment.getExamMatrixId())
          .ifPresent(
              matrix -> {
                if (matrix.getStatus() == MatrixStatus.LOCKED) {
                  matrix.setStatus(MatrixStatus.APPROVED);
                  examMatrixRepository.save(matrix);
                  log.info(
                      "Matrix {} reset from LOCKED to APPROVED after assessment unpublish",
                      matrix.getId());
                }
              });
    }

    log.info("Assessment unpublished successfully: {}", id);
    return mapToResponse(assessment);
  }

  @Override
  @Transactional
  public void deleteAssessment(UUID id) {
    log.info("Deleting assessment: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    if (assessment.getStatus().isTerminal()) {
      throw new AppException(ErrorCode.ASSESSMENT_IS_CLOSED);
    }
    if (assessment.getStatus() != AssessmentStatus.DRAFT) {
      throw new AppException(ErrorCode.ASSESSMENT_ALREADY_PUBLISHED);
    }

    Long submissionCount = assessmentRepository.countSubmissionsByAssessmentId(id);
    if (submissionCount > 0) {
      throw new AppException(ErrorCode.ASSESSMENT_HAS_SUBMISSIONS);
    }

    Instant now = Instant.now();
    assessment.setDeletedAt(now);
    assessmentRepository.save(assessment);

    examMatrixRepository
        .findByAssessmentIdAndNotDeleted(id)
        .ifPresent(
            matrix -> {
              matrix.setDeletedAt(now);
              examMatrixRepository.save(matrix);
              log.info(
                  "Soft-deleted orphaned ExamMatrix {} along with Assessment {}",
                  matrix.getId(),
                  id);
            });

    log.info("Assessment soft deleted successfully: {}", id);
  }

  @Override
  @Transactional(readOnly = true)
  public AssessmentResponse getAssessmentById(UUID id) {
    log.info("Getting assessment: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    UUID currentUserId = getCurrentUserId();

    boolean isOwnerOrAdmin =
        assessment.getTeacherId().equals(currentUserId) || hasRole(PredefinedRole.ADMIN_ROLE);
    boolean isPublishedAndAnyUser =
        assessment.getStatus() == AssessmentStatus.PUBLISHED && !isOwnerOrAdmin;

    if (!isOwnerOrAdmin && !isPublishedAndAnyUser) {
      throw new AppException(ErrorCode.ASSESSMENT_ACCESS_DENIED);
    }

    return mapToResponse(assessment);
  }

  @Override
  @Transactional(readOnly = true)
  public AssessmentSourcePdfUrlResponse getImportSourcePdfUrl(UUID id) {
    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());
    String key = assessment.getSourcePdfPath();
    if (!StringUtils.hasText(key)) {
      throw new AppException(ErrorCode.INVALID_REQUEST, "Đề này không có file PDF nguồn từ import.");
    }
    String fileName =
        StringUtils.hasText(assessment.getSourcePdfOriginalName())
            ? assessment.getSourcePdfOriginalName().trim()
            : "de-import.pdf";
    String url =
        uploadService.getPresignedUrl(key.trim(), minioProperties.getTemplateBucket());
    return new AssessmentSourcePdfUrlResponse(url, fileName);
  }

  @Override
  @Transactional(readOnly = true)
  public AssessmentPdfImportDocumentResponse getPdfImportDocument(UUID id) {
    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());
    if (!StringUtils.hasText(assessment.getSourcePdfPath())
        && !StringUtils.hasText(assessment.getPdfImportPagesJson())) {
      throw new AppException(
          ErrorCode.INVALID_REQUEST, "Đề này không có dữ liệu import PDF để rà soát.");
    }
    String json = assessment.getPdfImportDocumentJson();
    if (StringUtils.hasText(json)) {
      return AssessmentPdfImportDocumentHelper.parseDocumentJson(json, objectMapper);
    }
    List<PdfImportPageDto> pages = parsePdfImportPages(assessment.getPdfImportPagesJson());
    if (pages.isEmpty()) {
      return AssessmentPdfImportDocumentHelper.emptyDocument();
    }
    return AssessmentPdfImportDocumentHelper.seedFromPages(pages, null);
  }

  @Override
  @Transactional
  public AssessmentPdfImportDocumentResponse updatePdfImportDocument(
      UUID id, UpdateAssessmentPdfImportDocumentRequest request) {
    if (!canEditAssessment(id)) {
      throw new AppException(ErrorCode.UNAUTHORIZED, "Không thể chỉnh sửa đề ở trạng thái hiện tại.");
    }
    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());
    AssessmentPdfImportDocumentResponse doc =
        AssessmentPdfImportDocumentResponse.builder()
            .questionBlocks(
                request.getQuestionBlocks() != null
                    ? request.getQuestionBlocks()
                    : List.of())
            .answerBlocks(
                request.getAnswerBlocks() != null ? request.getAnswerBlocks() : List.of())
            .build();
    assessment.setPdfImportDocumentJson(
        AssessmentPdfImportDocumentHelper.toJson(doc, objectMapper));
    assessmentRepository.save(assessment);
    return doc;
  }

  @Override
  @Transactional
  public AssessmentResponse updatePdfImportMetadata(
      UUID id, UpdateAssessmentPdfImportMetadataRequest request) {
    if (!canEditAssessment(id)) {
      throw new AppException(ErrorCode.UNAUTHORIZED, "Không thể chỉnh sửa đề ở trạng thái hiện tại.");
    }
    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());
    if (!StringUtils.hasText(assessment.getSourcePdfPath())) {
      throw new AppException(ErrorCode.INVALID_KEY, "Đề này không phải import từ PDF.");
    }

    var existing =
        AssessmentPdfImportMetadataHelper.parseMetadataJson(
            assessment.getPdfImportMetadataJson(), objectMapper);
    if (existing == null) {
      existing = AssessmentPdfImportMetadataHelper.buildFallbackFromAssessment(assessment);
    }

    String gradeName = null;
    if (request.getSchoolGradeId() != null) {
      gradeName =
          schoolGradeRepository
              .findById(request.getSchoolGradeId())
              .map(SchoolGrade::getName)
              .orElse(null);
    }
    String subjectName = null;
    if (request.getSubjectId() != null) {
      subjectName =
          subjectRepository.findById(request.getSubjectId()).map(Subject::getName).orElse(null);
    }
    String bankName = null;
    if (request.getQuestionBankId() != null) {
      bankName =
          questionBankRepository
              .findById(request.getQuestionBankId())
              .map(QuestionBank::getName)
              .orElse(null);
    }

    var merged =
        AssessmentPdfImportMetadataHelper.mergeUpdate(
            existing,
            request,
            assessmentImportConfigService.getFormOptions(),
            gradeName,
            subjectName,
            bankName);
    assessment.setPdfImportMetadataJson(
        AssessmentPdfImportMetadataHelper.toJson(merged, objectMapper));

    if (request.getExamTitle() != null && !request.getExamTitle().isBlank()) {
      assessment.setTitle(request.getExamTitle().trim());
    }
    if (request.getTimeLimitMinutes() != null) {
      assessment.setTimeLimitMinutes(request.getTimeLimitMinutes());
    }
    if (request.getAssessmentType() != null) {
      assessment.setAssessmentType(request.getAssessmentType());
    }

    assessment = assessmentRepository.save(assessment);
    return mapToResponse(assessment);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AssessmentResponse> getMyAssessments(AssessmentStatus status, String search, Pageable pageable) {

    UUID currentUserId = getCurrentUserId();
    String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
    Page<Assessment> assessmentsPage =
        assessmentRepository.findWithFilters(currentUserId, status, searchTerm, pageable);

    // FIX: eliminate N+1 — fetch bulk summary for all IDs on this page in one query
    List<UUID> ids = assessmentsPage.getContent().stream().map(Assessment::getId).toList();

    Map<UUID, long[]> summaryMap = buildSummaryMap(ids);

    // Pre-fetch teacher name once (all assessments on this page share the same teacher)
    String teacherName =
      userRepository.findById(currentUserId).map(User::getFullName).orElse(UNKNOWN_NAME);

    return assessmentsPage.map(
        a ->
            mapToResponseWithSummary(
                a, teacherName, summaryMap.getOrDefault(a.getId(), new long[] {0L, 0L, 0L})));
  }

  @Override
  @Transactional(readOnly = true)
  public List<AssessmentResponse> searchAssessmentsByName(String name, AssessmentStatus status) {
    // Return all assessments when keyword is empty (for dropdown/search UX)
    String keyword = (name == null || name.trim().isEmpty()) ? "" : name.trim();

    return assessmentRepository
        .findByTitleContainingAndStatusAndNotDeleted(keyword, status)
        .stream()
        .map(this::mapToResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canEditAssessment(UUID id) {
    Assessment assessment = loadAssessmentOrThrow(id);
    UUID currentUserId = getCurrentUserId();
    return (assessment.getTeacherId().equals(currentUserId) || hasRole(PredefinedRole.ADMIN_ROLE))
        && assessment.getStatus() == AssessmentStatus.DRAFT;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canDeleteAssessment(UUID id) {
    Assessment assessment = loadAssessmentOrThrow(id);
    UUID currentUserId = getCurrentUserId();
    Long submissionCount = assessmentRepository.countSubmissionsByAssessmentId(id);
    return (assessment.getTeacherId().equals(currentUserId) || hasRole(PredefinedRole.ADMIN_ROLE))
        && assessment.getStatus() == AssessmentStatus.DRAFT
        && submissionCount == 0;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canPublishAssessment(UUID id) {
    Assessment assessment = loadAssessmentOrThrow(id);
    UUID currentUserId = getCurrentUserId();
    Long totalQuestions = assessmentRepository.countQuestionsByAssessmentId(id);
    return (assessment.getTeacherId().equals(currentUserId) || hasRole(PredefinedRole.ADMIN_ROLE))
        && assessment.getStatus() == AssessmentStatus.DRAFT
        && totalQuestions > 0;
  }

  @Override
  @Transactional
  public AssessmentResponse closeAssessment(UUID id) {
    log.info("Closing assessment: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    if (assessment.getStatus() == AssessmentStatus.CLOSED) {
      throw new AppException(ErrorCode.ASSESSMENT_ALREADY_CLOSED);
    }
    if (assessment.getStatus() != AssessmentStatus.PUBLISHED) {
      throw new AppException(ErrorCode.ASSESSMENT_NOT_PUBLISHED);
    }

    assessment.setStatus(AssessmentStatus.CLOSED);
    assessment = assessmentRepository.save(assessment);
    log.info("Assessment {} closed", id);
    return mapToResponse(assessment);
  }

  @Override
  @Transactional
  public AssessmentResponse reopenAssessment(UUID id) {
    log.info("Reopening assessment: {}", id);

    Assessment assessment = loadAssessmentOrThrow(id);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    if (assessment.getStatus() != AssessmentStatus.CLOSED) {
      throw new AppException(ErrorCode.ASSESSMENT_NOT_CLOSED);
    }

    assessment.setStatus(AssessmentStatus.PUBLISHED);
    assessment = assessmentRepository.save(assessment);
    log.info("Assessment {} reopened", id);
    return mapToResponse(assessment);
  }

  @Override
  @Transactional
  public AssessmentResponse addQuestion(UUID assessmentId, AddQuestionToAssessmentRequest request) {
    log.info("Adding question {} to assessment {}", request.getQuestionId(), assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    // Question management is unrestricted — teachers can add/remove questions
    // on any status, including matrix-based assessments, so they can rebalance
    // their answer keys after publishing without going through a clone cycle.

    // Verify the question exists and is not deleted
    questionRepository
        .findByIdAndNotDeleted(request.getQuestionId())
        .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

    if (assessmentQuestionRepository
        .findByAssessmentIdAndQuestionId(assessmentId, request.getQuestionId())
        .isPresent()) {
      throw new AppException(ErrorCode.QUESTION_ALREADY_IN_ASSESSMENT);
    }

    // Determine order index
    int nextOrder;
    if (request.getOrderIndex() != null) {
      nextOrder = request.getOrderIndex();
    } else {
      Integer maxOrder = assessmentQuestionRepository.findMaxOrderIndex(assessmentId);
      nextOrder = (maxOrder != null ? maxOrder : 0) + 1;
    }

    AssessmentQuestion aq =
        AssessmentQuestion.builder()
            .assessmentId(assessmentId)
            .questionId(request.getQuestionId())
            .orderIndex(nextOrder)
            .pointsOverride(request.getPointsOverride())
            .build();
    assessmentQuestionRepository.save(aq);

    log.info(
        "Question {} added to assessment {} at index {}",
        request.getQuestionId(),
        assessmentId,
        nextOrder);
    return mapToResponse(assessment);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AssessmentQuestionResponse> getAssessmentQuestions(UUID assessmentId) {
    log.info("Getting questions for assessment {}", assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    List<AssessmentQuestion> assessmentQuestions =
        assessmentQuestionRepository.findByAssessmentIdOrderByOrderIndex(assessmentId);

    return assessmentQuestions.stream()
        .map(
            aq -> {
              Question question =
                  questionRepository
                      .findByIdAndNotDeleted(aq.getQuestionId())
                      .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

              BigDecimal effectivePoints =
                  aq.getPointsOverride() != null ? aq.getPointsOverride() : question.getPoints();

              Map<String, Object> genMeta = question.getGenerationMetadata();
              Integer pageNumber = readMetaInt(genMeta, "pageNumber");
              String sectionLabel = readMetaString(genMeta, "sectionLabel");
              Boolean pageSource = readMetaBoolean(genMeta, "pageSource");

              return AssessmentQuestionResponse.builder()
                  .questionId(question.getId())
                  .orderIndex(aq.getOrderIndex())
                  .points(effectivePoints)
                  .pointsOverride(aq.getPointsOverride())
                  .questionType(question.getQuestionType())
                  .questionText(question.getQuestionText())
                  .options(question.getOptions())
                  .correctAnswer(question.getCorrectAnswer())
                  .explanation(question.getExplanation())
                  .diagramData(question.getDiagramData())
                  .diagramUrl(question.getRenderedImageUrl())
                  .tags(question.getTags())
                  .cognitiveLevel(question.getCognitiveLevel())
                  .createdAt(question.getCreatedAt())
                  .pageNumber(pageNumber)
                  .sectionLabel(sectionLabel)
                  .pageSource(pageSource)
                  .build();
            })
        .toList();
  }

  @Override
  @Transactional
  public List<AssessmentQuestionResponse> reorderQuestions(
      UUID assessmentId,
      com.fptu.math_master.dto.request.ReorderAssessmentQuestionsRequest request) {
    log.info(
        "Reordering {} questions for assessment {}", request.getOrders().size(), assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    List<com.fptu.math_master.dto.request.ReorderAssessmentQuestionsRequest.Item> orders =
        request.getOrders();

    java.util.Set<UUID> seenQuestionIds = new java.util.HashSet<>();
    java.util.Set<Integer> seenOrders = new java.util.HashSet<>();
    for (var item : orders) {
      if (!seenQuestionIds.add(item.getQuestionId())) {
        throw new AppException(ErrorCode.DUPLICATE_ORDER_INDEX);
      }
      if (!seenOrders.add(item.getOrderIndex())) {
        throw new AppException(ErrorCode.DUPLICATE_ORDER_INDEX);
      }
    }

    List<AssessmentQuestion> existing =
        assessmentQuestionRepository.findByAssessmentIdOrderByOrderIndex(assessmentId);
    if (existing.size() != orders.size()) {
      throw new AppException(ErrorCode.ASSESSMENT_QUESTION_NOT_FOUND);
    }

    java.util.Map<UUID, AssessmentQuestion> byQuestionId = new java.util.HashMap<>();
    for (AssessmentQuestion aq : existing) {
      byQuestionId.put(aq.getQuestionId(), aq);
    }
    for (var item : orders) {
      if (!byQuestionId.containsKey(item.getQuestionId())) {
        throw new AppException(ErrorCode.QUESTION_NOT_IN_ASSESSMENT);
      }
    }

    // Two-phase save to avoid violating UNIQUE(assessment_id, order_index):
    // 1) bump every targeted row's order_index to a non-conflicting negative value, flush
    // 2) assign final order_index values
    int tmp = -1;
    for (AssessmentQuestion aq : existing) {
      aq.setOrderIndex(tmp--);
    }
    assessmentQuestionRepository.saveAllAndFlush(existing);

    for (var item : orders) {
      AssessmentQuestion aq = byQuestionId.get(item.getQuestionId());
      aq.setOrderIndex(item.getOrderIndex());
    }
    assessmentQuestionRepository.saveAllAndFlush(existing);

    log.info("Reordered {} questions for assessment {}", orders.size(), assessmentId);
    return getAssessmentQuestions(assessmentId);
  }

      @Override
      @Transactional(readOnly = true)
      public PagedDataResponse<QuestionResponse> getAvailableQuestions(
        UUID assessmentId, String keyword, String tag, Pageable pageable) {
      log.info(
        "Getting available questions for assessment {} with keyword='{}', tag='{}'",
        assessmentId,
        keyword,
        tag);

      Assessment assessment = loadAssessmentOrThrow(assessmentId);
      UUID currentUserId = getCurrentUserId();
      validateOwnerOrAdmin(assessment.getTeacherId(), currentUserId);

      String normalizedKeyword = normalizeKeywordPattern(keyword);
      String normalizedTag = normalizeKeywordPattern(tag);

      // Create Pageable with correct column name for native query (snake_case)
      Pageable sortedPageable = PageRequest.of(
          pageable.getPageNumber(),
          pageable.getPageSize(),
          Sort.by(Sort.Direction.DESC, "created_at")
      );

      Page<Question> page =
        questionRepository.findAvailableByAssessmentId(
          assessment.getTeacherId(), assessmentId, normalizedKeyword, normalizedTag, sortedPageable);

      List<QuestionResponse> items = page.getContent().stream().map(this::mapQuestionToResponse).toList();

      return PagedDataResponse.<QuestionResponse>builder()
        .data(items)
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .build();
      }

  @Override
  @Transactional
  public AssessmentResponse removeQuestion(UUID assessmentId, UUID questionId) {
    log.info("Removing question {} from assessment {}", questionId, assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    // Question management is unrestricted on every assessment — see addQuestion.

    assessmentQuestionRepository
        .findByAssessmentIdAndQuestionId(assessmentId, questionId)
        .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_IN_ASSESSMENT));

    assessmentQuestionRepository.deleteByAssessmentIdAndQuestionId(assessmentId, questionId);

    log.info("Question {} removed from assessment {}", questionId, assessmentId);
    return mapToResponse(assessment);
  }

  @Override
  @Transactional
  public AssessmentGenerationResponse generateQuestionsFromMatrix(
      UUID assessmentId, GenerateAssessmentQuestionsRequest request) {
    return generateQuestionsFromMatrixInternal(assessmentId, request);
    }

    private AssessmentGenerationResponse generateQuestionsFromMatrixInternal(
      UUID assessmentId, GenerateAssessmentQuestionsRequest request) {
    log.info(
        "Generating questions from matrix {} for assessment {}",
        request.getExamMatrixId(),
        assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    UUID currentUserId = getCurrentUserId();
    validateOwnerOrAdmin(assessment.getTeacherId(), currentUserId);

    if (assessment.getStatus() != AssessmentStatus.DRAFT) {
      throw new AppException(ErrorCode.ASSESSMENT_ALREADY_PUBLISHED);
    }

    // Load the exam matrix
    ExamMatrix matrix =
        examMatrixRepository
            .findByIdAndNotDeleted(request.getExamMatrixId())
            .orElseThrow(() -> new AppException(ErrorCode.EXAM_MATRIX_NOT_FOUND));

    // Allow generation from both APPROVED and LOCKED matrices for reuse.
    if (!isMatrixReusableForGeneration(matrix)) {
      throw new AppException(ErrorCode.EXAM_MATRIX_NOT_FOUND);
    }

    List<ExamMatrixBankMapping> bankMappings =
      examMatrixBankMappingRepository.findByExamMatrixIdOrderByCreatedAt(matrix.getId());

    if (bankMappings.isEmpty()) {
      throw new AppException(ErrorCode.MATRIX_VALIDATION_FAILED);
    }

    // BUG FIX #4: Auto-populate lessons from matrix
    autoPopulateLessonsFromMatrix(assessmentId, matrix.getId());

    // Regenerate from blueprint to keep assessment deterministic and aligned with matrix.
    assessmentQuestionRepository.deleteAllByAssessmentId(assessmentId);

    // Matrix is a pure blueprint: prefer the bank set from the generation
    // request (multi-bank), fall back to the legacy single-bank field, then
    // to the matrix's stored default for legacy callers.
    java.util.Collection<UUID> overrideBankIds = effectiveBankIds(request);
    QuestionSelectionService.SelectionPlan selectionPlan =
      questionSelectionService.buildSelectionPlan(
          assessmentId, matrix.getId(), overrideBankIds, 1);

    assessmentQuestionRepository.saveAll(selectionPlan.assessmentQuestions());

    int totalQuestionsGenerated = selectionPlan.assessmentQuestions().size();
    int totalPoints = selectionPlan.totalPoints();

    log.info(
      "Generated {} bank-based questions for assessment {} with {} total points",
      totalQuestionsGenerated,
      assessmentId,
      totalPoints);

    return AssessmentGenerationResponse.builder()
      .totalQuestionsGenerated(totalQuestionsGenerated)
      .questionsFromBank(totalQuestionsGenerated)
      .questionsFromAi(0)
      .totalPoints(totalPoints)
      .warnings(null)
      .message(
        String.format(
          "%d questions selected from Question Bank based on Exam Matrix rules. Total points: %d",
          totalQuestionsGenerated, totalPoints))
      .build();
  }

  private void autoPopulateLessonsFromMatrix(UUID assessmentId, UUID matrixId) {
    // Phase 4: Get chapters directly from ExamMatrixRow.chapterId
    // Legacy bank-based lookup removed - all matrices must have chapter data in rows
    List<UUID> chapterIds =
        examMatrixRowRepository.findByExamMatrixId(matrixId).stream()
            .map(ExamMatrixRow::getChapterId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    if (chapterIds.isEmpty()) {
      log.warn("No chapters found in matrix {} rows - cannot populate lessons", matrixId);
      return;
    }

    // Get all lessons from those chapters
    List<UUID> lessonIds =
        lessonRepository.findByChapterIdIn(chapterIds).stream().map(Lesson::getId).toList();

    // Sync assessment lessons
    if (!lessonIds.isEmpty()) {
      syncAssessmentLessons(assessmentId, lessonIds);
      log.info(
          "Auto-populated {} lessons for assessment {} from matrix {} (chapter-based)",
          lessonIds.size(),
          assessmentId,
          matrixId);
    }
  }

  private boolean isMatrixReusableForGeneration(ExamMatrix matrix) {
    return matrix.getStatus() == MatrixStatus.APPROVED
        || matrix.getStatus() == MatrixStatus.LOCKED;
  }

  private Assessment loadAssessmentOrThrow(UUID id) {
    return assessmentRepository
        .findByIdAndNotDeleted(id)
        .orElseThrow(() -> new AppException(ErrorCode.ASSESSMENT_NOT_FOUND));
  }

  /**
   * Project the bank-id fields of a generation request into a single override
   * collection. Prefers the multi-bank list when populated; falls back to the
   * legacy single-bank field; returns null when neither is set so the
   * QuestionSelectionService can fall back to the matrix's stored default.
   */
  private java.util.Collection<UUID> effectiveBankIds(GenerateAssessmentQuestionsRequest request) {
    if (request.getQuestionBankIds() != null && !request.getQuestionBankIds().isEmpty()) {
      java.util.List<UUID> deduped = new java.util.ArrayList<>();
      for (UUID b : request.getQuestionBankIds()) if (b != null) deduped.add(b);
      if (!deduped.isEmpty()) return deduped;
    }
    if (request.getQuestionBankId() != null) {
      return java.util.List.of(request.getQuestionBankId());
    }
    return null;
  }

  private String normalizeKeywordPattern(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return "%" + value.trim() + "%";
  }

  private QuestionResponse mapQuestionToResponse(Question question) {
    String creatorName =
        userRepository.findById(question.getCreatedBy()).map(User::getFullName).orElse(UNKNOWN_NAME);

    return QuestionResponse.builder()
        .id(question.getId())
        .createdBy(question.getCreatedBy())
        .creatorName(creatorName)
        .questionText(question.getQuestionText())
        .questionType(question.getQuestionType())
        .options(question.getOptions())
        .correctAnswer(question.getCorrectAnswer())
        .explanation(question.getExplanation())
        .solutionSteps(question.getSolutionSteps())
        .diagramData(question.getDiagramData())
        .diagramUrl(question.getRenderedImageUrl())
        .points(question.getPoints())
        .cognitiveLevel(question.getCognitiveLevel())
        .questionStatus(question.getQuestionStatus())
        .questionSourceType(question.getQuestionSourceType())
        .tags(question.getTags())
        .templateId(question.getTemplateId())
        .canonicalQuestionId(question.getCanonicalQuestionId())
        .questionBankId(question.getQuestionBankId())
        .createdAt(question.getCreatedAt())
        .updatedAt(question.getUpdatedAt())
        .build();
  }

  private UUID getCurrentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      return UUID.fromString(jwtAuth.getToken().getSubject());
    }
    throw new IllegalStateException("Authentication is not JwtAuthenticationToken");
  }

  private boolean hasRole(String roleName) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return false;
    String prefixed = "ROLE_" + roleName;
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(a -> a.equals(roleName) || a.equals(prefixed));
  }

  private void validateTeacherRole(UUID userId) {
    if (!hasRole(PredefinedRole.TEACHER_ROLE) && !hasRole(PredefinedRole.ADMIN_ROLE)) {
      User user =
          userRepository
              .findByIdWithRoles(userId)
              .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
      boolean isTeacher =
          user.getRoles().stream()
              .anyMatch(
                  role ->
                      PredefinedRole.TEACHER_ROLE.equals(role.getName())
                          || PredefinedRole.ADMIN_ROLE.equals(role.getName()));
      if (!isTeacher) {
        throw new AppException(ErrorCode.NOT_A_TEACHER);
      }
    }
  }

  private void validateOwnerOrAdmin(UUID ownerId, UUID currentUserId) {
    if (!ownerId.equals(currentUserId) && !hasRole(PredefinedRole.ADMIN_ROLE)) {
      throw new AppException(ErrorCode.ASSESSMENT_ACCESS_DENIED);
    }
  }

  private void validateDates(Instant startDate, Instant endDate) {
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
      throw new AppException(ErrorCode.ASSESSMENT_INVALID_SCHEDULE);
    }
  }

  private BigDecimal safeTotalPoints(Double raw) {
    if (raw == null) return BigDecimal.ZERO;
    return new BigDecimal(raw.toString());
  }

  /**
   * FIX: build a UUID → [questionCount, totalPoints*100, submissionCount] map from a single bulk
   * query so mapToResponse doesn't fire 3 extra queries per assessment (N+1 fix).
   * totalPoints is stored ×100 as long to avoid Double; divided back when building the response.
   */
  private Map<UUID, long[]> buildSummaryMap(Collection<UUID> ids) {
    Map<UUID, long[]> map = new HashMap<>();
    if (ids.isEmpty()) return map;
    List<Object[]> rows = assessmentRepository.findBulkSummaryByIds(ids);
    for (Object[] row : rows) {
      UUID aid = (UUID) row[0];
      long qCount = row[1] == null ? 0L : ((Number) row[1]).longValue();
      // row[2] is a Double from COALESCE(SUM(...))
      long pointsCents =
          row[2] == null
              ? 0L
              : new BigDecimal(row[2].toString()).multiply(BigDecimal.valueOf(100)).longValue();
      long subCount = row[3] == null ? 0L : ((Number) row[3]).longValue();
      map.put(aid, new long[] {qCount, pointsCents, subCount});
    }
    return map;
  }

  /** Single-assessment response — fires individual queries (used by write methods). */
  private AssessmentResponse mapToResponse(Assessment assessment) {
    Long totalQuestions = assessmentRepository.countQuestionsByAssessmentId(assessment.getId());
    BigDecimal totalPoints =
        safeTotalPoints(assessmentRepository.calculateTotalPoints(assessment.getId()));
    Long submissionCount = assessmentRepository.countSubmissionsByAssessmentId(assessment.getId());

    String teacherName =
      userRepository.findById(assessment.getTeacherId()).map(User::getFullName).orElse(UNKNOWN_NAME);

    return buildResponse(assessment, teacherName, totalQuestions, totalPoints, submissionCount);
  }

  /** Bulk-list response — uses pre-fetched summary row to avoid N+1. */
  private AssessmentResponse mapToResponseWithSummary(
      Assessment assessment, String teacherName, long[] summary) {
    long qCount = summary[0];
    BigDecimal totalPoints =
        BigDecimal.valueOf(summary[1]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    long subCount = summary[2];

    return buildResponse(assessment, teacherName, qCount, totalPoints, subCount);
  }

  private AssessmentResponse buildResponse(
      Assessment assessment,
      String teacherName,
      long totalQuestions,
      BigDecimal totalPoints,
      long submissionCount) {

    List<UUID> lessonIds =
        assessmentLessonRepository.findLessonIdsByAssessmentId(assessment.getId());
    Map<UUID, String> lessonTitleById =
        lessonRepository.findByIdInAndNotDeleted(lessonIds).stream()
            .collect(Collectors.toMap(Lesson::getId, Lesson::getTitle));
    List<String> lessonTitles =
      lessonIds.stream().map(id -> lessonTitleById.getOrDefault(id, UNKNOWN_NAME)).toList();

    // BUG FIX #4: Build detailed lesson information with subject and grade
    List<AssessmentResponse.AssessmentLessonInfo> lessons = 
        lessonRepository.findByIdInAndNotDeleted(lessonIds).stream()
            .map(lesson -> {
              String chapterName = "Unknown Chapter";
              String subjectName = null;
              Integer gradeLevel = null;
              String gradeName = null;
              
              // Get chapter information
              if (lesson.getChapterId() != null) {
                Chapter chapter = chapterRepository.findById(lesson.getChapterId()).orElse(null);
                if (chapter != null) {
                  chapterName = chapter.getTitle();
                  
                  // Get subject and grade information from chapter
                  if (chapter.getSubjectId() != null) {
                    Subject subject = subjectRepository.findById(chapter.getSubjectId()).orElse(null);
                    if (subject != null) {
                      subjectName = subject.getName();
                      
                      // Get grade from subject's school grade
                      if (subject.getSchoolGrade() != null) {
                        gradeLevel = subject.getSchoolGrade().getGradeLevel();
                        gradeName = subject.getSchoolGrade().getName();
                      }
                    }
                  }
                }
              }
              
              return AssessmentResponse.AssessmentLessonInfo.builder()
                  .lessonId(lesson.getId())
                  .lessonName(lesson.getTitle())
                  .chapterName(chapterName)
                  .orderIndex(lesson.getOrderIndex())
                  .subjectName(subjectName)
                  .gradeLevel(gradeLevel)
                  .gradeName(gradeName)
                  .build();
            })
            .toList();

    // Get ExamMatrix info if exists
    String examMatrixName = null;
    Integer examMatrixGradeLevel = null;
    if (assessment.getExamMatrixId() != null) {
      examMatrixRepository.findByIdAndNotDeleted(assessment.getExamMatrixId())
          .ifPresent(matrix -> {});
      examMatrixName = examMatrixRepository.findByIdAndNotDeleted(assessment.getExamMatrixId())
          .map(ExamMatrix::getName).orElse(null);
      examMatrixGradeLevel = examMatrixRepository.findByIdAndNotDeleted(assessment.getExamMatrixId())
          .map(ExamMatrix::getGradeLevel).orElse(null);
    }

    return AssessmentResponse.builder()
        .id(assessment.getId())
        .teacherId(assessment.getTeacherId())
        .teacherName(teacherName)
        .lessonIds(lessonIds)
        .lessonTitles(lessonTitles)
        .lessons(lessons)  // BUG FIX #4: Add detailed lesson info
        .title(assessment.getTitle())
        .description(assessment.getDescription())
        .assessmentType(assessment.getAssessmentType())
        .timeLimitMinutes(assessment.getTimeLimitMinutes())
        .passingScore(assessment.getPassingScore())
        .startDate(assessment.getStartDate())
        .endDate(assessment.getEndDate())
        .randomizeQuestions(assessment.getRandomizeQuestions())
        .showCorrectAnswers(assessment.getShowCorrectAnswers())
        .assessmentMode(assessment.getAssessmentMode())
        .examMatrixId(assessment.getExamMatrixId())
        .examMatrixName(examMatrixName)
        .examMatrixGradeLevel(examMatrixGradeLevel)
        .allowMultipleAttempts(assessment.getAllowMultipleAttempts())
        .maxAttempts(assessment.getMaxAttempts())
        .attemptScoringPolicy(assessment.getAttemptScoringPolicy())
        .showScoreImmediately(assessment.getShowScoreImmediately())
        .status(assessment.getStatus())
        .totalQuestions(totalQuestions)
        .totalPoints(totalPoints)
        .submissionCount(submissionCount)
        .createdAt(assessment.getCreatedAt())
        .updatedAt(assessment.getUpdatedAt())
        .sourcePdfPath(assessment.getSourcePdfPath())
        .sourcePdfOriginalName(assessment.getSourcePdfOriginalName())
        .pdfImportPages(parsePdfImportPages(assessment.getPdfImportPagesJson()))
        .pdfImportDocument(resolvePdfImportDocumentForResponse(assessment))
        .pdfImportMetadata(resolvePdfImportMetadataForResponse(assessment))
        .build();
  }

  private com.fptu.math_master.dto.response.AssessmentPdfImportMetadataResponse
      resolvePdfImportMetadataForResponse(Assessment assessment) {
    var parsed =
        AssessmentPdfImportMetadataHelper.parseMetadataJson(
            assessment.getPdfImportMetadataJson(), objectMapper);
    if (parsed != null) {
      return parsed;
    }
    if (!StringUtils.hasText(assessment.getSourcePdfPath())) {
      return null;
    }
    return AssessmentPdfImportMetadataHelper.buildFallbackFromAssessment(assessment);
  }

  private AssessmentPdfImportDocumentResponse resolvePdfImportDocumentForResponse(
      Assessment assessment) {
    String json = assessment.getPdfImportDocumentJson();
    if (StringUtils.hasText(json)) {
      return AssessmentPdfImportDocumentHelper.parseDocumentJson(json, objectMapper);
    }
    List<PdfImportPageDto> pages = parsePdfImportPages(assessment.getPdfImportPagesJson());
    if (pages.isEmpty()) {
      return null;
    }
    return AssessmentPdfImportDocumentHelper.seedFromPages(pages, null);
  }

  private static Integer readMetaInt(Map<String, Object> meta, String key) {
    if (meta == null || !meta.containsKey(key)) {
      return null;
    }
    Object v = meta.get(key);
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String readMetaString(Map<String, Object> meta, String key) {
    if (meta == null || !meta.containsKey(key)) {
      return null;
    }
    Object v = meta.get(key);
    return v != null ? String.valueOf(v) : null;
  }

  private static Boolean readMetaBoolean(Map<String, Object> meta, String key) {
    if (meta == null || !meta.containsKey(key)) {
      return null;
    }
    Object v = meta.get(key);
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }

  private List<com.fptu.math_master.dto.response.pdfimport.PdfImportPageDto> parsePdfImportPages(
      String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
      com.fasterxml.jackson.databind.JsonNode pages = root.get("pages");
      if (pages == null || !pages.isArray()) {
        return List.of();
      }
      List<com.fptu.math_master.dto.response.pdfimport.PdfImportPageDto> out = new ArrayList<>();
      for (com.fasterxml.jackson.databind.JsonNode node : pages) {
        out.add(
            com.fptu.math_master.dto.response.pdfimport.PdfImportPageDto.builder()
                .pageNumber(
                    node.has("pageNumber") ? node.get("pageNumber").asInt() : null)
                .text(node.has("text") ? node.get("text").asText() : null)
                .sectionLabel(
                    node.has("sectionLabel") ? node.get("sectionLabel").asText() : null)
                .confidence(
                    node.has("confidence") ? node.get("confidence").asDouble() : null)
                .build());
      }
      return out;
    } catch (Exception ex) {
      log.warn("Invalid pdf_import_pages_json: {}", ex.getMessage());
      return List.of();
    }
  }

  private ExamMatrix validateAndGetAccessibleMatrix(UUID matrixId, UUID currentUserId) {
    ExamMatrix matrix =
        examMatrixRepository
            .findByIdAndNotDeleted(matrixId)
            .orElseThrow(() -> new AppException(ErrorCode.EXAM_MATRIX_NOT_FOUND));
    if (!matrix.getTeacherId().equals(currentUserId) && !hasRole(PredefinedRole.ADMIN_ROLE)) {
      throw new AppException(ErrorCode.ASSESSMENT_ACCESS_DENIED);
    }
    return matrix;
  }

  private List<UUID> normalizeLessonIds(List<UUID> lessonIds) {
    return new java.util.ArrayList<>(new LinkedHashSet<>(lessonIds));
  }

  private void validateLessonSelectionForMatrix(UUID matrixId, List<UUID> lessonIds) {
    Set<UUID> existingLessonIds =
        new java.util.HashSet<>(lessonRepository.findExistingIdsByIds(lessonIds));
    if (existingLessonIds.size() != lessonIds.size()) {
      throw new AppException(ErrorCode.LESSON_NOT_FOUND);
    }

    Set<UUID> matrixLessonIds =
        new java.util.HashSet<>(examMatrixRowRepository.findDistinctLessonIdsByExamMatrixId(matrixId));

    // Bank-only matrices do not have template-linked lessons, so lesson constraints are skipped.
    if (matrixLessonIds.isEmpty()) {
      return;
    }

    if (!matrixLessonIds.containsAll(lessonIds)) {
      throw new AppException(ErrorCode.ASSESSMENT_LESSON_NOT_IN_MATRIX);
    }
  }

  private void autoMapQuestionsFromMatrixOnCreate(UUID assessmentId, UUID matrixId) {
    if (assessmentQuestionRepository.findMaxOrderIndex(assessmentId) != null) {
      return;
    }

    ExamMatrix matrix =
        examMatrixRepository
            .findByIdAndNotDeleted(matrixId)
            .orElseThrow(() -> new AppException(ErrorCode.EXAM_MATRIX_NOT_FOUND));

    if (!isMatrixReusableForGeneration(matrix)) {
      return;
    }

    List<ExamMatrixBankMapping> bankMappings =
      examMatrixBankMappingRepository.findByExamMatrixIdOrderByCreatedAt(matrixId);

    if (bankMappings.isEmpty()) {
      return;
    }

    GenerateAssessmentQuestionsRequest generateRequest =
        GenerateAssessmentQuestionsRequest.builder()
            .examMatrixId(matrixId)
            .reuseApprovedQuestions(true)
            .build();

    generateQuestionsFromMatrixInternal(assessmentId, generateRequest);
  }

  private void syncAssessmentLessons(UUID assessmentId, List<UUID> lessonIds) {
    assessmentLessonRepository.deleteByAssessmentId(assessmentId);
    List<AssessmentLesson> links =
        lessonIds.stream()
            .map(
                lessonId ->
                    AssessmentLesson.builder()
                        .assessmentId(assessmentId)
                        .lessonId(lessonId)
                        .build())
            .toList();
    assessmentLessonRepository.saveAll(links);
  }

  @Override
  @Transactional
  public AssessmentResponse generateAssessmentFromMatrix(
      GenerateAssessmentQuestionsRequest request) {
    log.info("Auto-generating assessment from matrix: {}", request.getExamMatrixId());

    UUID currentUserId = getCurrentUserId();
    validateTeacherRole(currentUserId);

    // Load and validate matrix
    ExamMatrix matrix =
        examMatrixRepository
            .findByIdAndNotDeleted(request.getExamMatrixId())
            .orElseThrow(() -> new AppException(ErrorCode.EXAM_MATRIX_NOT_FOUND));

    if (!isMatrixReusableForGeneration(matrix)) {
      throw new AppException(ErrorCode.EXAM_MATRIX_NOT_FOUND);
    }

    // Auto-create assessment with matrix name
    Assessment assessment =
        Assessment.builder()
            .teacherId(currentUserId)
            .title(matrix.getName() != null ? matrix.getName() : "Auto-Generated Assessment")
            .description("Được tạo tự động từ ma trận: " + matrix.getName())
            .assessmentType(com.fptu.math_master.enums.AssessmentType.QUIZ)
            .status(AssessmentStatus.DRAFT)
            .randomizeQuestions(false)
            .showCorrectAnswers(false)
            .assessmentMode(com.fptu.math_master.enums.AssessmentMode.MATRIX_BASED)
            .allowMultipleAttempts(false)
            .showScoreImmediately(true)
            .examMatrixId(matrix.getId())
            .build();

    Assessment savedAssessment = assessmentRepository.save(assessment);
    log.info("Created assessment {} from matrix {}", savedAssessment.getId(), matrix.getId());

    // Generate questions from matrix using strict bank-based selection.
    generateQuestionsFromMatrixInternal(savedAssessment.getId(), request);

    // Return the created assessment with its questions
    return mapToResponse(savedAssessment);
  }

  @Override
  @Transactional
  public PercentageBasedGenerationResponse generateAssessmentByPercentage(
      GenerateAssessmentByPercentageRequest request) {
    log.info(
        "Generating assessment by percentage from matrix: {}, total questions: {}",
        request.getExamMatrixId(),
        request.getTotalQuestions());

    UUID currentUserId = getCurrentUserId();
    validateTeacherRole(currentUserId);

    // Validate percentages sum to 100
    double totalPercentage =
        request.getCognitiveLevelPercentages().values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
    if (Math.abs(totalPercentage - 100.0) > 0.01) {
      throw new AppException(ErrorCode.MATRIX_VALIDATION_FAILED);
    }

    // Load and validate matrix
    ExamMatrix matrix =
        examMatrixRepository
            .findByIdAndNotDeleted(request.getExamMatrixId())
            .orElseThrow(() -> new AppException(ErrorCode.EXAM_MATRIX_NOT_FOUND));

    // Matrix does NOT need to be APPROVED for percentage-based generation
    // This allows creating multiple assessments from the same matrix.

    // Matrix is a pure blueprint: prefer the bank set from the generation
    // request (multi-bank), fall back to the legacy single-bank field, then
    // to the matrix's stored default. Percentage-based selection still uses
    // the *first* resolved bank per cell because the underlying
    // countApprovedByBanksAndCognitiveLevel/findRandomApprovedByBanksAndCognitiveLevel
    // queries already accept a Collection on this code path.
    java.util.List<UUID> resolvedBankIds = new java.util.ArrayList<>();
    if (request.getQuestionBankIds() != null && !request.getQuestionBankIds().isEmpty()) {
      for (UUID b : request.getQuestionBankIds()) if (b != null) resolvedBankIds.add(b);
    } else if (request.getQuestionBankId() != null) {
      resolvedBankIds.add(request.getQuestionBankId());
    } else if (matrix.getQuestionBankId() != null) {
      resolvedBankIds.add(matrix.getQuestionBankId());
    }
    if (resolvedBankIds.isEmpty()) {
      throw new AppException(ErrorCode.QUESTION_BANK_REQUIRED);
    }

    // Existing repository contract for percentage flow expects a single bank
    // per call; keep the first as the canonical source and surface the rest
    // only via the matrix-driven flow above.
    UUID questionBankId = resolvedBankIds.get(0);

    log.info(
        "Using question bank(s) {} for percentage-based generation on matrix {} (primary={})",
        resolvedBankIds,
        matrix.getId(),
        questionBankId);

    // BUG-3 FIX: Load matrix parts to respect question type configuration
    // Note: This is a simplified implementation that doesn't use chapter-based selection
    // For full chapter-aware generation, use generateAssessmentFromMatrix instead
    List<com.fptu.math_master.entity.ExamMatrixPart> parts =
        examMatrixPartRepository.findByExamMatrixIdOrderByPartNumber(matrix.getId());

    if (parts.isEmpty()) {
      log.warn("Matrix {} has no parts configured, cannot generate assessment", matrix.getId());
      throw new AppException(ErrorCode.MATRIX_VALIDATION_FAILED);
    }

    // Calculate question count for each cognitive level
    Map<CognitiveLevel, Integer> questionCounts = new HashMap<>();
    List<CognitiveLevelDistributionResponse> distributionResponses = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    for (Map.Entry<CognitiveLevel, Double> entry :
        request.getCognitiveLevelPercentages().entrySet()) {
      CognitiveLevel level = entry.getKey();
      Double percentage = entry.getValue();

      // Calculate required count (round to nearest integer)
      int requiredCount = (int) Math.round((percentage / 100.0) * request.getTotalQuestions());
      questionCounts.put(level, requiredCount);

      // BUG-3 FIX: Check availability across all parts (sum of all question types)
      long availableCount = 0;
      for (com.fptu.math_master.entity.ExamMatrixPart part : parts) {
        availableCount += questionRepository.countApprovedByBankAndCognitiveAndType(
            questionBankId, level.name(), part.getQuestionType().name());
      }

      CognitiveLevelDistributionResponse distResponse =
          CognitiveLevelDistributionResponse.builder()
              .cognitiveLevel(level)
              .requestedPercentage(percentage)
              .requestedCount(requiredCount)
              .availableInBank((int) availableCount)
              .build();

      if (availableCount < requiredCount) {
        distResponse.setActualCount((int) availableCount);
        distResponse.setStatus("INSUFFICIENT");
        distResponse.setMessage(
            String.format(
                "Only %d questions available, but %d required", availableCount, requiredCount));
        warnings.add(
            String.format(
                "%s: Only %d/%d questions available",
                level.name(), availableCount, requiredCount));
      } else {
        distResponse.setActualCount(requiredCount);
        distResponse.setStatus("SUCCESS");
        distResponse.setMessage("Sufficient questions available");
      }

      distributionResponses.add(distResponse);
    }

    // Create assessment
    String assessmentTitle =
        request.getAssessmentTitle() != null && !request.getAssessmentTitle().isBlank()
            ? request.getAssessmentTitle()
            : matrix.getName() + " - " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.now());

    Assessment assessment =
        Assessment.builder()
            .teacherId(currentUserId)
            .title(assessmentTitle)
            .description(
                request.getAssessmentDescription() != null
                    ? request.getAssessmentDescription()
                    : "Generated from exam matrix: " + matrix.getName())
            .assessmentType(com.fptu.math_master.enums.AssessmentType.QUIZ)
            .status(AssessmentStatus.DRAFT)
            .randomizeQuestions(
                request.getRandomizeQuestions() != null ? request.getRandomizeQuestions() : false)
            .showCorrectAnswers(false)
            .assessmentMode(com.fptu.math_master.enums.AssessmentMode.MATRIX_BASED)
            .allowMultipleAttempts(false)
            .showScoreImmediately(true)
            .examMatrixId(matrix.getId())
            .timeLimitMinutes(request.getTimeLimitMinutes())
            .passingScore(
                request.getPassingScore() != null
                    ? BigDecimal.valueOf(request.getPassingScore())
                    : null)
            .build();

    Assessment savedAssessment = assessmentRepository.save(assessment);
    log.info("Created assessment {} from matrix {}", savedAssessment.getId(), matrix.getId());

    // BUG-3 FIX: Generate questions grouped by part → questionType, then by cognitiveLevel
    List<AssessmentQuestion> assessmentQuestions = new ArrayList<>();
    int orderIndex = 0;
    int totalGenerated = 0;
    BigDecimal totalPoints = BigDecimal.ZERO;

    // Distribute questions across parts proportionally
    int questionsPerPart = request.getTotalQuestions() / parts.size();
    int remainder = request.getTotalQuestions() % parts.size();

    for (int i = 0; i < parts.size(); i++) {
      com.fptu.math_master.entity.ExamMatrixPart part = parts.get(i);
      int partQuestionCount = questionsPerPart + (i < remainder ? 1 : 0);

      log.info(
          "Generating {} questions for Part {} ({})",
          partQuestionCount,
          part.getPartNumber(),
          part.getQuestionType().name());

      // For each cognitive level, select questions of the correct type
      for (Map.Entry<CognitiveLevel, Integer> entry : questionCounts.entrySet()) {
        CognitiveLevel level = entry.getKey();
        int totalCountForLevel = entry.getValue();

        if (totalCountForLevel == 0) continue;

        // Calculate count for this part proportionally
        int countForThisPart = (int) Math.round(
            (double) totalCountForLevel * partQuestionCount / request.getTotalQuestions());

        if (countForThisPart == 0) continue;

        // BUG-3 FIX: Select questions filtered by questionType
        List<Question> questions =
            questionRepository.findRandomApprovedByBankAndCognitiveAndType(
                questionBankId, level.name(), part.getQuestionType().name(), countForThisPart);

        log.info(
            "Selected {} questions for Part {} - {} - {} (requested: {})",
            questions.size(),
            part.getPartNumber(),
            part.getQuestionType().name(),
            level.name(),
            countForThisPart);

        // Create assessment questions
        for (Question question : questions) {
          BigDecimal points =
              question.getPoints() != null ? question.getPoints() : BigDecimal.valueOf(1.0);

          AssessmentQuestion aq =
              AssessmentQuestion.builder()
                  .assessmentId(savedAssessment.getId())
                  .questionId(question.getId())
                  .orderIndex(orderIndex++)
                  .pointsOverride(points)
                  .build();

          assessmentQuestions.add(aq);
          totalPoints = totalPoints.add(points);
          totalGenerated++;
        }
      }
    }

    // Save all assessment questions
    if (!assessmentQuestions.isEmpty()) {
      assessmentQuestionRepository.saveAll(assessmentQuestions);
    }

    log.info(
        "Generated {} questions for assessment {} with {} total points",
        totalGenerated,
        savedAssessment.getId(),
        totalPoints);

    // Build response
    return PercentageBasedGenerationResponse.builder()
        .assessmentId(savedAssessment.getId())
        .assessmentTitle(savedAssessment.getTitle())
        .totalQuestionsRequested(request.getTotalQuestions())
        .totalQuestionsGenerated(totalGenerated)
        .totalPoints(totalPoints.intValue())
        .distribution(distributionResponses)
        .warnings(warnings.isEmpty() ? null : warnings)
        .message(
            totalGenerated == request.getTotalQuestions()
                ? String.format(
                    "Successfully generated %d questions from question bank",
                    totalGenerated)
                : String.format(
                    "Generated %d/%d questions. Some cognitive levels had insufficient questions.",
                    totalGenerated, request.getTotalQuestions()))
        .success(totalGenerated > 0)
        .build();
  }

  @Override
  public List<AssessmentResponse> getAssessmentsByLessonId(UUID lessonId) {
    // Get all assessment IDs linked to this lesson
    List<UUID> assessmentIds = assessmentLessonRepository.findAssessmentIdsByLessonId(lessonId);

    if (assessmentIds.isEmpty()) {
      return List.of();
    }

    // Get all assessments
    List<Assessment> assessments = assessmentRepository.findByIdInAndNotDeleted(assessmentIds);

    // Map to response
    return assessments.stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void linkAssessmentToLesson(UUID assessmentId, UUID lessonId) {
    UUID currentUserId = SecurityUtils.getCurrentUserId();

    // Verify assessment exists and belongs to current user
    Assessment assessment = assessmentRepository.findByIdAndNotDeleted(assessmentId)
        .orElseThrow(() -> new AppException(ErrorCode.ASSESSMENT_NOT_FOUND));

    if (!assessment.getTeacherId().equals(currentUserId)) {
      throw new AppException(ErrorCode.ASSESSMENT_ACCESS_DENIED);
    }

    // Check if already linked
    List<UUID> existingLessonIds = assessmentLessonRepository.findLessonIdsByAssessmentId(assessmentId);
    if (existingLessonIds.contains(lessonId)) {
      return; // Already linked, do nothing
    }

    // Create link
    AssessmentLesson assessmentLesson = AssessmentLesson.builder()
        .assessmentId(assessmentId)
        .lessonId(lessonId)
        .build();

    assessmentLessonRepository.save(assessmentLesson);
    log.info("Linked assessment {} to lesson {} by user {}", assessmentId, lessonId, currentUserId);
  }

  @Override
  @Transactional
  public void unlinkAssessmentFromLesson(UUID assessmentId, UUID lessonId) {
    UUID currentUserId = SecurityUtils.getCurrentUserId();

    // Verify assessment exists and belongs to current user
    Assessment assessment = assessmentRepository.findByIdAndNotDeleted(assessmentId)
        .orElseThrow(() -> new AppException(ErrorCode.ASSESSMENT_NOT_FOUND));

    if (!assessment.getTeacherId().equals(currentUserId)) {
      throw new AppException(ErrorCode.ASSESSMENT_ACCESS_DENIED);
    }

    // Find and delete the link
    assessmentLessonRepository.findByAssessmentIdOrderByCreatedAt(assessmentId).stream()
        .filter(al -> al.getLessonId().equals(lessonId))
        .forEach(assessmentLessonRepository::delete);

    log.info("Unlinked assessment {} from lesson {} by user {}", assessmentId, lessonId, currentUserId);
  }

  // ─────────────────────────────── Batch operations ───────────────────────────────

  @Override
  @Transactional
  public List<AssessmentQuestionResponse> batchAddQuestions(
      UUID assessmentId, BatchAddQuestionsRequest request) {
    log.info("Batch-adding {} questions to assessment {}", request.getQuestionIds().size(), assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    // Question management is unrestricted on every assessment — see addQuestion.

    for (UUID questionId : request.getQuestionIds()) {
      questionRepository
          .findByIdAndNotDeleted(questionId)
          .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

      boolean isDuplicate =
          assessmentQuestionRepository.findByAssessmentIdAndQuestionId(assessmentId, questionId).isPresent();
      if (isDuplicate) {
        log.debug("Question {} already in assessment {}, skipping", questionId, assessmentId);
        continue;
      }

      Integer maxOrder = assessmentQuestionRepository.findMaxOrderIndex(assessmentId);
      int nextOrder = (maxOrder != null ? maxOrder : 0) + 1;

      AssessmentQuestion aq =
          AssessmentQuestion.builder()
              .assessmentId(assessmentId)
              .questionId(questionId)
              .orderIndex(nextOrder)
              .build();
      assessmentQuestionRepository.save(aq);
    }

    return getAssessmentQuestions(assessmentId);
  }

  @Override
  @Transactional
  public List<AssessmentQuestionResponse> batchUpdatePoints(
      UUID assessmentId, BatchUpdatePointsRequest request) {
    log.info("Batch-updating points for {} questions in assessment {}", request.getQuestions().size(), assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    // Per-question points are explicitly editable on every assessment regardless
    // of status / matrix / bank linkage. See the rationale in setPointsOverride.

    List<AssessmentQuestion> toSave = new java.util.ArrayList<>();
    for (BatchUpdatePointsRequest.QuestionPointItem item : request.getQuestions()) {
      AssessmentQuestion aq =
          assessmentQuestionRepository
              .findByAssessmentIdAndQuestionId(assessmentId, item.getId())
              .orElseThrow(() -> new AppException(ErrorCode.ASSESSMENT_QUESTION_NOT_FOUND));
      aq.setPointsOverride(item.getPoint());
      toSave.add(aq);
    }
    assessmentQuestionRepository.saveAll(toSave);

    return getAssessmentQuestions(assessmentId);
  }

  @Override
  @Transactional
  public List<AssessmentQuestionResponse> autoDistributePoints(
      UUID assessmentId, AutoDistributePointsRequest request) {
    log.info("Auto-distributing {} total points for assessment {}", request.getTotalPoints(), assessmentId);

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    // Per-question points are explicitly editable on every assessment regardless
    // of status / matrix / bank linkage. See the rationale in setPointsOverride.

    List<AssessmentQuestion> allAQs =
        assessmentQuestionRepository.findByAssessmentIdOrderByOrderIndex(assessmentId);
    if (allAQs.isEmpty()) {
      return java.util.Collections.emptyList();
    }

    java.math.BigDecimal totalPoints = request.getTotalPoints();
    java.util.Map<String, Integer> distribution =
        (request.getDistribution() != null) ? request.getDistribution() : java.util.Collections.emptyMap();

    // AUDIT-01 FIX: Batch-fetch ALL questions once instead of N+1 queries
    List<UUID> questionIds = allAQs.stream()
        .map(AssessmentQuestion::getQuestionId)
        .collect(java.util.stream.Collectors.toList());
    
    java.util.Map<UUID, Question> questionCache = new java.util.HashMap<>();
    for (Question q : questionRepository.findAllById(questionIds)) {
      questionCache.put(q.getId(), q);
    }

    // ISSUE-02: Calculate effective "question weight" — TF questions have 4 clauses, so weight = 4
    // Other questions weight = 1
    java.util.Map<UUID, Integer> questionWeights = new java.util.HashMap<>();
    for (AssessmentQuestion aq : allAQs) {
      Question q = questionCache.get(aq.getQuestionId());
      if (q != null && q.getQuestionType() == QuestionType.TRUE_FALSE) {
        questionWeights.put(aq.getQuestionId(), 4); // 4 clauses
      } else {
        questionWeights.put(aq.getQuestionId(), 1);
      }
    }

    // Group AssessmentQuestion by cognitiveLevel of underlying question
    java.util.Map<String, List<AssessmentQuestion>> byLevel = new java.util.LinkedHashMap<>();
    List<AssessmentQuestion> unmatched = new java.util.ArrayList<>();

    for (AssessmentQuestion aq : allAQs) {
      Question q = questionCache.get(aq.getQuestionId());
      if (q == null) {
        throw new AppException(ErrorCode.QUESTION_NOT_FOUND);
      }
      String levelKey =
          (q.getCognitiveLevel() != null) ? q.getCognitiveLevel().name() : null;
      if (levelKey != null && distribution.containsKey(levelKey)) {
        byLevel.computeIfAbsent(levelKey, k -> new java.util.ArrayList<>()).add(aq);
      } else {
        unmatched.add(aq);
      }
    }

    // Calculate total allocated percentage
    int allocatedPct = distribution.values().stream().mapToInt(Integer::intValue).sum();
    int remainingPct = Math.max(0, 100 - allocatedPct);

    List<AssessmentQuestion> toSave = new java.util.ArrayList<>();

    // ISSUE-02: Distribute by weight for each cognitive level group
    for (java.util.Map.Entry<String, Integer> entry : distribution.entrySet()) {
      List<AssessmentQuestion> group = byLevel.getOrDefault(entry.getKey(), java.util.Collections.emptyList());
      if (group.isEmpty()) continue;
      
      java.math.BigDecimal groupPoints =
          totalPoints.multiply(java.math.BigDecimal.valueOf(entry.getValue()))
              .divide(java.math.BigDecimal.valueOf(100), 10, java.math.RoundingMode.HALF_UP);
      
      // Calculate total weight for the group
      int totalWeight = group.stream()
          .mapToInt(aq -> questionWeights.getOrDefault(aq.getQuestionId(), 1))
          .sum();
      
      // Distribute by weight: TF gets 4× share, others get 1× share
      java.math.BigDecimal pointPerUnit = groupPoints.divide(
          java.math.BigDecimal.valueOf(totalWeight), 10, java.math.RoundingMode.HALF_UP);
      
      for (AssessmentQuestion aq : group) {
        int weight = questionWeights.getOrDefault(aq.getQuestionId(), 1);
        java.math.BigDecimal questionPoints = pointPerUnit
            .multiply(java.math.BigDecimal.valueOf(weight))
            .setScale(1, java.math.RoundingMode.HALF_UP);
        aq.setPointsOverride(questionPoints);
        toSave.add(aq);
      }
    }

    // ISSUE-02: Distribute remaining points to unmatched questions by weight
    if (!unmatched.isEmpty() && remainingPct > 0) {
      java.math.BigDecimal remainingPoints =
          totalPoints.multiply(java.math.BigDecimal.valueOf(remainingPct))
              .divide(java.math.BigDecimal.valueOf(100), 10, java.math.RoundingMode.HALF_UP);
      
      int totalUnmatchedWeight = unmatched.stream()
          .mapToInt(aq -> questionWeights.getOrDefault(aq.getQuestionId(), 1))
          .sum();
      
      java.math.BigDecimal pointPerUnit = remainingPoints.divide(
          java.math.BigDecimal.valueOf(totalUnmatchedWeight), 10, java.math.RoundingMode.HALF_UP);
      
      for (AssessmentQuestion aq : unmatched) {
        int weight = questionWeights.getOrDefault(aq.getQuestionId(), 1);
        java.math.BigDecimal questionPoints = pointPerUnit
            .multiply(java.math.BigDecimal.valueOf(weight))
            .setScale(1, java.math.RoundingMode.HALF_UP);
        aq.setPointsOverride(questionPoints);
        toSave.add(aq);
      }
    } else if (!unmatched.isEmpty()) {
      // No percentage reserved — split totalPoints equally among all questions by weight
      int totalAllWeight = allAQs.stream()
          .mapToInt(aq -> questionWeights.getOrDefault(aq.getQuestionId(), 1))
          .sum();
      
      java.math.BigDecimal pointPerUnit = totalPoints.divide(
          java.math.BigDecimal.valueOf(totalAllWeight), 10, java.math.RoundingMode.HALF_UP);
      
      for (AssessmentQuestion aq : unmatched) {
        int weight = questionWeights.getOrDefault(aq.getQuestionId(), 1);
        java.math.BigDecimal questionPoints = pointPerUnit
            .multiply(java.math.BigDecimal.valueOf(weight))
            .setScale(1, java.math.RoundingMode.HALF_UP);
        aq.setPointsOverride(questionPoints);
        toSave.add(aq);
      }
    }

    assessmentQuestionRepository.saveAll(toSave);
    return getAssessmentQuestions(assessmentId);
  }

  @Override
  @Transactional
  public DistributeAssessmentPointsResponse distributeQuestionPoints(
      UUID assessmentId, DistributeAssessmentPointsRequest request) {
    log.info(
        "Distributing points for assessment {} with strategy {} and totalPoints {}",
        assessmentId,
        request.getStrategy(),
        request.getTotalPoints());

    Assessment assessment = loadAssessmentOrThrow(assessmentId);
    validateOwnerOrAdmin(assessment.getTeacherId(), getCurrentUserId());

    if (assessment.getStatus() == AssessmentStatus.PUBLISHED) {
      throw new AppException(ErrorCode.ASSESSMENT_ALREADY_PUBLISHED);
    }

    if (request.getStrategy() != DistributeAssessmentPointsRequest.Strategy.EQUAL) {
      throw new AppException(ErrorCode.INVALID_REQUEST);
    }

    int scale = request.getScale() == null ? 1 : request.getScale();
    BigDecimal totalPoints = request.getTotalPoints().setScale(scale, RoundingMode.HALF_UP);

    List<AssessmentQuestion> allAQs =
        assessmentQuestionRepository.findByAssessmentIdOrderByOrderIndex(assessmentId);
    if (allAQs.isEmpty()) {
      return DistributeAssessmentPointsResponse.builder()
          .updated(0)
          .pointPerQuestion(BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP))
          .totalPoints(totalPoints)
          .scale(scale)
          .strategy(request.getStrategy().name())
          .build();
    }

    int questionCount = allAQs.size();
    BigDecimal pointPerQuestion =
        totalPoints.divide(BigDecimal.valueOf(questionCount), scale, RoundingMode.HALF_UP);

    BigDecimal factor = BigDecimal.TEN.pow(scale);
    long totalUnits = totalPoints.multiply(factor).longValueExact();
    long baseUnits = totalUnits / questionCount;
    int remainderUnits = (int) (totalUnits % questionCount);

    for (int i = 0; i < questionCount; i++) {
      long allocatedUnits = baseUnits + (i < remainderUnits ? 1 : 0);
      BigDecimal allocated =
          BigDecimal.valueOf(allocatedUnits).divide(factor, scale, RoundingMode.UNNECESSARY);
      allAQs.get(i).setPointsOverride(allocated);
    }

    assessmentQuestionRepository.saveAll(allAQs);

    return DistributeAssessmentPointsResponse.builder()
        .updated(questionCount)
        .pointPerQuestion(pointPerQuestion)
        .totalPoints(totalPoints)
        .scale(scale)
        .strategy(request.getStrategy().name())
        .build();
  }
}
