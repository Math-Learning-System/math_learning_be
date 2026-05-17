package com.fptu.math_master.dto.response.importconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceCityOption {
  /** Display name, e.g. Hà Nội */
  private String name;
  /** municipality | province */
  private String type;
}
