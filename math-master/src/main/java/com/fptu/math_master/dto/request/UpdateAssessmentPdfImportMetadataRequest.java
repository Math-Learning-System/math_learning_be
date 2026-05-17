package com.fptu.math_master.dto.request;

import com.fptu.math_master.enums.AssessmentType;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Teacher edits wizard metadata on assessment detail (PDF import). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssessmentPdfImportMetadataRequest {

  private String examTitle;
  private String schoolYear;
  private String examType;
  private String examScope;
  private String organizerType;
  private String provinceCity;
  private String district;
  private String schoolName;
  private String department;
  private String organizerName;
  private String examDate;
  private UUID schoolGradeId;
  private UUID subjectId;
  private String contextHint;
  private UUID questionBankId;
  private AssessmentType assessmentType;
  private Integer timeLimitMinutes;
  private String pdfLayout;
  private String importContentMode;
}
