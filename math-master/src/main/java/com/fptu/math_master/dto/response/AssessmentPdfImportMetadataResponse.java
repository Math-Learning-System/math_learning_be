package com.fptu.math_master.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Teacher form values from PDF import wizard (steps 1–2 config, not OCR body). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentPdfImportMetadataResponse {
  private String examTitle;
  private String schoolYear;
  private String examType;
  private String examScope;
  private String examScopeLabel;
  private String organizerType;
  private String organizerTypeLabel;
  private String provinceCity;
  private String district;
  private String schoolName;
  private String department;
  private String organizerName;
  private String examDate;
  private String schoolGradeName;
  private String subjectName;
  private String contextHint;
  private String questionBankName;
  private String assessmentType;
  private String assessmentTypeLabel;
  private Integer timeLimitMinutes;
  private String pdfLayout;
  private String pdfLayoutLabel;
  private String importContentMode;
  private String importContentModeLabel;
  private String sourceFileName;
}
