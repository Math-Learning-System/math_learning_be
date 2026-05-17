package com.fptu.math_master.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** OCR wizard draft stored in MongoDB (via Python). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentPdfImportDraftResponse {
  private String draftId;
  private String fileKey;
  private String fileName;
  private int totalPages;
  private List<PdfImportDraftPageDto> pages;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PdfImportDraftPageDto {
    private int pageNumber;
    private String text;
    private double confidence;
    private String status;
    private String error;
  }
}
