package com.fptu.math_master.dto.response.pdfimport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One OCR page from PDF import wizard. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfImportPageDto {
  private Integer pageNumber;
  private String text;
  private String sectionLabel;
  private Double confidence;
}
