package com.fptu.math_master.dto.response;

import com.fptu.math_master.dto.response.pdfimport.PdfImportedExamDto;
import com.fptu.math_master.dto.response.pdfimport.PdfImportedQuestionDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JSON from Python POST /api/v1/assessments/extract-pdf (Mathpix per page). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentPdfExtractResponse {
  private boolean analysisSuccessful;
  private Double confidenceScore;
  private List<String> warnings;
  private String extractedText;
  private PdfImportedExamDto exam;
  private List<PdfImportedQuestionDto> questions;
}
