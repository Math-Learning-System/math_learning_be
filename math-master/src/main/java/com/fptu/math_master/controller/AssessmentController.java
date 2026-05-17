package com.fptu.math_master.controller;

import com.fptu.math_master.dto.request.AddQuestionToAssessmentRequest;
import com.fptu.math_master.dto.request.AutoDistributePointsRequest;
import com.fptu.math_master.dto.request.BatchAddQuestionsRequest;
import com.fptu.math_master.dto.request.BatchUpdatePointsRequest;
import com.fptu.math_master.dto.request.AssessmentRequest;
import com.fptu.math_master.dto.request.DistributeAssessmentPointsRequest;
import com.fptu.math_master.dto.request.GenerateAssessmentByPercentageRequest;
import com.fptu.math_master.dto.request.GenerateAssessmentQuestionsRequest;
import com.fptu.math_master.dto.request.PointsOverrideRequest;
import com.fptu.math_master.dto.request.UpdateAssessmentPdfImportDocumentRequest;
import com.fptu.math_master.configuration.properties.MinioProperties;
import com.fptu.math_master.dto.response.ApiResponse;
import com.fptu.math_master.dto.response.AssessmentPdfImportDocumentResponse;
import com.fptu.math_master.dto.response.BookPageImagePresignedUrlResponse;
import com.fptu.math_master.dto.response.BookPageImageResponse;
import com.fptu.math_master.exception.AppException;
import com.fptu.math_master.exception.ErrorCode;
import com.fptu.math_master.service.UploadService;
import com.fptu.math_master.dto.response.AssessmentGenerationResponse;
import com.fptu.math_master.dto.response.AssessmentQuestionResponse;
import com.fptu.math_master.dto.response.AssessmentResponse;
import com.fptu.math_master.dto.response.AssessmentSummary;
import com.fptu.math_master.dto.response.DistributeAssessmentPointsResponse;
import com.fptu.math_master.dto.response.PagedDataResponse;
import com.fptu.math_master.dto.response.PercentageBasedGenerationResponse;
import com.fptu.math_master.dto.response.QuestionResponse;
import com.fptu.math_master.enums.AssessmentStatus;
import com.fptu.math_master.dto.response.AssessmentImportResponse;
import com.fptu.math_master.dto.response.AssessmentSourcePdfUrlResponse;
import com.fptu.math_master.enums.AssessmentType;
import com.fptu.math_master.service.AssessmentImportService;
import com.fptu.math_master.service.AssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/assessments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class AssessmentController {

  AssessmentService assessmentService;
  AssessmentImportService assessmentImportService;
  com.fptu.math_master.service.PythonCrawlerClient pythonCrawlerClient;
  com.fptu.math_master.service.AssessmentImportConfigService assessmentImportConfigService;
  com.fptu.math_master.service.QuestionSelectionService questionSelectionService;
  UploadService uploadService;
  MinioProperties minioProperties;

  @GetMapping("/import-form-options")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Get form options for PDF assessment import",
      description = "Returns admin-configured school years and exam types for the import form.")
  public ApiResponse<com.fptu.math_master.dto.response.AssessmentImportFormOptionsResponse>
      getImportFormOptions() {
    return ApiResponse.<com.fptu.math_master.dto.response.AssessmentImportFormOptionsResponse>builder()
        .result(assessmentImportConfigService.getFormOptions())
        .build();
  }

  @PostMapping(value = "/pdf-info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "PDF page count for import wizard step 2")
  public ApiResponse<com.fptu.math_master.dto.response.AssessmentPdfInfoResponse> getPdfInfo(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "fileKey", required = false) String fileKey,
      @RequestParam(value = "draftId", required = false) String draftId) {
    return ApiResponse.<com.fptu.math_master.dto.response.AssessmentPdfInfoResponse>builder()
        .result(pythonCrawlerClient.getAssessmentPdfInfo(file, fileKey, draftId))
        .build();
  }

  @PostMapping(value = "/ocr-pdf-page", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "OCR one PDF page (Mathpix PDF API)",
      description =
          "Mathpix async PDF OCR (no Gemini). First page runs full PDF; later pages use cache.")
  public ApiResponse<com.fptu.math_master.dto.response.AssessmentPdfOcrPageResponse> ocrPdfPage(
      @RequestParam("file") MultipartFile file,
      @RequestParam("pageNumber") int pageNumber,
      @RequestParam(value = "fileKey", required = false) String fileKey,
      @RequestParam(value = "draftId", required = false) String draftId) {
    return ApiResponse.<com.fptu.math_master.dto.response.AssessmentPdfOcrPageResponse>builder()
        .result(pythonCrawlerClient.ocrAssessmentPdfPage(file, pageNumber, fileKey, draftId))
        .build();
  }

  @GetMapping("/pdf-import-draft/{draftId}")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "Load OCR wizard draft from MongoDB")
  public ApiResponse<com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse>
      getPdfImportDraft(@PathVariable String draftId) {
    return ApiResponse
        .<com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse>builder()
        .result(pythonCrawlerClient.getPdfImportDraft(draftId))
        .build();
  }

  @GetMapping("/pdf-import-draft")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "Load OCR wizard draft by file key")
  public ApiResponse<com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse>
      getPdfImportDraftByFileKey(@RequestParam("fileKey") String fileKey) {
    return ApiResponse
        .<com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse>builder()
        .result(pythonCrawlerClient.getPdfImportDraftByFileKey(fileKey))
        .build();
  }

  @PostMapping(value = "/import-from-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Import assessment from PDF",
      description =
          "Upload a PDF exam document. AI extracts questions and creates a DRAFT assessment "
              + "in DIRECT mode for teacher/admin review.")
  public ApiResponse<AssessmentImportResponse> importAssessmentFromPdf(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) String examTitle,
      @RequestParam(required = false) String schoolYear,
      @RequestParam(required = false) String department,
      @RequestParam(required = false) String examDate,
      @RequestParam(required = false) String examType,
      @RequestParam(required = false) String examScope,
      @RequestParam(required = false) String organizerName,
      @RequestParam(required = false) String organizerType,
      @RequestParam(required = false) String provinceCity,
      @RequestParam(required = false) String district,
      @RequestParam(required = false) String schoolName,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) UUID schoolGradeId,
      @RequestParam(required = false) UUID subjectId,
      @RequestParam(required = false) String contextHint,
      @RequestParam(required = false) UUID questionBankId,
      @RequestParam(required = false) AssessmentType assessmentType,
      @RequestParam(required = false) Integer timeLimitMinutes,
      @RequestParam(required = false) String pdfLayout,
      @RequestParam(required = false) String importContentMode,
      @RequestParam(required = false) String preExtractedJson) {
    log.info("REST request to import assessment from PDF: {}", file.getOriginalFilename());
    com.fptu.math_master.dto.request.PdfAssessmentImportFormInput form =
        com.fptu.math_master.dto.request.PdfAssessmentImportFormInput.builder()
            .examTitle(examTitle)
            .schoolYear(schoolYear)
            .department(department)
            .examDate(examDate)
            .examType(examType)
            .examScope(examScope)
            .organizerName(organizerName)
            .organizerType(organizerType)
            .provinceCity(provinceCity)
            .district(district)
            .schoolName(schoolName)
            .country(country)
            .schoolGradeId(schoolGradeId)
            .subjectId(subjectId)
            .contextHint(contextHint)
            .questionBankId(questionBankId)
            .assessmentType(assessmentType)
            .timeLimitMinutes(timeLimitMinutes)
            .pdfLayout(pdfLayout)
            .importContentMode(importContentMode)
            .preExtractedJson(preExtractedJson)
            .build();
    AssessmentImportResponse response = assessmentImportService.importAssessmentFromPdf(file, form);
    return ApiResponse.<AssessmentImportResponse>builder()
        .message(
            "Đã tạo đề nháp từ PDF với "
                + response.getQuestionsImported()
                + " câu hỏi. Vui lòng rà soát trước khi công khai.")
        .result(response)
        .build();
  }

  @GetMapping("/{id}/import-source-pdf-url")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Get presigned URL for import source PDF",
      description =
          "Returns a short-lived URL to view/download the original PDF stored on MinIO "
              + "when the assessment was created via PDF import (Cách 2).")
  public ApiResponse<AssessmentSourcePdfUrlResponse> getImportSourcePdfUrl(@PathVariable UUID id) {
    return ApiResponse.<AssessmentSourcePdfUrlResponse>builder()
        .result(assessmentService.getImportSourcePdfUrl(id))
        .build();
  }

  @GetMapping("/{id}/pdf-import-document")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "Get structured OCR blocks for PDF import review")
  public ApiResponse<AssessmentPdfImportDocumentResponse> getPdfImportDocument(@PathVariable UUID id) {
    return ApiResponse.<AssessmentPdfImportDocumentResponse>builder()
        .result(assessmentService.getPdfImportDocument(id))
        .build();
  }

  @PutMapping("/{id}/pdf-import-document")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "Save structured OCR blocks (câu hỏi / đáp án)")
  public ApiResponse<AssessmentPdfImportDocumentResponse> updatePdfImportDocument(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateAssessmentPdfImportDocumentRequest request) {
    return ApiResponse.<AssessmentPdfImportDocumentResponse>builder()
        .message("Đã lưu nội dung rà soát PDF.")
        .result(assessmentService.updatePdfImportDocument(id, request))
        .build();
  }

  @PutMapping("/{id}/pdf-import-metadata")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "Update PDF import wizard metadata (thông tin chi tiết)")
  public ApiResponse<AssessmentResponse> updatePdfImportMetadata(
      @PathVariable UUID id,
      @Valid @RequestBody
          com.fptu.math_master.dto.request.UpdateAssessmentPdfImportMetadataRequest request) {
    return ApiResponse.<AssessmentResponse>builder()
        .message("Đã cập nhật thông tin chi tiết.")
        .result(assessmentService.updatePdfImportMetadata(id, request))
        .build();
  }

  @PostMapping(value = "/{id}/import-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "Upload image for PDF import content block")
  public ApiResponse<BookPageImageResponse> uploadImportImage(
      @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new AppException(ErrorCode.INVALID_REQUEST);
    }
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new AppException(ErrorCode.INVALID_REQUEST);
    }
    assessmentService.getPdfImportDocument(id);

    String directory = "assessments/" + id + "/import-images";
    String objectKey =
        uploadService.uploadFile(file, directory, minioProperties.getOcrContentBucket());
    int slash = objectKey.lastIndexOf('/');
    String fileName = slash >= 0 ? objectKey.substring(slash + 1) : objectKey;
    String imageUrl = "/api/v1/assessments/" + id + "/import-images/" + fileName;
    return ApiResponse.<BookPageImageResponse>builder()
        .result(new BookPageImageResponse(imageUrl, objectKey))
        .build();
  }

  @GetMapping("/{id}/import-images/presigned")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "Presigned URL for PDF import block image")
  public ApiResponse<BookPageImagePresignedUrlResponse> getImportImagePresignedUrl(
      @PathVariable UUID id, @RequestParam("fileName") String fileName) {
    assessmentService.getPdfImportDocument(id);
    String safeName = sanitizeImportImageFileName(fileName);
    String key = "assessments/" + id + "/import-images/" + safeName;
    try {
      String url = uploadService.getPresignedUrl(key, minioProperties.getOcrContentBucket());
      return ApiResponse.<BookPageImagePresignedUrlResponse>builder()
          .result(new BookPageImagePresignedUrlResponse(url))
          .build();
    } catch (Exception ex) {
      log.warn("Presign failed for assessment {} import-image {}", id, safeName, ex);
      throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Could not generate image URL");
    }
  }

  @GetMapping("/{id}/import-images/{fileName:.+}")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(summary = "Serve uploaded PDF import block image")
  public ResponseEntity<byte[]> serveImportImage(
      @PathVariable UUID id, @PathVariable String fileName) {
    assessmentService.getPdfImportDocument(id);
    String safeName = sanitizeImportImageFileName(fileName);
    String key = "assessments/" + id + "/import-images/" + safeName;
    byte[] data = uploadService.downloadFile(key, minioProperties.getOcrContentBucket());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(guessImportImageContentType(safeName)))
        .cacheControl(CacheControl.noStore())
        .body(data);
  }

  private static String sanitizeImportImageFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      throw new AppException(ErrorCode.INVALID_REQUEST);
    }
    if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
      throw new AppException(ErrorCode.INVALID_REQUEST);
    }
    return fileName;
  }

  private static String guessImportImageContentType(String fileName) {
    String lower = fileName.toLowerCase();
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (lower.endsWith(".webp")) {
      return "image/webp";
    }
    if (lower.endsWith(".gif")) {
      return "image/gif";
    }
    return "application/octet-stream";
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Create a new assessment",
      description =
          "Teacher creates a new assessment (quiz/test/exam/homework). "
              + "Title is required (max 255 chars), description is optional. "
              + "Must select an examMatrixId and one or more lessonIds. "
              + "Selected lessonIds are validated to ensure they belong to the chosen matrix. "
              + "If matrix is APPROVED and already has mappings, questions are auto-generated and mapped to the assessment. "
              + "Can set type, time limit, passing score, schedule, and options. "
              + "Status is DRAFT by default. Redirects to Assessment Builder after creation.")
  public ApiResponse<AssessmentResponse> createAssessment(
      @Valid @RequestBody AssessmentRequest request) {
    log.info("REST request to create assessment: {}", request.getTitle());
    return ApiResponse.<AssessmentResponse>builder()
        .message("Assessment created successfully. Questions are auto-mapped when matrix mappings are available.")
        .result(assessmentService.createAssessment(request))
        .build();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Update assessment (full)",
      description =
          "Update assessment details. Same null-safe field handling as PATCH — "
              + "kept for backward compatibility. Prefer PATCH for partial edits.")
  public ApiResponse<AssessmentResponse> updateAssessment(
      @PathVariable UUID id, @Valid @RequestBody com.fptu.math_master.dto.request.UpdateAssessmentRequest request) {
    log.info("REST request to update assessment (PUT): {}", id);
    return ApiResponse.<AssessmentResponse>builder()
        .message("Assessment updated successfully")
        .result(assessmentService.updateAssessment(id, request))
        .build();
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Patch assessment (partial)",
      description =
          "Patch a subset of fields on an existing assessment. Any field omitted "
              + "(or sent as null) is left untouched, so callers can update e.g. "
              + "timeLimitMinutes or maxAttempts without re-sending the entire "
              + "settings object. Reuses the same null-safe service path as PUT.")
  public ApiResponse<AssessmentResponse> patchAssessment(
      @PathVariable UUID id,
      @Valid @RequestBody com.fptu.math_master.dto.request.UpdateAssessmentRequest request) {
    log.info("REST request to patch assessment: {}", id);
    return ApiResponse.<AssessmentResponse>builder()
        .message("Assessment patched successfully")
        .result(assessmentService.updateAssessment(id, request))
        .build();
  }

  @PatchMapping("/{assessmentId}/points-override")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Set points override for a question",
      description =
          "Teacher can override default points for a question in this specific assessment. "
              + "Default is null (uses question.points). Teacher can edit inline. "
              + "System recalculates total points automatically. "
              + "Example: Question default = 2.0, override in this assessment = 5.0 → student gets 5 points if correct.")
  public ApiResponse<AssessmentResponse> setPointsOverride(
      @PathVariable UUID assessmentId, @Valid @RequestBody PointsOverrideRequest request) {
    log.info(
        "REST request to set points override for assessment: {}, question: {}",
        assessmentId,
        request.getQuestionId());
    return ApiResponse.<AssessmentResponse>builder()
        .message("Points override updated successfully. Total points recalculated.")
        .result(assessmentService.setPointsOverride(assessmentId, request))
        .build();
  }

  @GetMapping("/{id}/preview")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Preview assessment as student",
      description =
          "Teacher can preview how the assessment will look to students. "
              + "Shows exact student UI, can test-take (doesn't save submission), displays timer if set. "
              + "Open in new tab/modal for best experience.")
  public ApiResponse<AssessmentResponse> getAssessmentPreview(@PathVariable UUID id) {
    log.info("REST request to preview assessment: {}", id);
    return ApiResponse.<AssessmentResponse>builder()
        .result(assessmentService.getAssessmentPreview(id))
        .build();
  }

  @PostMapping("/{id}/preview-submit")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Grade a teacher preview submission (stateless)",
      description =
          "Runs the full grading pipeline on the supplied answers in memory. "
              + "Does not create a Submission / Answer / QuizAttempt row, does not "
              + "count toward student stats or attempt limits, and does not change "
              + "the assessment status. Idempotent — teacher can call repeatedly to "
              + "test different answer sets.")
  public ApiResponse<com.fptu.math_master.dto.response.PreviewSubmitResponse> previewSubmit(
      @PathVariable UUID id,
      @Valid @RequestBody com.fptu.math_master.dto.request.PreviewSubmitRequest request) {
    log.info("REST request to grade preview submission for assessment: {}", id);
    return ApiResponse.<com.fptu.math_master.dto.response.PreviewSubmitResponse>builder()
        .result(assessmentService.previewSubmit(id, request))
        .build();
  }

  @GetMapping("/{id}/publish-summary")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Get publish summary for validation",
      description =
          "Get assessment summary before publishing with validation checks: "
              + "- At least 1 question required "
              + "- All questions must be valid "
              + "- Total points > 0 "
              + "- Schedule valid (if set) "
              + "Shows: total questions, total points, time limit, schedule. "
              + "Warning: Assessment cannot be edited after publishing.")
  public ApiResponse<AssessmentSummary> getPublishSummary(@PathVariable UUID id) {
    log.info("REST request to get publish summary for assessment: {}", id);
    return ApiResponse.<AssessmentSummary>builder()
        .result(assessmentService.getPublishSummary(id))
        .build();
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Publish assessment",
      description =
          "Publish assessment to make it available to students. "
              + "Validates: at least 1 question, all questions valid, total points > 0, valid schedule. "
              + "After publishing: Status = PUBLISHED, notifications sent to students (if assigned), "
              + "assessment cannot be edited (only unpublished if no submissions).")
  public ApiResponse<AssessmentResponse> publishAssessment(@PathVariable UUID id) {
    log.info("REST request to publish assessment: {}", id);
    return ApiResponse.<AssessmentResponse>builder()
        .message("Assessment published successfully! Students can now take this assessment.")
        .result(assessmentService.publishAssessment(id))
        .build();
  }

  @PostMapping("/{id}/unpublish")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Unpublish assessment",
      description =
          "Unpublish assessment to edit it. "
              + "Can only unpublish if no submissions exist. "
              + "If submissions exist → BLOCKED with error message. "
              + "After unpublishing: Status = DRAFT, students cannot access.")
  public ApiResponse<AssessmentResponse> unpublishAssessment(@PathVariable UUID id) {
    log.info("REST request to unpublish assessment: {}", id);
    return ApiResponse.<AssessmentResponse>builder()
        .message("Assessment unpublished successfully. You can now edit it.")
        .result(assessmentService.unpublishAssessment(id))
        .build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Delete assessment",
      description =
          "Delete assessment. Can only delete if Status = DRAFT and no submissions exist. "
              + "If has submissions → BLOCKED. Soft delete (sets deleted_at). "
              + "Questions are NOT deleted, only the assessment.")
  public ApiResponse<Void> deleteAssessment(@PathVariable UUID id) {
    log.info("REST request to delete assessment: {}", id);
    assessmentService.deleteAssessment(id);
    return ApiResponse.<Void>builder()
        .message("Assessment deleted successfully. Questions have been preserved.")
        .build();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'STUDENT')")
  @Operation(
      summary = "Get assessment by ID",
      description =
          "Get assessment details. Teachers/admins can view all, students can view published assessments.")
  public ApiResponse<AssessmentResponse> getAssessmentById(@PathVariable UUID id) {
    log.info("REST request to get assessment: {}", id);
    return ApiResponse.<AssessmentResponse>builder()
        .result(assessmentService.getAssessmentById(id))
        .build();
  }

  @GetMapping("/my")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "List my assessments",
      description =
          "Get all assessments created by current teacher. "
              + "Supports filtering by status (DRAFT/PUBLISHED/CLOSED) and search by title. "
              + "Pagination supported (default 20 items/page).")
  public ApiResponse<Page<AssessmentResponse>> getMyAssessments(
      @RequestParam(required = false) AssessmentStatus status,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt") String sortBy,
      @RequestParam(defaultValue = "DESC") String sortDirection) {

    Sort sort =
        sortDirection.equalsIgnoreCase("ASC")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();

    Pageable pageable = PageRequest.of(page, size, sort);

    return ApiResponse.<Page<AssessmentResponse>>builder()
        .result(assessmentService.getMyAssessments(status, search, pageable))
        .build();
  }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(
            summary = "Search assessments by name",
            description =
                    "Search assessments by name. Supports both query and name params for FE compatibility. "
                            + "Status filter supports PUBLIC (mapped to PUBLISHED).")
    public ApiResponse<List<AssessmentResponse>> searchAssessments(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status) {

        String keyword = name != null ? name : query;
        AssessmentStatus statusFilter = parseSearchStatus(status);

        return ApiResponse.<List<AssessmentResponse>>builder()
                .result(assessmentService.searchAssessmentsByName(keyword, statusFilter))
                .build();
    }

    private AssessmentStatus parseSearchStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        if ("PUBLIC".equalsIgnoreCase(status)) {
            return AssessmentStatus.PUBLISHED;
        }

        return AssessmentStatus.valueOf(status.trim().toUpperCase());
    }

  @GetMapping("/{id}/can-edit")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Check if user can edit assessment",
      description =
          "Verify if current user can edit the assessment (owner/admin and status = DRAFT).")
  public ApiResponse<Boolean> canEditAssessment(@PathVariable UUID id) {
    log.info("REST request to check edit permission for assessment: {}", id);
    return ApiResponse.<Boolean>builder().result(assessmentService.canEditAssessment(id)).build();
  }

  @GetMapping("/{id}/can-delete")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Check if user can delete assessment",
      description =
          "Verify if current user can delete the assessment (owner/admin, DRAFT, no submissions).")
  public ApiResponse<Boolean> canDeleteAssessment(@PathVariable UUID id) {
    log.info("REST request to check delete permission for assessment: {}", id);
    return ApiResponse.<Boolean>builder().result(assessmentService.canDeleteAssessment(id)).build();
  }

  @GetMapping("/{id}/can-publish")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Check if user can publish assessment",
      description =
          "Verify if current user can publish the assessment (owner/admin, DRAFT, has questions).")
  public ApiResponse<Boolean> canPublishAssessment(@PathVariable UUID id) {
    log.info("REST request to check publish permission for assessment: {}", id);
    return ApiResponse.<Boolean>builder()
        .result(assessmentService.canPublishAssessment(id))
        .build();
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Close assessment",
      description = "Permanently close a PUBLISHED assessment. No further attempts are allowed.")
  public ApiResponse<AssessmentResponse> closeAssessment(@PathVariable UUID id) {
    log.info("REST request to close assessment: {}", id);
    return ApiResponse.<AssessmentResponse>builder()
        .message("Assessment closed successfully.")
        .result(assessmentService.closeAssessment(id))
        .build();
  }

  @PostMapping("/{id}/reopen")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Reopen a closed assessment",
      description =
          "Reopen a CLOSED assessment by flipping its status back to PUBLISHED so students can attempt it again.")
  public ApiResponse<AssessmentResponse> reopenAssessment(@PathVariable UUID id) {
    log.info("REST request to reopen assessment: {}", id);
    return ApiResponse.<AssessmentResponse>builder()
        .message("Assessment reopened successfully.")
        .result(assessmentService.reopenAssessment(id))
        .build();
  }

  @PostMapping("/{assessmentId}/questions")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Add question to assessment",
      description = "Add an existing question to a non-matrix DRAFT assessment.")
  public ApiResponse<AssessmentResponse> addQuestion(
      @PathVariable UUID assessmentId, @Valid @RequestBody AddQuestionToAssessmentRequest request) {
    log.info(
        "REST request to add question {} to assessment {}", request.getQuestionId(), assessmentId);
    return ApiResponse.<AssessmentResponse>builder()
        .message("Question added to assessment.")
        .result(assessmentService.addQuestion(assessmentId, request))
        .build();
  }

    @GetMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(
            summary = "Get assessment questions",
            description = "Get all questions in an assessment ordered by orderIndex.")
    public ApiResponse<List<AssessmentQuestionResponse>> getAssessmentQuestions(@PathVariable UUID id) {
        log.info("REST request to get questions for assessment: {}", id);
        return ApiResponse.<List<AssessmentQuestionResponse>>builder()
                .result(assessmentService.getAssessmentQuestions(id))
                .build();
    }

  @PatchMapping("/{id}/questions/reorder")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Batch reorder assessment questions",
      description =
          "Bulk update orderIndex for every question in the assessment in a single transaction. "
              + "Body must include every question currently in the assessment. "
              + "Returns the updated question list ordered by the new orderIndex.")
  public ApiResponse<List<AssessmentQuestionResponse>> reorderAssessmentQuestions(
      @PathVariable UUID id,
      @jakarta.validation.Valid @RequestBody
          com.fptu.math_master.dto.request.ReorderAssessmentQuestionsRequest request) {
    log.info("REST request to reorder questions for assessment: {}", id);
    return ApiResponse.<List<AssessmentQuestionResponse>>builder()
        .message("Assessment questions reordered successfully.")
        .result(assessmentService.reorderQuestions(id, request))
        .build();
  }

  @GetMapping("/{id}/available-questions")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Get paged available questions for assessment",
      description =
          "Get paged question list that can be added to assessment. "
              + "Already-added questions are excluded. Supports keyword/tag filters.")
  public ApiResponse<PagedDataResponse<QuestionResponse>> getAvailableQuestions(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String tag) {
    log.info(
        "REST request to get available questions for assessment {} with page={}, size={}, keyword={}, tag={}",
        id,
        page,
        size,
        keyword,
        tag);

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    return ApiResponse.<PagedDataResponse<QuestionResponse>>builder()
        .message("Available questions retrieved successfully")
        .result(assessmentService.getAvailableQuestions(id, keyword, tag, pageable))
        .build();
  }

  @DeleteMapping("/{assessmentId}/questions/{questionId}")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Remove question from assessment",
      description = "Remove a question from a non-matrix DRAFT assessment.")
  public ApiResponse<AssessmentResponse> removeQuestion(
      @PathVariable UUID assessmentId, @PathVariable UUID questionId) {
    log.info("REST request to remove question {} from assessment {}", questionId, assessmentId);
    return ApiResponse.<AssessmentResponse>builder()
        .message("Question removed from assessment.")
        .result(assessmentService.removeQuestion(assessmentId, questionId))
        .build();
  }

  @PostMapping("/validate-bank-coverage")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Validate that the chosen banks cover the matrix's per-cell requirements",
      description =
          "Pre-flight check before assessment generation. Returns one entry per matrix cell "
              + "(chapter × cognitive level × question type) with the required count vs the "
              + "approved-question count available across the chosen bank set. The FE renders "
              + "shortage rows directly (e.g. \"Bank thiếu Chương 2 – Vận dụng cao: cần 3, có 0\").")
  public com.fptu.math_master.dto.response.ApiResponse<
          com.fptu.math_master.dto.response.BankCoverageResponse>
      validateBankCoverage(
          @Valid @RequestBody
              com.fptu.math_master.dto.request.ValidateBankCoverageRequest request) {
    log.info(
        "REST request to validate bank coverage for matrix={} banks={}",
        request.getExamMatrixId(),
        request.getQuestionBankIds());
    com.fptu.math_master.service.QuestionSelectionService.CoverageReport report =
        questionSelectionService.computeCoverage(
            request.getExamMatrixId(), request.getQuestionBankIds());

    java.util.List<com.fptu.math_master.dto.response.BankCoverageResponse.CoverageCell> cells =
        new java.util.ArrayList<>();
    for (com.fptu.math_master.service.QuestionSelectionService.Gap gap : report.gaps()) {
      cells.add(
          com.fptu.math_master.dto.response.BankCoverageResponse.CoverageCell.builder()
              .chapterId(gap.chapterId())
              .chapterTitle(gap.chapterTitle())
              .cognitiveLevel(gap.cognitiveLevel())
              .questionType(gap.questionType())
              .required(gap.required())
              .available(gap.available())
              .build());
    }

    return com.fptu.math_master.dto.response.ApiResponse
        .<com.fptu.math_master.dto.response.BankCoverageResponse>builder()
        .message(
            report.ok()
                ? "Banks satisfy the matrix requirements."
                : "Selected banks do not cover all matrix cells; see gaps in response.")
        .result(
            com.fptu.math_master.dto.response.BankCoverageResponse.builder()
                .ok(report.ok())
                .cells(cells)
                .build())
        .build();
  }

  @PostMapping("/generate-from-matrix")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Generate assessment from exam matrix (simplified)",
      description =
          "One-step assessment generation: auto-creates assessment and generates questions from exam matrix. "
              + "Only requires examMatrixId. Automatically names assessment from matrix name. "
              + "Perfect for quick assessment creation with minimal input.")
  public ApiResponse<AssessmentResponse> generateAssessmentFromMatrix(
      @Valid @RequestBody GenerateAssessmentQuestionsRequest request) {
    log.info("REST request to auto-generate assessment from matrix: {}", request.getExamMatrixId());
    return ApiResponse.<AssessmentResponse>builder()
        .message("Assessment generated successfully from exam matrix with all questions.")
        .result(assessmentService.generateAssessmentFromMatrix(request))
        .build();
  }

  @PostMapping("/generate-by-percentage")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Generate assessment using percentage-based cognitive level distribution",
      description =
          "Create assessment from exam matrix using percentage distribution for cognitive levels. "
              + "Specify total questions and percentage for each level (NHAN_BIET, THONG_HIEU, VAN_DUNG, VAN_DUNG_CAO). "
              + "Questions are randomly selected from all question banks in the matrix. "
              + "Matrix is NOT locked, allowing multiple assessments from the same matrix. "
              + "Example: 40 questions with 25% NHAN_BIET (10q), 35% THONG_HIEU (14q), 30% VAN_DUNG (12q), 10% VAN_DUNG_CAO (4q).")
  public ApiResponse<PercentageBasedGenerationResponse> generateAssessmentByPercentage(
      @Valid @RequestBody GenerateAssessmentByPercentageRequest request) {
    log.info(
        "REST request to generate assessment by percentage from matrix: {}, total questions: {}",
        request.getExamMatrixId(),
        request.getTotalQuestions());
    return ApiResponse.<PercentageBasedGenerationResponse>builder()
        .message("Assessment generated successfully using percentage-based distribution.")
        .result(assessmentService.generateAssessmentByPercentage(request))
        .build();
  }

  @PostMapping("/{assessmentId}/generate")
  @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
  @Operation(
      summary = "Generate assessment questions from exam matrix",
      description =
          "Generate questions from exam matrix templates using AI. "
              + "Iterates through all template mappings in the matrix and generates required number of questions. "
              + "Can optionally reuse previously approved questions.")
  public ApiResponse<AssessmentGenerationResponse> generateQuestionsFromMatrix(
      @PathVariable UUID assessmentId,
      @Valid @RequestBody GenerateAssessmentQuestionsRequest request) {
    log.info(
        "REST request to generate questions from matrix {} for assessment {}",
        request.getExamMatrixId(),
        assessmentId);
    return ApiResponse.<AssessmentGenerationResponse>builder()
        .message("Questions generated successfully from exam matrix.")
        .result(assessmentService.generateQuestionsFromMatrix(assessmentId, request))
        .build();
  }

  @GetMapping("/lessons/{lessonId}/assessments")
  public ApiResponse<List<AssessmentResponse>> getAssessmentsByLesson(
      @PathVariable UUID lessonId) {
    log.info("GET /api/v1/assessments/lessons/{}/assessments", lessonId);
    List<AssessmentResponse> assessments = assessmentService.getAssessmentsByLessonId(lessonId);
    return ApiResponse.<List<AssessmentResponse>>builder()
        .code(1000)
        .message("Get assessments by lesson successfully")
        .result(assessments)
        .build();
  }

  @PostMapping("/{assessmentId}/lessons/{lessonId}")
  public ApiResponse<Void> linkAssessmentToLesson(
      @PathVariable UUID assessmentId,
      @PathVariable UUID lessonId) {
    log.info("POST /api/v1/assessments/{}/lessons/{}", assessmentId, lessonId);
    assessmentService.linkAssessmentToLesson(assessmentId, lessonId);
    return ApiResponse.<Void>builder()
        .code(1000)
        .message("Linked assessment to lesson successfully")
        .build();
  }

  @DeleteMapping("/{assessmentId}/lessons/{lessonId}")
  public ApiResponse<Void> unlinkAssessmentFromLesson(
      @PathVariable UUID assessmentId,
      @PathVariable UUID lessonId) {
    log.info("DELETE /api/v1/assessments/{}/lessons/{}", assessmentId, lessonId);
    assessmentService.unlinkAssessmentFromLesson(assessmentId, lessonId);
    return ApiResponse.<Void>builder()
        .code(1000)
        .message("Unlinked assessment from lesson successfully")
        .build();
  }

  // ─────────────────────────────── Batch endpoints ───────────────────────────────

  @PostMapping("/{id}/questions/batch")
  @Operation(summary = "Batch add questions to assessment", description = "Add multiple questions by ID at once. Duplicates are silently skipped.")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<List<AssessmentQuestionResponse>> batchAddQuestions(
      @PathVariable UUID id,
      @Valid @org.springframework.web.bind.annotation.RequestBody BatchAddQuestionsRequest request) {
    log.info("POST /api/v1/assessments/{}/questions/batch size={}", id, request.getQuestionIds().size());
    return ApiResponse.<List<AssessmentQuestionResponse>>builder()
        .code(1000)
        .message("Questions added successfully")
        .result(assessmentService.batchAddQuestions(id, request))
        .build();
  }

  @org.springframework.web.bind.annotation.PutMapping("/{id}/questions/points")
  @Operation(summary = "Batch update question points", description = "Update points for multiple questions in one transactional request.")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<List<AssessmentQuestionResponse>> batchUpdatePoints(
      @PathVariable UUID id,
      @Valid @org.springframework.web.bind.annotation.RequestBody BatchUpdatePointsRequest request) {
    log.info("PUT /api/v1/assessments/{}/questions/points", id);
    return ApiResponse.<List<AssessmentQuestionResponse>>builder()
        .code(1000)
        .message("Points updated successfully")
        .result(assessmentService.batchUpdatePoints(id, request))
        .build();
  }

  @PostMapping("/{id}/auto-distribute")
  @Operation(summary = "Auto-distribute total points", description = "Distribute totalPoints across all questions based on cognitive-level percentages.")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<List<AssessmentQuestionResponse>> autoDistributePoints(
      @PathVariable UUID id,
      @Valid @org.springframework.web.bind.annotation.RequestBody AutoDistributePointsRequest request) {
    log.info("POST /api/v1/assessments/{}/auto-distribute", id);
    return ApiResponse.<List<AssessmentQuestionResponse>>builder()
        .code(1000)
        .message("Points distributed successfully")
        .result(assessmentService.autoDistributePoints(id, request))
        .build();
  }

  @PostMapping("/{id}/questions/distribute-points")
  @Operation(
      summary = "Distribute points equally for all assessment questions",
      description =
          "Distribute totalPoints across all questions using strategy EQUAL with configurable decimal scale. "
              + "Ensures sum(points_override) equals totalPoints after rounding.")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<DistributeAssessmentPointsResponse> distributeQuestionPoints(
      @PathVariable UUID id,
      @Valid @org.springframework.web.bind.annotation.RequestBody
          DistributeAssessmentPointsRequest request) {
    log.info("POST /api/v1/assessments/{}/questions/distribute-points", id);
    return ApiResponse.<DistributeAssessmentPointsResponse>builder()
        .code(1000)
        .message("Points distributed successfully")
        .result(assessmentService.distributeQuestionPoints(id, request))
        .build();
  }
}
