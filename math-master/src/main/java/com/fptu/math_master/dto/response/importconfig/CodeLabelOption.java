package com.fptu.math_master.dto.response.importconfig;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeLabelOption {
  private String id;
  private String label;
  /** Optional form fields shown for this scope, e.g. provinceCity, district, schoolName. */
  private List<String> fields;
  /** Short help text for teachers (PDF layout, content mode, …). */
  private String description;
  /** When false, option is visible but not selectable (e.g. LaTeX coming soon). */
  private Boolean enabled;
}
