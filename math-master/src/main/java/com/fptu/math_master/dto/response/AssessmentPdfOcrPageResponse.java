package com.fptu.math_master.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One page from Python POST /assessments/ocr-pdf-page (Gemini + Mathpix). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentPdfOcrPageResponse {
  private int pageNumber;
  private String text;
  private double confidence;
  private boolean success;
  private String ocrSource;
  /** Mongo draft id — persist OCR when navigating wizard steps. */
  private String draftId;
}
