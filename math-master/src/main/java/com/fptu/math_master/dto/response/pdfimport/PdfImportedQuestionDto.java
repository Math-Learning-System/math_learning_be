package com.fptu.math_master.dto.response.pdfimport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One importable unit: typically section + sub-question (e.g. Câu I.1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfImportedQuestionDto {
  private Integer orderIndex;
  private String sectionLabel;
  private BigDecimal sectionScore;
  private String subQuestionLabel;
  private String questionText;
  private String questionType;
  private BigDecimal points;
  private Integer pageNumber;
  private Boolean hasTable;
  private PdfImportedTableDataDto tableData;
  private List<String> mathLatex;
  private String conditions;
  private String task;
  private String rawText;
  private String answerKey;
  private String solution;
  private String difficulty;
  private List<String> topicTags;
  private List<String> images;
  /** Full AI node preserved for audit / re-import. */
  private Map<String, Object> rawImportPayload;
}
