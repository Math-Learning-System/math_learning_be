package com.fptu.math_master.dto.request;

import com.fptu.math_master.dto.response.ContentBlockDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssessmentPdfImportDocumentRequest {
  private List<ContentBlockDto> questionBlocks;
  private List<ContentBlockDto> answerBlocks;
}
