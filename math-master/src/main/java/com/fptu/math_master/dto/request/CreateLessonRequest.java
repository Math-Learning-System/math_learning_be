package com.fptu.math_master.dto.request;

import com.fptu.math_master.enums.LessonDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLessonRequest {
  @NotNull(message = "Chapter ID is required")
  private UUID chapterId;

  @NotBlank(message = "Title is required")
  private String title;

  private String learningObjectives;

  /** Optional at creation; may be filled later. */
  private String lessonContent;

  private String summary;

  private Integer orderIndex;

  private Integer durationMinutes;

  private LessonDifficulty difficulty;
}
