package com.fptu.math_master.dto.response;

import com.fptu.math_master.enums.CognitiveLevel;
import com.fptu.math_master.enums.QuestionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestionResponse {

  private UUID questionId;
  private Integer orderIndex;
  /** Final effective points (pointsOverride if set, otherwise question.points). */
  private BigDecimal points;
  private QuestionType questionType;
  private String questionText;
  private Map<String, Object> options;
  private String correctAnswer;
  private String explanation;
  private String diagramData;
  private String diagramUrl;
  private String[] tags;
  private CognitiveLevel cognitiveLevel;
  private Instant createdAt;
  private BigDecimal pointsOverride;
  private Integer pageNumber;
  private String sectionLabel;
  /** Full-page OCR reference — not shown as an exam question in the page workspace. */
  private Boolean pageSource;
}
