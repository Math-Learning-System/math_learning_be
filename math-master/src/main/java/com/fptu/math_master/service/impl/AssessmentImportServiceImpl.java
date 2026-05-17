package com.fptu.math_master.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.math_master.dto.request.CreateQuestionRequest;
import com.fptu.math_master.dto.request.PdfAssessmentImportFormInput;
import com.fptu.math_master.dto.response.AssessmentImportResponse;
import com.fptu.math_master.dto.response.AssessmentResponse;
import com.fptu.math_master.dto.response.QuestionResponse;
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
import com.fptu.math_master.service.AssessmentImportConfigService;
import com.fptu.math_master.service.AssessmentImportService;
import com.fptu.math_master.service.AssessmentService;
import com.fptu.math_master.service.GeminiService;
import com.fptu.math_master.service.QuestionService;
import com.fptu.math_master.service.TemplateImportService;
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

  TemplateImportService templateImportService;
  GeminiService geminiService;
  ObjectMapper objectMapper;
  QuestionService questionService;
  AssessmentService assessmentService;
  AssessmentRepository assessmentRepository;
  AssessmentQuestionRepository assessmentQuestionRepository;
  QuestionBankRepository questionBankRepository;
  AssessmentImportConfigService assessmentImportConfigService;
  SchoolGradeRepository schoolGradeRepository;
  SubjectRepository subjectRepository;

  @Override
  @Transactional
  public AssessmentImportResponse importAssessmentFromPdf(
      MultipartFile file, PdfAssessmentImportFormInput form) {

    if (form == null) {
      form = PdfAssessmentImportFormInput.builder().build();
    }

    assessmentImportConfigService.assertSchoolYearAllowed(form.schoolYear());
    assessmentImportConfigService.assertExamTypeAllowed(form.examType());
    assessmentImportConfigService.assertDepartmentAllowed(form.department());
    assessmentImportConfigService.assertExamScopeAllowed(form.examScope());
    assessmentImportConfigService.assertOrganizerTypeAllowed(form.organizerType());
    assessmentImportConfigService.assertProvinceCityAllowed(form.provinceCity());

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
    String subjectHint = resolveSubjectHint(form);
    String contextHint = form.contextHint();

    String extractedText = templateImportService.extractTextFromFile(file);
    if (extractedText == null || extractedText.trim().isEmpty()) {
      throw new AppException(ErrorCode.INVALID_KEY, "Không trích xuất được nội dung từ file PDF");
    }

    ParsedExamPayload payload =
        analyzeExamWithAI(extractedText, subjectHint, contextHint, sourceFile);
    PdfImportedExamDto exam = mergeExamWithForm(payload.exam(), form, sourceFile);
    List<PdfImportedQuestionDto> questions = payload.questions();

    if (questions.isEmpty()) {
      throw new AppException(
          ErrorCode.INVALID_KEY,
          "Không nhận diện được câu hỏi trong đề. Vui lòng kiểm tra file hoặc thử bổ sung gợi ý môn học.");
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
            .description(buildAssessmentDescription(exam))
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

    List<AssessmentImportResponse.ParsedQuestionPreview> previews = new ArrayList<>();
    int imported = 0;
    int skipped = 0;
    int order = 1;

    for (PdfImportedQuestionDto parsed : questions) {
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
                        : "Import từ PDF — vui lòng bổ sung đáp án/lời giải nếu cần")
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
                    buildQuestionGenerationMetadata(exam, parsed, assessment.getId(), sourceFile))
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

    if (imported == 0) {
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
    warnings.add(
        "Đề tự luận/PDF: giữ cả raw_text và math_latex/table_data trong metadata — cần rà soát trước khi công khai.");

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

  private ParsedExamPayload analyzeExamWithAI(
      String text, String subjectHint, String contextHint, String sourceFile) {
    try {
      String prompt = buildExamAnalysisPrompt(text, subjectHint, contextHint);
      String aiResponse = geminiService.sendMessage(prompt);
      return parseExamAnalysis(aiResponse, text, sourceFile);
    } catch (Exception ex) {
      log.error("AI exam import failed: {}", ex.getMessage(), ex);
      return fallbackParse(text, sourceFile);
    }
  }

  private String buildExamAnalysisPrompt(String text, String subjectHint, String contextHint) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("# ROLE\n");
    prompt.append(
        "You are a Vietnamese mathematics exam PDF parser. Split data into EXAM-level fields and QUESTION-level fields.\n");
    prompt.append("Output ONLY valid JSON (no markdown fences).\n\n");
    prompt.append("# EXAM-LEVEL FIELDS (object \"exam\")\n");
    prompt.append(
        "exam_title, school_year, department (legacy), subject, exam_date, duration_minutes, ");
    prompt.append("exam_type, total_pages, grade_level, raw_header_text, ");
    prompt.append(
        "exam_scope (national|province_city|district|school|organization|internal), ");
    prompt.append(
        "organizer_name, organizer_type (department_of_education|school|university|exam_board|...), ");
    prompt.append("province_city, district, school_name, country.\n\n");
    prompt.append("# QUESTION-LEVEL FIELDS (array \"questions\")\n");
    prompt.append("Each item is one gradable unit (e.g. Câu I.1, Câu I.2, Câu II):\n");
    prompt.append(
        "section_label (Câu I), section_score, sub_question_label (1,2), question_text, ");
    prompt.append(
        "question_type (tu_luan|trac_nghiem|bang_so_lieu|bieu_thuc|xac_suat|...), points, ");
    prompt.append("order_index, page_number, has_table, table_data {table_title, headers, rows}, ");
    prompt.append("math_latex (array of LaTeX strings), conditions, task, raw_text, ");
    prompt.append("answer_key, solution, difficulty, topic_tags, images.\n\n");
    prompt.append("# RULES\n");
    prompt.append("- Preserve Vietnamese diacritics and math in raw_text.\n");
    prompt.append("- Normalize formulas to LaTeX in math_latex.\n");
    prompt.append("- For tables: headers + rows exactly as in the document.\n");
    prompt.append("- Keep section groups (Câu I, Câu II) on each sub-question.\n");
    prompt.append("- If answer unknown, omit answer_key (do not guess).\n\n");
    if (subjectHint != null && !subjectHint.isBlank()) {
      prompt.append("Subject hint: ").append(subjectHint).append("\n");
    }
    if (contextHint != null && !contextHint.isBlank()) {
      prompt.append("Context hint: ").append(contextHint).append("\n");
    }
    prompt.append("\n# DOCUMENT\n").append(text).append("\n\n");
    prompt.append(
        """
        Return JSON shape:
        {
          "analysisSuccessful": true,
          "confidenceScore": 0.85,
          "warnings": [],
          "exam": {
            "exam_title": "...",
            "school_year": "2025 - 2026",
            "department": "...",
            "exam_scope": "province_city",
            "organizer_name": "Sở Giáo dục và Đào tạo Hà Nội",
            "organizer_type": "department_of_education",
            "province_city": "Hà Nội",
            "district": null,
            "school_name": null,
            "country": "Việt Nam",
            "subject": "Toán",
            "exam_date": "08/6/2025",
            "duration_minutes": 120,
            "exam_type": "Đề chính thức",
            "total_pages": 2,
            "grade_level": "Lớp 10",
            "raw_header_text": "..."
          },
          "questions": [
            {
              "section_label": "Câu I",
              "section_score": 1.5,
              "sub_question_label": "1",
              "question_type": "bang_so_lieu",
              "question_text": "...",
              "table_data": {"headers": ["..."], "rows": [["...", 17]]},
              "task": "...",
              "raw_text": "...",
              "order_index": 1,
              "page_number": 1
            }
          ]
        }
        """);
    return prompt.toString();
  }

  private ParsedExamPayload parseExamAnalysis(
      String aiResponse, String originalText, String sourceFile) {
    try {
      String json = extractJSON(aiResponse);
      if (json == null || json.isBlank()) {
        return fallbackParse(originalText, sourceFile);
      }
      JsonNode root = objectMapper.readTree(json);
      boolean ok = root.path("analysisSuccessful").asBoolean(true);
      double confidence = root.path("confidenceScore").asDouble(0.5);
      List<String> warnings = parseStringArray(root.path("warnings"));

      PdfImportedExamDto exam = parseExamDto(root.path("exam"), sourceFile);
      if (exam.getRawHeaderText() == null || exam.getRawHeaderText().isBlank()) {
        exam.setRawHeaderText(guessHeaderFromText(originalText));
      }

      List<PdfImportedQuestionDto> questions = parseQuestions(root.path("questions"));
      return new ParsedExamPayload(ok, confidence, warnings, exam, questions);
    } catch (Exception ex) {
      log.warn("Failed to parse AI exam JSON: {}", ex.getMessage());
      return fallbackParse(originalText, sourceFile);
    }
  }

  private PdfImportedExamDto parseExamDto(JsonNode node, String sourceFile) {
    if (node == null || node.isMissingNode()) {
      return PdfImportedExamDto.builder().sourceFile(sourceFile).build();
    }
    return PdfImportedExamDto.builder()
        .examTitle(textOrNull(node, "exam_title"))
        .schoolYear(textOrNull(node, "school_year"))
        .department(textOrNull(node, "department"))
        .examScope(textOrNull(node, "exam_scope"))
        .organizerName(textOrNull(node, "organizer_name"))
        .organizerType(textOrNull(node, "organizer_type"))
        .provinceCity(textOrNull(node, "province_city"))
        .district(textOrNull(node, "district"))
        .schoolName(textOrNull(node, "school_name"))
        .country(textOrNull(node, "country"))
        .subject(textOrNull(node, "subject"))
        .examDate(textOrNull(node, "exam_date"))
        .durationMinutes(intOrNull(node, "duration_minutes"))
        .examType(textOrNull(node, "exam_type"))
        .totalPages(intOrNull(node, "total_pages"))
        .gradeLevel(textOrNull(node, "grade_level"))
        .sourceFile(sourceFile)
        .rawHeaderText(textOrNull(node, "raw_header_text"))
        .build();
  }

  private List<PdfImportedQuestionDto> parseQuestions(JsonNode questionsNode) {
    List<PdfImportedQuestionDto> result = new ArrayList<>();
    if (!questionsNode.isArray()) {
      return result;
    }
    int fallbackOrder = 1;
    for (JsonNode node : questionsNode) {
      PdfImportedQuestionDto dto = parseQuestionNode(node, fallbackOrder);
      if (dto != null) {
        result.add(dto);
        fallbackOrder++;
      }
    }
    return result;
  }

  private PdfImportedQuestionDto parseQuestionNode(JsonNode node, int fallbackOrder) {
    String questionText = textOrNull(node, "question_text");
    String rawText = textOrNull(node, "raw_text");
    if ((questionText == null || questionText.isBlank()) && (rawText == null || rawText.isBlank())) {
      return null;
    }

    Map<String, Object> rawPayload = objectMapper.convertValue(node, new TypeReference<>() {});
    Integer orderIdx = intOrNull(node, "order_index");

    return PdfImportedQuestionDto.builder()
        .orderIndex(orderIdx != null ? orderIdx : fallbackOrder)
        .sectionLabel(textOrNull(node, "section_label"))
        .sectionScore(decimalOrNull(node, "section_score"))
        .subQuestionLabel(textOrNull(node, "sub_question_label"))
        .questionText(questionText)
        .questionType(textOrNull(node, "question_type"))
        .points(decimalOrNull(node, "points"))
        .pageNumber(intOrNull(node, "page_number"))
        .hasTable(node.path("has_table").asBoolean(node.has("table_data")))
        .tableData(parseTableData(node.path("table_data")))
        .mathLatex(parseStringList(node.path("math_latex")))
        .conditions(textOrNull(node, "conditions"))
        .task(textOrNull(node, "task"))
        .rawText(rawText)
        .answerKey(textOrNull(node, "answer_key"))
        .solution(textOrNull(node, "solution"))
        .difficulty(textOrNull(node, "difficulty"))
        .topicTags(parseStringList(node.path("topic_tags")))
        .images(parseStringList(node.path("images")))
        .rawImportPayload(rawPayload)
        .build();
  }

  private PdfImportedTableDataDto parseTableData(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    List<String> headers = new ArrayList<>();
    if (node.path("headers").isArray()) {
      node.path("headers").forEach(h -> headers.add(h.asText()));
    }
    List<List<Object>> rows = new ArrayList<>();
    if (node.path("rows").isArray()) {
      node.path("rows").forEach(rowNode -> {
        List<Object> row = new ArrayList<>();
        if (rowNode.isArray()) {
          rowNode.forEach(cell -> row.add(parseCell(cell)));
        }
        rows.add(row);
      });
    }
    return PdfImportedTableDataDto.builder()
        .tableTitle(textOrNull(node, "table_title"))
        .headers(headers.isEmpty() ? null : headers)
        .rows(rows.isEmpty() ? null : rows)
        .tableRawHtml(textOrNull(node, "table_raw_html"))
        .tableMarkdown(textOrNull(node, "table_markdown"))
        .build();
  }

  private Object parseCell(JsonNode cell) {
    if (cell.isNumber()) {
      return cell.numberValue();
    }
    return cell.asText();
  }

  private ParsedExamPayload fallbackParse(String text, String sourceFile) {
    List<PdfImportedQuestionDto> questions = new ArrayList<>();
    List<String> warnings =
        List.of("Phân tích AI thất bại — đã tách câu theo nhãn Câu/Bài cơ bản.");
    String header = guessHeaderFromText(text);
    PdfImportedExamDto exam =
        PdfImportedExamDto.builder()
            .examTitle("Đề import từ PDF")
            .sourceFile(sourceFile)
            .rawHeaderText(header)
            .build();

    String[] blocks = text.split("(?=(?i)(?:Câu|Bài)\\s+[IVXLC\\d]+)");
    int order = 1;
    for (String block : blocks) {
      String trimmed = block.trim();
      if (trimmed.length() < 15) {
        continue;
      }
      String section = trimmed.split("\\n")[0].trim();
      questions.add(
          PdfImportedQuestionDto.builder()
              .orderIndex(order++)
              .sectionLabel(section.length() < 40 ? section : null)
              .questionText(trimmed)
              .rawText(trimmed)
              .questionType("tu_luan")
              .build());
    }
    return new ParsedExamPayload(false, 0.3, warnings, exam, questions);
  }

  private String guessHeaderFromText(String text) {
    if (text == null) {
      return null;
    }
    String[] lines = text.split("\\n");
    StringBuilder header = new StringBuilder();
    int limit = Math.min(lines.length, 12);
    for (int i = 0; i < limit; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) {
        if (header.length() > 0) {
          break;
        }
        continue;
      }
      header.append(line).append("\n");
    }
    return header.toString().trim();
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
      String sourceFile) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("source", PDF_IMPORT_SOURCE);
    meta.put("assessmentId", assessmentId.toString());
    meta.put("sourceFile", sourceFile);
    if (parsed.getPageNumber() != null) {
      meta.put("pageNumber", parsed.getPageNumber());
    }
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

  private String textOrNull(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (v.isMissingNode() || v.isNull()) {
      return null;
    }
    String text = v.asText().trim();
    return text.isEmpty() ? null : text;
  }

  private Integer intOrNull(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (v.isMissingNode() || v.isNull() || !v.canConvertToInt()) {
      return null;
    }
    return v.asInt();
  }

  private BigDecimal decimalOrNull(JsonNode node, String field) {
    JsonNode v = node.path(field);
    if (v.isMissingNode() || v.isNull() || !v.isNumber()) {
      return null;
    }
    return BigDecimal.valueOf(v.asDouble());
  }

  private List<String> parseStringList(JsonNode node) {
    List<String> result = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(item -> {
        String s = item.asText().trim();
        if (!s.isEmpty()) {
          result.add(s);
        }
      });
    }
    return result.isEmpty() ? null : result;
  }

  private List<String> parseStringArray(JsonNode node) {
    List<String> list = parseStringList(node);
    return list != null ? list : new ArrayList<>();
  }

  private String extractJSON(String content) {
    if (content == null || content.trim().isEmpty()) {
      return null;
    }
    int startIdx = content.indexOf('{');
    int endIdx = content.lastIndexOf('}');
    if (startIdx >= 0 && endIdx > startIdx) {
      return content.substring(startIdx, endIdx + 1);
    }
    return content.trim();
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

  private String resolveSubjectHint(PdfAssessmentImportFormInput form) {
    if (form.subjectId() == null) {
      return null;
    }
    Subject subject =
        subjectRepository
            .findById(form.subjectId())
            .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));
    if (form.schoolGradeId() != null) {
      SchoolGrade grade =
          schoolGradeRepository
              .findById(form.schoolGradeId())
              .orElseThrow(() -> new AppException(ErrorCode.SCHOOL_GRADE_NOT_FOUND));
      return grade.getName() + " — " + subject.getName();
    }
    return subject.getName();
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
