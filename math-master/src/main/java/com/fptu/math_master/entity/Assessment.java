package com.fptu.math_master.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.Nationalized;

import com.fptu.math_master.enums.AssessmentMode;
import com.fptu.math_master.enums.AssessmentStatus;
import com.fptu.math_master.enums.AssessmentType;
import com.fptu.math_master.enums.AttemptScoringPolicy;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
    name = "assessments",
    indexes = {
      @Index(name = "idx_assessments_teacher", columnList = "teacher_id"),
      @Index(name = "idx_assessments_exam_matrix", columnList = "exam_matrix_id"),
      @Index(name = "idx_assessments_mode", columnList = "assessment_mode"),
      @Index(name = "idx_assessments_status", columnList = "status"),
      @Index(name = "idx_assessments_start_date", columnList = "start_date"),
      @Index(name = "idx_assessments_end_date", columnList = "end_date")
    })
/**
 * The entity of 'Assessment'.
 */
public class Assessment extends BaseEntity {

  /**
   * teacher_id
   */
  @Column(name = "teacher_id", nullable = false)
  private UUID teacherId;

  /**
   * title
   */
  @Size(max = 255)
  @Nationalized
  @Column(name = "title", length = 255, nullable = false)
  private String title;

  /**
   * description
   */
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  /**
   * assessment_type
   */
  @Column(name = "assessment_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private AssessmentType assessmentType;

  /**
   * time_limit_minutes
   */
  @Column(name = "time_limit_minutes")
  private Integer timeLimitMinutes;

  /**
   * passing_score
   */
  @Column(name = "passing_score", precision = 5, scale = 2)
  private BigDecimal passingScore;

  /**
   * start_date
   */
  @Column(name = "start_date")
  private Instant startDate;

  /**
   * end_date
   */
  @Column(name = "end_date")
  private Instant endDate;

  /**
   * randomize_questions
   */
  @Column(name = "randomize_questions")
  private Boolean randomizeQuestions;

  /**
   * show_correct_answers
   */
  @Column(name = "show_correct_answers")
  private Boolean showCorrectAnswers;

  /**
   * assessment_mode
   */
  @Column(name = "assessment_mode")
  @Enumerated(EnumType.STRING)
  private AssessmentMode assessmentMode;

  /**
   * exam_matrix_id
   */
  @Column(name = "exam_matrix_id")
  private UUID examMatrixId;

  /**
   * allow_multiple_attempts
   */
  @Column(name = "allow_multiple_attempts")
  private Boolean allowMultipleAttempts;

  /**
   * max_attempts
   */
  @Column(name = "max_attempts")
  private Integer maxAttempts;

  /**
   * attempt_scoring_policy
   */
  @Column(name = "attempt_scoring_policy")
  @Enumerated(EnumType.STRING)
  private AttemptScoringPolicy attemptScoringPolicy;

  /**
   * show_score_immediately
   */
  @Column(name = "show_score_immediately")
  private Boolean showScoreImmediately;

  /**
   * status
   */
  @Column(name = "status")
  @Enumerated(EnumType.STRING)
  private AssessmentStatus status;

  /** MinIO object key for PDF imported via Cách 2 (bucket: slide-templates). */
  @Column(name = "source_pdf_path", length = 500)
  private String sourcePdfPath;

  /** Original filename from teacher upload. */
  @Size(max = 255)
  @Column(name = "source_pdf_original_name", length = 255)
  private String sourcePdfOriginalName;

  /** JSON: {"pages":[{"pageNumber":1,"text":"..."}, ...]} from PDF OCR wizard. */
  @Column(name = "pdf_import_pages_json", columnDefinition = "TEXT")
  private String pdfImportPagesJson;

  /** Structured blocks for teacher review (question + answer sections). */
  @Column(name = "pdf_import_document_json", columnDefinition = "TEXT")
  private String pdfImportDocumentJson;

  /** Wizard form fields (step 1–2) for detail display. */
  @Column(name = "pdf_import_metadata_json", columnDefinition = "TEXT")
  private String pdfImportMetadataJson;

  /**
   * Relationships
   * - Many-to-One with User (teacher)
   * - One-to-Many with AssessmentQuestion
   * - One-to-Many with AssessmentLesson
   * - One-to-Many with Submission
   * - One-to-Many with QuizAttempt
   * - Many-to-One with ExamMatrix
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
  private User teacher;

  @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<AssessmentQuestion> assessmentQuestions;

  @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<AssessmentLesson> assessmentLessons;

  @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<Submission> submissions;

  @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<QuizAttempt> quizAttempts;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exam_matrix_id", insertable = false, updatable = false)
  private ExamMatrix examMatrix;

  @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<CourseAssessment> courseAssessments;

  @PrePersist
  public void prePersist() {
    super.prePersist();
    if (randomizeQuestions == null) randomizeQuestions = false;
    if (showCorrectAnswers == null) showCorrectAnswers = false;
    if (allowMultipleAttempts == null) allowMultipleAttempts = false;
    if (showScoreImmediately == null) showScoreImmediately = true;
    if (assessmentMode == null) assessmentMode = AssessmentMode.DIRECT;
    if (status == null) status = AssessmentStatus.DRAFT;
  }
}
