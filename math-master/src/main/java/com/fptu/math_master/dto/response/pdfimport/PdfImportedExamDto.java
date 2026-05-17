package com.fptu.math_master.dto.response.pdfimport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Metadata extracted from the exam PDF header / cover block. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfImportedExamDto {
  private String examTitle;
  private String schoolYear;
  /** Legacy; prefer organizerName when present. */
  private String department;
  private String subject;
  private String examDate;
  private Integer durationMinutes;
  private String examType;
  private Integer totalPages;
  private String gradeLevel;
  private String sourceFile;
  private String rawHeaderText;

  /** national | province_city | district | school | organization | internal */
  private String examScope;
  private String organizerName;
  private String organizerType;
  private String provinceCity;
  private String provinceCityType;
  private String district;
  private String schoolName;
  private String country;
  private String adminVersion;
}
