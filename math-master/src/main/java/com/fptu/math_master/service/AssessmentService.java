package com.fptu.math_master.service;

import com.fptu.math_master.dto.request.AutoDistributePointsRequest;
import com.fptu.math_master.dto.request.BatchAddQuestionsRequest;
import com.fptu.math_master.dto.request.BatchUpdatePointsRequest;
import com.fptu.math_master.dto.request.AddQuestionToAssessmentRequest;
import com.fptu.math_master.dto.request.AssessmentRequest;
import com.fptu.math_master.dto.request.DistributeAssessmentPointsRequest;
import com.fptu.math_master.dto.request.GenerateAssessmentByPercentageRequest;
import com.fptu.math_master.dto.request.GenerateAssessmentQuestionsRequest;
import com.fptu.math_master.dto.request.PointsOverrideRequest;
import com.fptu.math_master.dto.response.AssessmentGenerationResponse;
import com.fptu.math_master.dto.response.AssessmentQuestionResponse;
import com.fptu.math_master.dto.request.UpdateAssessmentPdfImportDocumentRequest;
import com.fptu.math_master.dto.response.AssessmentPdfImportDocumentResponse;
import com.fptu.math_master.dto.response.AssessmentResponse;
import com.fptu.math_master.dto.response.AssessmentSummary;
import com.fptu.math_master.dto.response.DistributeAssessmentPointsResponse;
import com.fptu.math_master.dto.response.PagedDataResponse;
import com.fptu.math_master.dto.response.PercentageBasedGenerationResponse;
import com.fptu.math_master.dto.response.QuestionResponse;
import com.fptu.math_master.enums.AssessmentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssessmentService {

  AssessmentResponse createAssessment(AssessmentRequest request);

  AssessmentResponse updateAssessment(UUID id, com.fptu.math_master.dto.request.UpdateAssessmentRequest request);

  AssessmentResponse setPointsOverride(UUID assessmentId, PointsOverrideRequest request);

  AssessmentResponse getAssessmentPreview(UUID id);

  /**
   * Stateless teacher preview submission. Grades the supplied answers in
   * memory using the same rules as student submissions, but does not persist
   * a Submission / Answer / QuizAttempt row and does not change the
   * assessment status.
   */
  com.fptu.math_master.dto.response.PreviewSubmitResponse previewSubmit(
      UUID assessmentId, com.fptu.math_master.dto.request.PreviewSubmitRequest request);

  AssessmentSummary getPublishSummary(UUID id);

  AssessmentResponse publishAssessment(UUID id);

  AssessmentResponse unpublishAssessment(UUID id);

  void deleteAssessment(UUID id);

  AssessmentResponse getAssessmentById(UUID id);

  /** Presigned URL for PDF uploaded via import (Cách 2). */
  com.fptu.math_master.dto.response.AssessmentSourcePdfUrlResponse getImportSourcePdfUrl(UUID id);

  /** Structured OCR blocks (câu hỏi / đáp án) for PDF-imported assessments. */
  AssessmentPdfImportDocumentResponse getPdfImportDocument(UUID id);

  AssessmentPdfImportDocumentResponse updatePdfImportDocument(
      UUID id, UpdateAssessmentPdfImportDocumentRequest request);

  AssessmentResponse updatePdfImportMetadata(
      UUID id, com.fptu.math_master.dto.request.UpdateAssessmentPdfImportMetadataRequest request);

  Page<AssessmentResponse> getMyAssessments(AssessmentStatus status, String search, Pageable pageable);

  List<AssessmentResponse> searchAssessmentsByName(String name, AssessmentStatus status);

  boolean canEditAssessment(UUID id);

  boolean canDeleteAssessment(UUID id);

  boolean canPublishAssessment(UUID id);

  AssessmentResponse closeAssessment(UUID id);

  AssessmentResponse reopenAssessment(UUID id);

  AssessmentResponse addQuestion(UUID assessmentId, AddQuestionToAssessmentRequest request);

  List<AssessmentQuestionResponse> getAssessmentQuestions(UUID assessmentId);

  List<AssessmentQuestionResponse> reorderQuestions(
      UUID assessmentId,
      com.fptu.math_master.dto.request.ReorderAssessmentQuestionsRequest request);

    PagedDataResponse<QuestionResponse> getAvailableQuestions(
            UUID assessmentId, String keyword, String tag, Pageable pageable);

  AssessmentResponse removeQuestion(UUID assessmentId, UUID questionId);

  /**
   * Generate assessment questions from exam matrix templates using AI.
   * Iterates through all template mappings in the matrix and generates questions.
   *
   * @param assessmentId Assessment ID
   * @param request Generation request with exam matrix ID and options
   * @return Response with count of generated questions
   */
  AssessmentGenerationResponse generateQuestionsFromMatrix(
      UUID assessmentId, GenerateAssessmentQuestionsRequest request);

  /**
   * Auto-generate assessment from exam matrix (simplified one-step flow).
   * Creates new assessment with name from matrix and generates all questions.
   *
   * @param request Generation request with exam matrix ID and options
   * @return Created assessment with generated questions
   */
  AssessmentResponse generateAssessmentFromMatrix(GenerateAssessmentQuestionsRequest request);

  /**
   * Generate assessment from exam matrix using percentage-based cognitive level distribution.
   * This allows creating multiple assessments from the same matrix without locking it.
   * Questions are randomly selected from the question bank based on cognitive level percentages.
   * 
   * @param request Contains matrix ID, total questions, and percentage distribution by cognitive level
   * @return Response with generated assessment details and distribution breakdown
   */
  PercentageBasedGenerationResponse generateAssessmentByPercentage(
      GenerateAssessmentByPercentageRequest request);

  /**
   * Get all assessments linked to a specific lesson.
   *
   * @param lessonId Lesson ID
   * @return List of assessments for this lesson
   */
  List<AssessmentResponse> getAssessmentsByLessonId(UUID lessonId);

  /**
   * Link an existing assessment to a lesson.
   *
   * @param assessmentId Assessment ID
   * @param lessonId Lesson ID
   */
  void linkAssessmentToLesson(UUID assessmentId, UUID lessonId);

  /**
   * Unlink an assessment from a lesson.
   *
   * @param assessmentId Assessment ID
   * @param lessonId Lesson ID
   */
  void unlinkAssessmentFromLesson(UUID assessmentId, UUID lessonId);

  /** Batch add questions to a direct assessment (no exam matrix). Skips duplicates. */
  List<AssessmentQuestionResponse> batchAddQuestions(
      UUID assessmentId, BatchAddQuestionsRequest request);

  /** Batch update points for questions in an assessment. Transactional. */
  List<AssessmentQuestionResponse> batchUpdatePoints(
      UUID assessmentId, BatchUpdatePointsRequest request);

  /**
   * Auto-distribute total points across questions by cognitive level percentages.
   * Questions not matching any distribution key are split evenly from remaining points.
   */
  List<AssessmentQuestionResponse> autoDistributePoints(
      UUID assessmentId, AutoDistributePointsRequest request);

  DistributeAssessmentPointsResponse distributeQuestionPoints(
      UUID assessmentId, DistributeAssessmentPointsRequest request);
}
