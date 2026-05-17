package com.fptu.math_master.dto.response;

import com.fptu.math_master.dto.response.importconfig.CodeLabelOption;
import com.fptu.math_master.dto.response.importconfig.ProvinceCityOption;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentImportFormOptionsResponse {
  private List<String> schoolYears;
  private List<String> examTypes;
  /** Preset organizer names (Sở, Bộ, trường...) for dropdown. */
  private List<String> departments;
  private List<CodeLabelOption> examScopes;
  private List<CodeLabelOption> organizerTypes;
  private List<ProvinceCityOption> provinceCities;
  /** e.g. VN_34_PROVINCES_2025 */
  private String adminVersion;
  private String country;
  /** Admin-configured PDF document shapes (questions only vs questions + answers). */
  private List<CodeLabelOption> pdfLayouts;
  /** How to process uploaded content: pdf (active) or latex (future). */
  private List<CodeLabelOption> importContentModes;
}
