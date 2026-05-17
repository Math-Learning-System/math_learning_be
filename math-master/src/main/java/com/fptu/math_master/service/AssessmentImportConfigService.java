package com.fptu.math_master.service;

import com.fptu.math_master.dto.response.AssessmentImportFormOptionsResponse;

public interface AssessmentImportConfigService {

  String CONFIG_KEY = "assessment_import_options";

  AssessmentImportFormOptionsResponse getFormOptions();

  /** Validates JSON payload before persisting to system_config. */
  void validateOptionsJson(String configValueJson);

  void assertSchoolYearAllowed(String schoolYear);

  void assertExamTypeAllowed(String examType);

  void assertDepartmentAllowed(String department);

  void assertExamScopeAllowed(String examScope);

  void assertOrganizerTypeAllowed(String organizerType);

  void assertProvinceCityAllowed(String provinceCity);

  void assertPdfLayoutAllowed(String pdfLayout);

  void assertImportContentModeAllowed(String importContentMode);

  /** municipality | province, or null if unknown. */
  String resolveProvinceCityType(String provinceCityName);
}
