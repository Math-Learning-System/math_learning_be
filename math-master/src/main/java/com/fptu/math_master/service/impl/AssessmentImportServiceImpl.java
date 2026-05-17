package com.fptu.math_master.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.math_master.dto.request.CreateQuestionRequest;
import com.fptu.math_master.dto.request.PdfAssessmentImportFormInput;
import com.fptu.math_master.dto.response.AssessmentImportResponse;
import com.fptu.math_master.dto.response.AssessmentResponse;
import com.fptu.math_master.dto.response.QuestionResponse;
import com.fptu.math_master.dto.response.pdfimport.PdfImportPageDto;
import com.fptu.math_master.dto.response.pdfimport.PdfImportedExamDto;
import com.fptu.math_master.dto.response.pdfimport.PdfImportedQuestionDto;
import com.fptu.math_master.dto.response.pdfimport.PdfImportedTableDataDto;
import com.fptu.math_master.entity.Assessment;
import com.fptu.math_master.entity.AssessmentQuestion;
import com.fptu.math_master.entity.QuestionBank;
import com.fptu.math_master.entity.SchoolGrade;
import com.fptu.math_master.entity.Subject;
import com.fptu.math_master.enums.AssessmentMode;
import com.fptu.math_master.enums.AssessmentStatus;
import com.fptu.math_master.enums.AssessmentType;
import com.fptu.math_master.enums.AttemptScoringPolicy;
import com.fptu.math_master.enums.CognitiveLevel;
import com.fptu.math_master.enums.QuestionType;
import com.fptu.math_master.exception.AppException;
import com.fptu.math_master.exception.ErrorCode;
import com.fptu.math_master.repository.AssessmentQuestionRepository;
import com.fptu.math_master.repository.AssessmentRepository;
import com.fptu.math_master.repository.QuestionBankRepository;
import com.fptu.math_master.repository.SchoolGradeRepository;
import com.fptu.math_master.repository.SubjectRepository;
import com.fptu.math_master.configuration.properties.MinioProperties;
import com.fptu.math_master.dto.response.AssessmentPdfExtractResponse;
import com.fptu.math_master.service.AssessmentImportConfigService;
import com.fptu.math_master.service.AssessmentImportService;
import com.fptu.math_master.service.AssessmentPdfImportDocumentHelper;
import com.fptu.math_master.service.AssessmentPdfImportMetadataHelper;
import com.fptu.math_master.service.AssessmentService;
import com.fptu.math_master.service.PythonCrawlerClient;
import com.fptu.math_master.service.QuestionService;
import com.fptu.math_master.service.TemplateImportService;
import com.fptu.math_master.service.UploadService;
import com.fptu.math_master.util.SecurityUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AssessmentImportServiceImpl implements AssessmentImportService {

  private static final int TEXT_PREVIEW_LIMIT = 500;
  private static final String REVIEW_PLACEHOLDER = "REVIEW_REQUIRED";
  private static final String PDF_IMPORT_SOURCE = "PDF_IMPORT";
  private static final String PDF_IMPORT_MINIO_PREFIX = "assessments/pdf-imports";

  TemplateImportService templateImportService;
  ObjectMapper objectMapper;
  PythonCrawlerClient pythonCrawlerClient;
  QuestionService questionService;
  AssessmentService assessmentService;
  AssessmentRepository assessmentRepository;
  AssessmentQuestionRepository assessmentQuestionRepository;
  QuestionBankRepository questionBankRepository;
  AssessmentImportConfigService assessmentImportConfigService;
  SchoolGradeRepository schoolGradeRepository;
  SubjectRepository subjectRepository;
  UploadService uploadService;
  MinioProperties minioProperties;

  @Override
  @Transactional
  public AssessmentImportResponse importAssessmentFromPdf(
      MultipartFile file, PdfAssessmentImportFormInput form) {

    if (form == null) {
      form = PdfAssessmentImportFormInput.builder().build();
    }
    form = enrichFormFromPreExtractedWizard(form);
    var importFormOptions = assessmentImportConfigService.getFormOptions();

    assessmentImportConfigService.assertSchoolYearAllowed(form.schoolYear());
    assessmentImportConfigService.assertExamTypeAllowed(form.examType());
    assessmentImportConfigService.assertDepartmentAllowed(form.department());
    assessmentImportConfigService.assertExamScopeAllowed(form.examScope());
    assessmentImportConfigService.assertOrganizerTypeAllowed(form.organizerType());
    assessmentImportConfigService.assertProvinceCityAllowed(form.provinceCity());
    assessmentImportConfigService.assertPdfLayoutAllowed(form.pdfLayout());
    assessmentImportConfigService.assertImportContentModeAllowed(form.importContentMode());

    if ("latex".equalsIgnoreCase(form.importContentMode())) {
      throw new AppException(
          ErrorCode.INVALID_KEY,
          "Chế độ chuyển LaTeX đang được phát triển. Vui lòng chọn xử lý PDF.");
    }

    if (!templateImportService.validateFile(file)) {
      throw new AppException(
          ErrorCode.INVALID_KEY,
          "File không hợp lệ hoặc vượt quá dung lượng cho phép (PDF, tối đa 10MB)");
    }

    UUID currentUserId = SecurityUtils.getCurrentUserId();
    UUID questionBankId = form.questionBankId();
    if (questionBankId != null) {
      validateCanUseQuestionBank(questionBankId, currentUserId);
    }

    String sourceFile = file.getOriginalFilename();

    AssessmentPdfExtractResponse pythonExtract = resolvePythonExtract(file, form, sourceFile);
    ParsedExamPayload payload = mapPythonExtract(pythonExtract, sourceFile);
    String extractedText =
        pythonExtract.getExtractedText() != null ? pythonExtract.getExtractedText() : "";
    PdfImportedExamDto exam = mergeExamWithForm(payload.exam(), form, sourceFile);
    List<PdfImportedQuestionDto> questions =
        payload.questions() != null ? payload.questions() : List.of();
    boolean manualBuild = isManualQuestionBuild(pythonExtract);

    if (questions.isEmpty() && !hasOcrPages(pythonExtract)) {
      throw new AppException(
          ErrorCode.INVALID_KEY,
          "Không trích được nội dung theo trang. Hoàn tất OCR ở bước 2 hoặc kiểm tra file PDF.");
    }

    String assessmentTitle =
        resolveTitle(form.examTitle(), exam != null ? exam.getExamTitle() : null, sourceFile);
    AssessmentType resolvedType =
        form.assessmentType() != null ? form.assessmentType() : AssessmentType.EXAM;
    Integer resolvedDuration =
        form.timeLimitMinutes() != null
            ? form.timeLimitMinutes()
            : exam != null ? exam.getDurationMinutes() : null;

    Assessment assessment =
        Assessment.builder()
            .teacherId(currentUserId)
            .title(assessmentTitle)
            .description(null)
            .assessmentType(resolvedType)
            .timeLimitMinutes(resolvedDuration)
            .passingScore(BigDecimal.valueOf(50))
            .randomizeQuestions(false)
            .showCorrectAnswers(false)
            .assessmentMode(AssessmentMode.DIRECT)
            .allowMultipleAttempts(false)
            .attemptScoringPolicy(AttemptScoringPolicy.BEST)
            .showScoreImmediately(true)
            .status(AssessmentStatus.DRAFT)
            .build();
    assessment = assessmentRepository.save(assessment);

    try {
      persistSourcePdfToMinio(assessment, file, sourceFile);
    } catch (Exception ex) {
      log.error("Failed to store source PDF on MinIO for assessment {}", assessment.getId(), ex);
      assessmentRepository.delete(assessment);
      throw new AppException(
          ErrorCode.INVALID_KEY,
          "Không lưu được file PDF lên kho lưu trữ. Kiểm tra MinIO đang chạy và thử lại.");
    }

    persistPdfImportPages(assessment, pythonExtract);
    persistPdfImportDocument(assessment, listOcrPages(pythonExtract), form.pdfLayout());
    applyPdfImportMetadataJson(assessment, form, exam, sourceFile, importFormOptions);
    assessment = assessmentRepository.save(assessment);

    List<AssessmentImportResponse.ParsedQuestionPreview> previews = new ArrayList<>();
    int imported = 0;
    int skipped = 0;
    int order = 1;

    if (manualBuild) {
      for (PdfImportPageDto page : listOcrPages(pythonExtract)) {
        previews.add(
            AssessmentImportResponse.ParsedQuestionPreview.builder()
                .orderIndex(page.getPageNumber())
                .sectionLabel(
                    page.getSectionLabel() != null
                        ? page.getSectionLabel()
                        : "Trang " + page.getPageNumber())
                .questionText(trimPreview(page.getText()))
                .imported(false)
                .skipReason("Tạo câu hỏi thủ công tại trang chi tiết đề")
                .build());
      }
    }

    for (PdfImportedQuestionDto parsed : manualBuild ? List.<PdfImportedQuestionDto>of() : questions) {
      if (!isImportable(parsed)) {
        skipped++;
        previews.add(toPreview(parsed, order, false, "Thiếu nội dung câu hỏi"));
        order++;
        continue;
      }

      try {
        String composedText = buildComposedQuestionText(parsed);
        QuestionType qType = mapPdfQuestionType(parsed.getQuestionType());
        String correctAnswer = resolveCorrectAnswer(parsed);
        BigDecimal points =
            parsed.getPoints() != null
                ? parsed.getPoints()
                : parsed.getSectionScore() != null ? parsed.getSectionScore() : BigDecimal.ONE;

        CreateQuestionRequest createRequest =
            CreateQuestionRequest.builder()
                .questionText(composedText)
                .questionType(qType)
                .correctAnswer(correctAnswer)
                .explanation(
                    parsed.getSolution() != null && !parsed.getSolution().isBlank()
                        ? parsed.getSolution().trim()
                        : "Trích bằng Mathpix — chỉnh đáp án/lời giải khi rà soát đề")
                .points(points)
                .cognitiveLevel(CognitiveLevel.VAN_DUNG)
                .questionBankId(questionBankId)
                .tags(
                    parsed.getTopicTags() != null && !parsed.getTopicTags().isEmpty()
                        ? parsed.getTopicTags().toArray(new String[0])
                        : null)
                .solutionSteps(parsed.getSolution())
                .diagramData(buildDiagramPayload(parsed))
                .generationMetadata(
                    buildQuestionGenerationMetadata(
                        exam,
                        parsed,
                        assessment.getId(),
                        sourceFile,
                        form,
                        assessment.getSourcePdfPath()))
                .build();

        QuestionResponse created = questionService.createQuestion(createRequest);

        AssessmentQuestion aq =
            AssessmentQuestion.builder()
                .assessmentId(assessment.getId())
                .questionId(created.getId())
                .orderIndex(parsed.getOrderIndex() != null ? parsed.getOrderIndex() : order)
                .build();
        assessmentQuestionRepository.save(aq);

        imported++;
        previews.add(toPreview(parsed, order, true, null));
        order++;
      } catch (Exception ex) {
        log.warn("Skip question at order {}: {}", order, ex.getMessage());
        skipped++;
        previews.add(toPreview(parsed, order, false, ex.getMessage()));
        order++;
      }
    }

    if (imported == 0 && !manualBuild) {
      deleteSourcePdfFromMinio(assessment);
      assessmentRepository.delete(assessment);
      throw new AppException(
          ErrorCode.INVALID_KEY,
          "Không thể tạo câu hỏi hợp lệ từ file. Vui lòng chỉnh sửa đề hoặc thử lại.");
    }

    AssessmentResponse assessmentResponse = assessmentService.getAssessmentById(assessment.getId());

    List<String> warnings = new ArrayList<>(payload.warnings());
    if (skipped > 0) {
      warnings.add("Đã bỏ qua " + skipped + " mục không đủ nội dung hoặc lỗi khi lưu.");
    }
    if (manualBuild) {
      int pageCount = listOcrPages(pythonExtract).size();
      warnings.add(
          "Đã lưu "
              + pageCount
              + " trang OCR. Vào Chi tiết đề để tạo và chỉnh từng câu từ LaTeX theo trang.");
    } else {
      warnings.add(
          "Đề import qua Mathpix (Python): chỉnh nội dung/đáp án từng câu trước khi công khai.");
    }

    return AssessmentImportResponse.builder()
        .analysisSuccessful(payload.analysisSuccessful())
        .confidenceScore(payload.confidenceScore())
        .warnings(warnings)
        .extractedTextPreview(trimPreview(extractedText))
        .questionsImported(imported)
        .questionsSkipped(skipped)
        .assessment(assessmentResponse)
        .exam(exam)
        .parsedQuestions(previews)
        .build();
  }

  private AssessmentPdfExtractResponse resolvePythonExtract(
      MultipartFile file, PdfAssessmentImportFormInput form, String sourceFile) {
    String pre = form.preExtractedJson();
    if (pre != null && !pre.isBlank()) {
      try {
        AssessmentPdfExtractResponse parsed =
            objectMapper.readValue(pre, AssessmentPdfExtractResponse.class);
        if (parsed.getExam() != null
            && (parsed.getExam().getSourceFile() == null
                || parsed.getExam().getSourceFile().isBlank())) {
          parsed.getExam().setSourceFile(sourceFile);
        }
        return parsed;
      } catch (Exception ex) {
        log.warn("Invalid preExtractedJson, falling back to Python OCR: {}", ex.getMessage());
      }
    }
    return pythonCrawlerClient.extractAssessmentFromPdf(file, form.pdfLayout(), sourceFile);
  }

  private ParsedExamPayload mapPythonExtract(
      AssessmentPdfExtractResponse response, String sourceFile) {
    if (response == null
        || ((!hasOcrPages(response))
            && (response.getQuestions() == null || response.getQuestions().isEmpty()))) {
      throw new AppException(
          ErrorCode.INVALID_KEY,
          "Không trích được nội dung theo trang. Hoàn tất OCR ở bước 2 hoặc kiểm tra file PDF.");
    }
    PdfImportedExamDto exam = response.getExam();
    if (exam == null) {
      exam = PdfImportedExamDto.builder().sourceFile(sourceFile).build();
    } else if (exam.getSourceFile() == null || exam.getSourceFile().isBlank()) {
      exam.setSourceFile(sourceFile);
    }
    List<String> warnings =
        response.getWarnings() != null ? new ArrayList<>(response.getWarnings()) : new ArrayList<>();
    double confidence =
        response.getConfidenceScore() != null ? response.getConfidenceScore() : 0.75;
    return new ParsedExamPayload(
        response.isAnalysisSuccessful(),
        confidence,
        warnings,
        exam,
        response.getQuestions() != null ? response.getQuestions() : List.of());
  }

  private static boolean isManualQuestionBuild(AssessmentPdfExtractResponse extract) {
    return extract != null && Boolean.TRUE.equals(extract.getManualQuestionBuild());
  }

  private static boolean hasOcrPages(AssessmentPdfExtractResponse extract) {
    return extract != null && extract.getPages() != null && !extract.getPages().isEmpty();
  }

  private static List<PdfImportPageDto> listOcrPages(AssessmentPdfExtractResponse extract) {
    if (extract == null) {
      return List.of();
    }
    if (hasOcrPages(extract)) {
      return extract.getPages();
    }
    if (extract.getQuestions() == null) {
      return List.of();
    }
    List<PdfImportPageDto> pages = new ArrayList<>();
    for (PdfImportedQuestionDto q : extract.getQuestions()) {
      if (q.getPageNumber() == null) {
        continue;
      }
      String text =
          q.getRawText() != null && !q.getRawText().isBlank()
              ? q.getRawText()
              : q.getQuestionText();
      if (text == null || text.isBlank()) {
        continue;
      }
      pages.add(
          PdfImportPageDto.builder()
              .pageNumber(q.getPageNumber())
              .sectionLabel(q.getSectionLabel())
              .text(text)
              .build());
    }
    return pages;
  }

  private void persistPdfImportPages(Assessment assessment, AssessmentPdfExtractResponse extract) {
    List<PdfImportPageDto> pages = listOcrPages(extract);
    if (pages.isEmpty()) {
      return;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("pages", pages);
      assessment.setPdfImportPagesJson(objectMapper.writeValueAsString(payload));
    } catch (Exception ex) {
      log.warn("Could not serialize pdf import pages for assessment {}: {}", assessment.getId(), ex.getMessage());
    }
  }

  /** Persists wizard step 1–2 fields into {@code pdf_import_metadata_json} (same transaction as import). */
  private void applyPdfImportMetadataJson(
      Assessment assessment,
      PdfAssessmentImportFormInput form,
      PdfImportedExamDto exam,
      String sourceFileName,
      com.fptu.math_master.dto.response.AssessmentImportFormOptionsResponse importFormOptions) {
    String gradeName = resolveSchoolGradeName(form);
    String subjectName = resolveSubjectName(form);
    String bankName = null;
    if (form.questionBankId() != null) {
      bankName =
          questionBankRepository
              .findById(form.questionBankId())
              .map(QuestionBank::getName)
              .orElse(null);
    }
    var metadata =
        AssessmentPdfImportMetadataHelper.buildFromImport(
            form,
            exam,
            sourceFileName,
            gradeName,
            subjectName,
            bankName,
            importFormOptions);
    String json = AssessmentPdfImportMetadataHelper.toJson(metadata, objectMapper);
    assessment.setPdfImportMetadataJson(json);
    log.info(
        "Stored PDF import wizard metadata for assessment {} ({} bytes)",
        assessment.getId(),
        json.length());
  }

  /** Merge wizardForm snapshot from preExtractedJson when multipart fields are missing. */
  private PdfAssessmentImportFormInput enrichFormFromPreExtractedWizard(
      PdfAssessmentImportFormInput form) {
    if (form == null || form.preExtractedJson() == null || form.preExtractedJson().isBlank()) {
      return form;
    }
    try {
      var root = objectMapper.readTree(form.preExtractedJson());
      var wizard = root.get("wizardForm");
      if (wizard == null || !wizard.isObject()) {
        return form;
      }
      var b = form.toBuilder();
      if (isBlank(form.examTitle()) && wizard.hasNonNull("examTitle")) {
        b.examTitle(wizard.get("examTitle").asText());
      }
      if (isBlank(form.schoolYear()) && wizard.hasNonNull("schoolYear")) {
        b.schoolYear(wizard.get("schoolYear").asText());
      }
      if (isBlank(form.examType()) && wizard.hasNonNull("examType")) {
        b.examType(wizard.get("examType").asText());
      }
      if (isBlank(form.examScope()) && wizard.hasNonNull("examScope")) {
        b.examScope(wizard.get("examScope").asText());
      }
      if (isBlank(form.organizerType()) && wizard.hasNonNull("organizerType")) {
        b.organizerType(wizard.get("organizerType").asText());
      }
      if (isBlank(form.provinceCity()) && wizard.hasNonNull("provinceCity")) {
        b.provinceCity(wizard.get("provinceCity").asText());
      }
      if (isBlank(form.district()) && wizard.hasNonNull("district")) {
        b.district(wizard.get("district").asText());
      }
      if (isBlank(form.schoolName()) && wizard.hasNonNull("schoolName")) {
        b.schoolName(wizard.get("schoolName").asText());
      }
      if (isBlank(form.department()) && wizard.hasNonNull("department")) {
        b.department(wizard.get("department").asText());
      }
      if (isBlank(form.organizerName()) && wizard.hasNonNull("organizerName")) {
        b.organizerName(wizard.get("organizerName").asText());
      }
      if (isBlank(form.examDate()) && wizard.hasNonNull("examDate")) {
        b.examDate(wizard.get("examDate").asText());
      }
      if (form.schoolGradeId() == null && wizard.hasNonNull("schoolGradeId")) {
        b.schoolGradeId(UUID.fromString(wizard.get("schoolGradeId").asText()));
      }
      if (form.subjectId() == null && wizard.hasNonNull("subjectId")) {
        b.subjectId(UUID.fromString(wizard.get("subjectId").asText()));
      }
      if (isBlank(form.contextHint()) && wizard.hasNonNull("contextHint")) {
        b.contextHint(wizard.get("contextHint").asText());
      }
      if (form.questionBankId() == null && wizard.hasNonNull("questionBankId")) {
        b.questionBankId(UUID.fromString(wizard.get("questionBankId").asText()));
      }
      if (form.timeLimitMinutes() == null && wizard.hasNonNull("timeLimitMinutes")) {
        b.timeLimitMinutes(wizard.get("timeLimitMinutes").asInt());
      }
      if (isBlank(form.pdfLayout()) && wizard.hasNonNull("pdfLayout")) {
        b.pdfLayout(wizard.get("pdfLayout").asText());
      }
      if (isBlank(form.importContentMode()) && wizard.hasNonNull("importContentMode")) {
        b.importContentMode(wizard.get("importContentMode").asText());
      }
      return b.build();
    } catch (Exception ex) {
      log.warn("Could not merge wizardForm from preExtractedJson: {}", ex.getMessage());
      return form;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String resolveSchoolGradeName(PdfAssessmentImportFormInput form) {
    if (form.schoolGradeId() != null) {
      String fromDb =
          schoolGradeRepository
              .findById(form.schoolGradeId())
              .map(SchoolGrade::getName)
              .orElse(null);
      if (fromDb != null && !fromDb.isBlank()) {
        return fromDb;
      }
    }
    return readWizardFormText(form, "schoolGradeName");
  }

  private String resolveSubjectName(PdfAssessmentImportFormInput form) {
    if (form.subjectId() != null) {
      String fromDb =
          subjectRepository.findById(form.subjectId()).map(Subject::getName).orElse(null);
      if (fromDb != null && !fromDb.isBlank()) {
        return fromDb;
      }
    }
    return readWizardFormText(form, "subjectName");
  }

  private String readWizardFormText(PdfAssessmentImportFormInput form, String field) {
    if (form == null || form.preExtractedJson() == null || form.preExtractedJson().isBlank()) {
      return null;
    }
    try {
      var wizard = objectMapper.readTree(form.preExtractedJson()).get("wizardForm");
      if (wizard != null && wizard.hasNonNull(field)) {
        String text = wizard.get(field).asText();
        return text != null && !text.isBlank() ? text.trim() : null;
      }
    } catch (Exception ignored) {
      // optional snapshot
    }
    return null;
  }

  private void persistPdfImportDocument(
      Assessment assessment, List<PdfImportPageDto> pages, String pdfLayout) {
    if (pages.isEmpty()) {
      return;
    }
    try {
      var doc = AssessmentPdfImportDocumentHelper.seedFromPages(pages, pdfLayout);
      assessment.setPdfImportDocumentJson(
          AssessmentPdfImportDocumentHelper.toJson(doc, objectMapper));
    } catch (Exception ex) {
      log.warn(
          "Could not serialize pdf import document for assessment {}: {}",
          assessment.getId(),
          ex.getMessage());
    }
  }

  private void persistSourcePdfToMinio(
      Assessment assessment, MultipartFile file, String originalFileName) {
    String directory = PDF_IMPORT_MINIO_PREFIX + "/" + assessment.getId();
    String objectKey =
        uploadService.uploadFile(file, directory, minioProperties.getTemplateBucket());
    assessment.setSourcePdfPath(objectKey);
    assessment.setSourcePdfOriginalName(originalFileName);
    assessmentRepository.save(assessment);
    log.info(
        "Stored import source PDF for assessment {} at {}/{}",
        assessment.getId(),
        minioProperties.getTemplateBucket(),
        objectKey);
  }

  private void deleteSourcePdfFromMinio(Assessment assessment) {
    String key = assessment.getSourcePdfPath();
    if (key == null || key.isBlank()) {
      return;
    }
    try {
      uploadService.deleteFile(key.trim(), minioProperties.getTemplateBucket());
    } catch (Exception ex) {
      log.warn(
          "Could not delete source PDF {} for assessment {}: {}",
          key,
          assessment.getId(),
          ex.getMessage());
    }
  }

  private boolean isImportable(PdfImportedQuestionDto parsed) {
    String composed = buildComposedQuestionText(parsed);
    return composed != null && !composed.isBlank();
  }

  private String buildComposedQuestionText(PdfImportedQuestionDto parsed) {
    StringBuilder sb = new StringBuilder();
    if (parsed.getSectionLabel() != null && !parsed.getSectionLabel().isBlank()) {
      sb.append("**").append(parsed.getSectionLabel().trim());
      if (parsed.getSubQuestionLabel() != null && !parsed.getSubQuestionLabel().isBlank()) {
        sb.append(".").append(parsed.getSubQuestionLabel().trim());
      }
      sb.append("**").append("\n\n");
    }
    String body =
        parsed.getQuestionText() != null && !parsed.getQuestionText().isBlank()
            ? parsed.getQuestionText().trim()
            : parsed.getRawText() != null ? parsed.getRawText().trim() : "";
    if (!body.isEmpty()) {
      sb.append(body).append("\n\n");
    }
    if (parsed.getTableData() != null && parsed.getTableData().getHeaders() != null) {
      sb.append(formatTableMarkdown(parsed.getTableData())).append("\n\n");
    }
    if (parsed.getMathLatex() != null && !parsed.getMathLatex().isEmpty()) {
      for (String latex : parsed.getMathLatex()) {
        sb.append("$").append(latex).append("$\n");
      }
      sb.append("\n");
    }
    if (parsed.getConditions() != null && !parsed.getConditions().isBlank()) {
      sb.append("Điều kiện: ").append(parsed.getConditions().trim()).append("\n\n");
    }
    if (parsed.getTask() != null && !parsed.getTask().isBlank()) {
      sb.append("Yêu cầu: ").append(parsed.getTask().trim());
    }
    return sb.toString().trim();
  }

  private String formatTableMarkdown(PdfImportedTableDataDto table) {
    if (table.getHeaders() == null || table.getHeaders().isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (table.getTableTitle() != null) {
      sb.append(table.getTableTitle()).append("\n");
    }
    sb.append("| ").append(String.join(" | ", table.getHeaders())).append(" |\n");
    sb.append("|").append(" --- |".repeat(table.getHeaders().size())).append("\n");
    if (table.getRows() != null) {
      for (List<Object> row : table.getRows()) {
        List<String> cells = new ArrayList<>();
        for (Object cell : row) {
          cells.add(cell == null ? "" : String.valueOf(cell));
        }
        sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
      }
    }
    return sb.toString();
  }

  private String buildAssessmentDescription(PdfImportedExamDto exam) {
    if (exam == null) {
      return "Import từ PDF";
    }
    StringBuilder sb = new StringBuilder("Import từ PDF");
    if (exam.getOrganizerName() != null) {
      sb.append("\n").append(exam.getOrganizerName());
    } else if (exam.getDepartment() != null) {
      sb.append("\n").append(exam.getDepartment());
    }
    if (exam.getExamScope() != null) {
      sb.append(" · ").append(exam.getExamScope());
    }
    if (exam.getProvinceCity() != null) {
      sb.append(" · ").append(exam.getProvinceCity());
    }
    if (exam.getSubject() != null) {
      sb.append(" · ").append(exam.getSubject());
    }
    if (exam.getSchoolYear() != null) {
      sb.append(" · ").append(exam.getSchoolYear());
    }
    if (exam.getExamDate() != null) {
      sb.append(" · ").append(exam.getExamDate());
    }
    if (exam.getDurationMinutes() != null) {
      sb.append(" · ").append(exam.getDurationMinutes()).append(" phút");
    }
    if (exam.getExamType() != null) {
      sb.append(" · ").append(exam.getExamType());
    }
    if (exam.getGradeLevel() != null) {
      sb.append(" · ").append(exam.getGradeLevel());
    }
    if (exam.getRawHeaderText() != null && !exam.getRawHeaderText().isBlank()) {
      sb.append("\n\n---\n").append(exam.getRawHeaderText());
    }
    return sb.toString();
  }

  private Map<String, Object> buildQuestionGenerationMetadata(
      PdfImportedExamDto exam,
      PdfImportedQuestionDto parsed,
      UUID assessmentId,
      String sourceFile,
      PdfAssessmentImportFormInput form,
      String sourcePdfPath) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("source", PDF_IMPORT_SOURCE);
    meta.put("assessmentId", assessmentId.toString());
    meta.put("sourceFile", sourceFile);
    if (form != null) {
      if (form.pdfLayout() != null && !form.pdfLayout().isBlank()) {
        meta.put("pdfLayout", form.pdfLayout());
      }
      if (form.importContentMode() != null && !form.importContentMode().isBlank()) {
        meta.put("importContentMode", form.importContentMode());
      }
    }
    if (sourcePdfPath != null && !sourcePdfPath.isBlank()) {
      meta.put("sourcePdfPath", sourcePdfPath);
      meta.put("sourcePdfBucket", minioProperties.getTemplateBucket());
    }
    if (parsed.getPageNumber() != null) {
      meta.put("pageNumber", parsed.getPageNumber());
    }
    if (parsed.getSectionLabel() != null && !parsed.getSectionLabel().isBlank()) {
      meta.put("sectionLabel", parsed.getSectionLabel());
    }
    meta.put("pageSource", Boolean.TRUE);
    if (exam != null) {
      meta.put("exam", objectMapper.convertValue(exam, new TypeReference<Map<String, Object>>() {}));
    }
    meta.put(
        "pdfQuestion",
        objectMapper.convertValue(parsed, new TypeReference<Map<String, Object>>() {}));
    if (parsed.getRawText() != null) {
      meta.put("rawText", parsed.getRawText());
    }
    if (parsed.getMathLatex() != null) {
      meta.put("mathLatex", parsed.getMathLatex());
    }
    if (parsed.getTableData() != null) {
      meta.put(
          "tableData",
          objectMapper.convertValue(
              parsed.getTableData(), new TypeReference<Map<String, Object>>() {}));
    }
    return meta;
  }

  private String buildDiagramPayload(PdfImportedQuestionDto parsed) {
    if ((parsed.getMathLatex() == null || parsed.getMathLatex().isEmpty())
        && (parsed.getConditions() == null || parsed.getConditions().isBlank())) {
      return null;
    }
    try {
      Map<String, Object> diagram = new LinkedHashMap<>();
      if (parsed.getMathLatex() != null) {
        diagram.put("mathLatex", parsed.getMathLatex());
      }
      if (parsed.getConditions() != null) {
        diagram.put("conditions", parsed.getConditions());
      }
      return objectMapper.writeValueAsString(diagram);
    } catch (Exception e) {
      return parsed.getMathLatex() != null ? String.join("\n", parsed.getMathLatex()) : null;
    }
  }

  private String resolveCorrectAnswer(PdfImportedQuestionDto parsed) {
    if (parsed.getAnswerKey() != null && !parsed.getAnswerKey().isBlank()) {
      return parsed.getAnswerKey().trim();
    }
    return REVIEW_PLACEHOLDER;
  }

  private QuestionType mapPdfQuestionType(String type) {
    if (type == null || type.isBlank()) {
      return QuestionType.ESSAY;
    }
    String t = type.toLowerCase().trim();
    if (t.contains("trac_nghiem") || t.contains("trắc nghiệm") || t.contains("mcq")) {
      return QuestionType.MULTIPLE_CHOICE;
    }
    if (t.contains("dung_sai") || t.contains("đúng") || t.contains("sai")) {
      return QuestionType.TRUE_FALSE;
    }
    if (t.contains("ngan") || t.contains("ngắn") || t.contains("short")) {
      return QuestionType.SHORT_ANSWER;
    }
    return QuestionType.ESSAY;
  }

  private AssessmentImportResponse.ParsedQuestionPreview toPreview(
      PdfImportedQuestionDto parsed, int order, boolean imported, String skipReason) {
    String composed = buildComposedQuestionText(parsed);
    String previewText =
        composed.length() > 240 ? composed.substring(0, 240) + "…" : composed;
    return AssessmentImportResponse.ParsedQuestionPreview.builder()
        .orderIndex(parsed.getOrderIndex() != null ? parsed.getOrderIndex() : order)
        .sectionLabel(parsed.getSectionLabel())
        .subQuestionLabel(parsed.getSubQuestionLabel())
        .questionText(previewText)
        .questionType(parsed.getQuestionType())
        .displayLabel(buildDisplayLabel(parsed))
        .hasTable(Boolean.TRUE.equals(parsed.getHasTable()) || parsed.getTableData() != null)
        .imported(imported)
        .skipReason(skipReason)
        .detail(parsed)
        .build();
  }

  private String buildDisplayLabel(PdfImportedQuestionDto parsed) {
    if (parsed.getSectionLabel() == null) {
      return "Câu " + parsed.getOrderIndex();
    }
    if (parsed.getSubQuestionLabel() != null && !parsed.getSubQuestionLabel().isBlank()) {
      return parsed.getSectionLabel().trim() + "." + parsed.getSubQuestionLabel().trim();
    }
    return parsed.getSectionLabel().trim();
  }

  private String resolveTitle(String requested, String suggested, String filename) {
    if (requested != null && !requested.isBlank()) {
      return requested.trim();
    }
    if (suggested != null && !suggested.isBlank()) {
      return suggested.trim();
    }
    if (filename != null && !filename.isBlank()) {
      String name = filename.replaceAll("(?i)\\.pdf$", "").trim();
      if (!name.isEmpty()) {
        return name;
      }
    }
    return "Đề thi import PDF";
  }

  private String trimPreview(String text) {
    if (text.length() <= TEXT_PREVIEW_LIMIT) {
      return text;
    }
    return text.substring(0, TEXT_PREVIEW_LIMIT) + "…";
  }

  private PdfImportedExamDto mergeExamWithForm(
      PdfImportedExamDto fromAi, PdfAssessmentImportFormInput form, String sourceFile) {
    PdfImportedExamDto base =
        fromAi != null
            ? fromAi
            : PdfImportedExamDto.builder().sourceFile(sourceFile).build();

    String gradeLevel = base.getGradeLevel();
    if (form.schoolGradeId() != null) {
      SchoolGrade grade =
          schoolGradeRepository
              .findById(form.schoolGradeId())
              .orElseThrow(() -> new AppException(ErrorCode.SCHOOL_GRADE_NOT_FOUND));
      gradeLevel = grade.getName();
    }

    String subject = base.getSubject();
    if (form.subjectId() != null) {
      Subject subj =
          subjectRepository
              .findById(form.subjectId())
              .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));
      subject = subj.getName();
    }

    String organizerName =
        firstNonBlank(
            form.organizerName(),
            firstNonBlank(form.department(), firstNonBlank(base.getOrganizerName(), base.getDepartment())));
    String department =
        firstNonBlank(form.department(), firstNonBlank(base.getDepartment(), organizerName));
    String provinceCity = firstNonBlank(form.provinceCity(), base.getProvinceCity());
    String provinceCityType =
        provinceCity != null
            ? assessmentImportConfigService.resolveProvinceCityType(provinceCity)
            : base.getProvinceCityType();
    var formOptions = assessmentImportConfigService.getFormOptions();

    return PdfImportedExamDto.builder()
        .examTitle(firstNonBlank(form.examTitle(), base.getExamTitle()))
        .schoolYear(firstNonBlank(form.schoolYear(), base.getSchoolYear()))
        .department(department)
        .subject(firstNonBlank(subject, base.getSubject()))
        .examDate(firstNonBlank(form.examDate(), base.getExamDate()))
        .durationMinutes(
            form.timeLimitMinutes() != null ? form.timeLimitMinutes() : base.getDurationMinutes())
        .examType(firstNonBlank(form.examType(), base.getExamType()))
        .totalPages(base.getTotalPages())
        .gradeLevel(firstNonBlank(gradeLevel, base.getGradeLevel()))
        .sourceFile(sourceFile)
        .rawHeaderText(base.getRawHeaderText())
        .examScope(firstNonBlank(form.examScope(), base.getExamScope()))
        .organizerName(organizerName)
        .organizerType(firstNonBlank(form.organizerType(), base.getOrganizerType()))
        .provinceCity(provinceCity)
        .provinceCityType(provinceCityType)
        .district(firstNonBlank(form.district(), base.getDistrict()))
        .schoolName(firstNonBlank(form.schoolName(), base.getSchoolName()))
        .country(
            firstNonBlank(
                form.country(),
                firstNonBlank(base.getCountry(), formOptions.getCountry())))
        .adminVersion(
            firstNonBlank(base.getAdminVersion(), formOptions.getAdminVersion()))
        .build();
  }

  private String firstNonBlank(String preferred, String fallback) {
    if (preferred != null && !preferred.isBlank()) {
      return preferred.trim();
    }
    return fallback;
  }

  private void validateCanUseQuestionBank(UUID bankId, UUID currentUserId) {
    QuestionBank bank =
        questionBankRepository
            .findByIdAndNotDeleted(bankId)
            .orElseThrow(() -> new AppException(ErrorCode.QUESTION_BANK_NOT_FOUND));

    if (!bank.getTeacherId().equals(currentUserId)
        && !Boolean.TRUE.equals(bank.getIsPublic())
        && !hasRoleAdmin()) {
      throw new AppException(ErrorCode.QUESTION_BANK_ACCESS_DENIED);
    }
  }

  private boolean hasRoleAdmin() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Object scopeClaim = jwtAuth.getToken().getClaims().get("scope");
      if (scopeClaim instanceof String scopes) {
        return Arrays.asList(scopes.split(" ")).contains("ROLE_ADMIN");
      }
    }
    return false;
  }

  private record ParsedExamPayload(
      boolean analysisSuccessful,
      double confidenceScore,
      List<String> warnings,
      PdfImportedExamDto exam,
      List<PdfImportedQuestionDto> questions) {}
}
