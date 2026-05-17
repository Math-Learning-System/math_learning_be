package com.fptu.math_master.dto.response;

import com.fptu.math_master.dto.response.pdfimport.PdfImportedExamDto;
import com.fptu.math_master.dto.response.pdfimport.PdfImportedQuestionDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentImportResponse {

  private boolean analysisSuccessful;
  private Double confidenceScore;
  private List<String> warnings;
  private String extractedTextPreview;
  private int questionsImported;
  private int questionsSkipped;
  private AssessmentResponse assessment;
  /** Exam-level fields from PDF header. */
  private PdfImportedExamDto exam;
  /** Parsed questions with full structure (import status per row). */
  private List<ParsedQuestionPreview> parsedQuestions;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ParsedQuestionPreview {
    private int orderIndex;
    private String sectionLabel;
    private String subQuestionLabel;
    private String questionText;
    private String questionType;
    private String displayLabel;
    private boolean hasTable;
    private boolean imported;
    private String skipReason;
    /** Full structured payload for review UI. */
    private PdfImportedQuestionDto detail;
  }
}
