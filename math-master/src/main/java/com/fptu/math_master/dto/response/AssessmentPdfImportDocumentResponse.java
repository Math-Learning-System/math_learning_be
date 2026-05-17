package com.fptu.math_master.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Editable OCR document for PDF-imported assessments (question + answer sections). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentPdfImportDocumentResponse {
  private List<ContentBlockDto> questionBlocks;
  private List<ContentBlockDto> answerBlocks;
}
