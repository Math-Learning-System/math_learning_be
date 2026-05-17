package com.fptu.math_master.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.math_master.dto.response.AssessmentImportFormOptionsResponse;
import com.fptu.math_master.dto.response.importconfig.CodeLabelOption;
import com.fptu.math_master.dto.response.importconfig.ProvinceCityOption;
import com.fptu.math_master.exception.AppException;
import com.fptu.math_master.exception.ErrorCode;
import com.fptu.math_master.repository.SystemConfigRepository;
import com.fptu.math_master.service.AssessmentImportConfigService;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AssessmentImportConfigServiceImpl implements AssessmentImportConfigService {

  private static final Pattern SCHOOL_YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");

  ObjectMapper objectMapper;
  SystemConfigRepository systemConfigRepository;

  @Override
  @Transactional(readOnly = true)
  public AssessmentImportFormOptionsResponse getFormOptions() {
    return loadOptions();
  }

  @Override
  public void validateOptionsJson(String configValueJson) {
    try {
      JsonNode root = objectMapper.readTree(configValueJson);
      validateSchoolYears(readStringList(root.path("schoolYears")));
      validateExamTypes(readStringList(root.path("examTypes")));
      validateDepartments(readStringList(root.path("departments")));
      validateExamScopes(readCodeLabels(root.path("examScopes")));
      validateOrganizerTypes(readCodeLabels(root.path("organizerTypes")));
      validateProvinceCities(readProvinceCities(root.path("provinceCities")));
      requireNonBlankText(root.path("adminVersion"), "adminVersion");
      requireNonBlankText(root.path("country"), "country");
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException(ErrorCode.INVALID_KEY, "Cấu hình import đề không hợp lệ: " + ex.getMessage());
    }
  }

  @Override
  public void assertSchoolYearAllowed(String schoolYear) {
    if (schoolYear == null || schoolYear.isBlank()) {
      return;
    }
    String normalized = normalizeSchoolYear(schoolYear);
    if (!loadOptions().getSchoolYears().contains(normalized)) {
      throw new AppException(ErrorCode.INVALID_KEY, "Năm học không nằm trong danh mục được phép");
    }
  }

  @Override
  public void assertExamTypeAllowed(String examType) {
    if (examType == null || examType.isBlank()) {
      return;
    }
    if (!loadOptions().getExamTypes().contains(examType.trim())) {
      throw new AppException(ErrorCode.INVALID_KEY, "Loại đề không nằm trong danh mục được phép");
    }
  }

  @Override
  public void assertDepartmentAllowed(String department) {
    if (department == null || department.isBlank()) {
      return;
    }
    if (!loadOptions().getDepartments().contains(department.trim())) {
      throw new AppException(
          ErrorCode.INVALID_KEY, "Sở/Kỳ thi không nằm trong danh mục được phép");
    }
  }

  @Override
  public void assertExamScopeAllowed(String examScope) {
    if (examScope == null || examScope.isBlank()) {
      return;
    }
    String id = examScope.trim();
    boolean ok =
        loadOptions().getExamScopes().stream().anyMatch(o -> o.getId().equals(id));
    if (!ok) {
      throw new AppException(ErrorCode.INVALID_KEY, "Cấp đề thi không hợp lệ");
    }
  }

  @Override
  public void assertOrganizerTypeAllowed(String organizerType) {
    if (organizerType == null || organizerType.isBlank()) {
      return;
    }
    String id = organizerType.trim();
    boolean ok =
        loadOptions().getOrganizerTypes().stream().anyMatch(o -> o.getId().equals(id));
    if (!ok) {
      throw new AppException(ErrorCode.INVALID_KEY, "Loại đơn vị ra đề không hợp lệ");
    }
  }

  @Override
  public void assertProvinceCityAllowed(String provinceCity) {
    if (provinceCity == null || provinceCity.isBlank()) {
      return;
    }
    String name = provinceCity.trim();
    boolean ok =
        loadOptions().getProvinceCities().stream().anyMatch(p -> p.getName().equals(name));
    if (!ok) {
      throw new AppException(ErrorCode.INVALID_KEY, "Tỉnh/thành phố không nằm trong danh mục");
    }
  }

  private AssessmentImportFormOptionsResponse loadOptions() {
    JsonNode root =
        systemConfigRepository
            .findByConfigKeyAndDeletedAtIsNull(CONFIG_KEY)
            .map(
                c -> {
                  try {
                    return objectMapper.readTree(c.getConfigValue());
                  } catch (Exception ex) {
                    throw new AppException(
                        ErrorCode.INVALID_KEY, "Cấu hình import đề không đọc được JSON");
                  }
                })
            .orElseThrow(
                () ->
                    new AppException(
                        ErrorCode.INVALID_KEY,
                        "Chưa có cấu hình assessment_import_options. Chạy migration V28 hoặc cấu hình qua admin."));

    try {
      List<String> schoolYears = validateSchoolYears(readStringList(root.path("schoolYears")));
      List<String> examTypes = validateExamTypes(readStringList(root.path("examTypes")));
      List<String> departments = normalizeDepartments(readStringList(root.path("departments")));
      List<CodeLabelOption> examScopes = validateExamScopes(readCodeLabels(root.path("examScopes")));
      List<CodeLabelOption> organizerTypes =
          validateOrganizerTypes(readCodeLabels(root.path("organizerTypes")));
      List<ProvinceCityOption> provinceCities =
          validateProvinceCities(readProvinceCities(root.path("provinceCities")));
      String adminVersion = requireNonBlankText(root.path("adminVersion"), "adminVersion");
      String country = requireNonBlankText(root.path("country"), "country");

      return AssessmentImportFormOptionsResponse.builder()
          .schoolYears(schoolYears)
          .examTypes(examTypes)
          .departments(departments)
          .examScopes(examScopes)
          .organizerTypes(organizerTypes)
          .provinceCities(provinceCities)
          .adminVersion(adminVersion)
          .country(country)
          .build();
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException(ErrorCode.INVALID_KEY, "Không đọc được cấu hình import đề");
    }
  }

  private List<CodeLabelOption> readCodeLabels(JsonNode node) {
    List<CodeLabelOption> result = new ArrayList<>();
    if (!node.isArray()) {
      return result;
    }
    node.forEach(
        item -> {
          String id = item.path("id").asText("").trim();
          String label = item.path("label").asText("").trim();
          if (!id.isEmpty() && !label.isEmpty()) {
            List<String> fields = readStringList(item.path("fields"));
            result.add(
                CodeLabelOption.builder().id(id).label(label).fields(fields.isEmpty() ? null : fields).build());
          }
        });
    return result;
  }

  private List<ProvinceCityOption> readProvinceCities(JsonNode node) {
    List<ProvinceCityOption> result = new ArrayList<>();
    if (!node.isArray()) {
      return result;
    }
    node.forEach(
        item -> {
          String name = item.path("name").asText("").trim();
          if (name.isEmpty()) {
            return;
          }
          String type = item.path("type").asText("province").trim();
          if (!"municipality".equals(type) && !"province".equals(type)) {
            type = "province";
          }
          result.add(ProvinceCityOption.builder().name(name).type(type).build());
        });
    return result;
  }

  private List<CodeLabelOption> validateExamScopes(List<CodeLabelOption> scopes) {
    if (scopes == null || scopes.isEmpty()) {
      throw new AppException(ErrorCode.INVALID_KEY, "Phải có ít nhất một cấp đề thi");
    }
    Set<String> seen = new HashSet<>();
    for (CodeLabelOption o : scopes) {
      if (!seen.add(o.getId())) {
        throw new AppException(ErrorCode.INVALID_KEY, "Cấp đề thi bị trùng: " + o.getId());
      }
    }
    return scopes;
  }

  private List<CodeLabelOption> validateOrganizerTypes(List<CodeLabelOption> types) {
    if (types == null || types.isEmpty()) {
      throw new AppException(ErrorCode.INVALID_KEY, "Phải có ít nhất một loại đơn vị ra đề");
    }
    Set<String> seen = new HashSet<>();
    for (CodeLabelOption o : types) {
      if (!seen.add(o.getId())) {
        throw new AppException(ErrorCode.INVALID_KEY, "Loại đơn vị ra đề bị trùng: " + o.getId());
      }
    }
    return types;
  }

  private List<ProvinceCityOption> validateProvinceCities(List<ProvinceCityOption> cities) {
    if (cities == null || cities.isEmpty()) {
      throw new AppException(ErrorCode.INVALID_KEY, "Phải có danh mục tỉnh/thành phố");
    }
    Set<String> seen = new HashSet<>();
    for (ProvinceCityOption p : cities) {
      if (!seen.add(p.getName())) {
        throw new AppException(ErrorCode.INVALID_KEY, "Tỉnh/thành bị trùng: " + p.getName());
      }
    }
    return cities;
  }

  private List<String> validateSchoolYears(List<String> rawYears) {
    if (rawYears == null || rawYears.isEmpty()) {
      throw new AppException(ErrorCode.INVALID_KEY, "Phải có ít nhất một năm học");
    }
    Set<String> seen = new HashSet<>();
    List<String> normalizedList = new ArrayList<>();
    int maxEndYear = Year.now().getValue() + 1;

    for (String raw : rawYears) {
      String normalized = normalizeSchoolYear(raw);
      Matcher matcher = SCHOOL_YEAR_PATTERN.matcher(normalized);
      if (!matcher.matches()) {
        throw new AppException(
            ErrorCode.INVALID_KEY,
            "Năm học phải có dạng YYYY-YYYY (ví dụ 2025-2026): " + raw);
      }
      int startYear = Integer.parseInt(matcher.group(1));
      int endYear = Integer.parseInt(matcher.group(2));
      if (endYear != startYear + 1) {
        throw new AppException(
            ErrorCode.INVALID_KEY, "Năm học phải là hai năm liên tiếp: " + normalized);
      }
      if (endYear > maxEndYear) {
        throw new AppException(
            ErrorCode.INVALID_KEY,
            "Năm học không được vượt quá năm hiện tại (tối đa "
                + (maxEndYear - 1)
                + "-"
                + maxEndYear
                + "): "
                + normalized);
      }
      if (!seen.add(normalized)) {
        throw new AppException(ErrorCode.INVALID_KEY, "Năm học bị trùng: " + normalized);
      }
      normalizedList.add(normalized);
    }
    normalizedList.sort(String::compareTo);
    return normalizedList;
  }

  private List<String> validateExamTypes(List<String> rawTypes) {
    if (rawTypes == null || rawTypes.isEmpty()) {
      throw new AppException(ErrorCode.INVALID_KEY, "Phải có ít nhất một loại đề");
    }
    Set<String> seen = new HashSet<>();
    List<String> result = new ArrayList<>();
    for (String raw : rawTypes) {
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.isEmpty()) {
        throw new AppException(ErrorCode.INVALID_KEY, "Loại đề không được để trống");
      }
      if (!seen.add(trimmed)) {
        throw new AppException(ErrorCode.INVALID_KEY, "Loại đề bị trùng: " + trimmed);
      }
      result.add(trimmed);
    }
    return result;
  }

  private List<String> validateDepartments(List<String> rawTypes) {
    if (rawTypes == null || rawTypes.isEmpty()) {
      throw new AppException(ErrorCode.INVALID_KEY, "Phải có ít nhất một Sở/Kỳ thi");
    }
    return normalizeDepartments(rawTypes);
  }

  private List<String> normalizeDepartments(List<String> rawTypes) {
    if (rawTypes == null || rawTypes.isEmpty()) {
      return List.of();
    }
    Set<String> seen = new HashSet<>();
    List<String> result = new ArrayList<>();
    for (String raw : rawTypes) {
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (!seen.add(trimmed)) {
        throw new AppException(ErrorCode.INVALID_KEY, "Sở/Kỳ thi bị trùng: " + trimmed);
      }
      result.add(trimmed);
    }
    return result;
  }

  private String normalizeSchoolYear(String raw) {
    return raw.trim().replaceAll("\\s+", "");
  }

  private List<String> readStringList(JsonNode node) {
    List<String> result = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(item -> {
        if (!item.asText().isBlank()) {
          result.add(item.asText().trim());
        }
      });
    }
    return result;
  }

  private String requireNonBlankText(JsonNode node, String fieldName) {
    if (node == null || node.isMissingNode() || node.asText().isBlank()) {
      throw new AppException(
          ErrorCode.INVALID_KEY, "Thiếu hoặc rỗng trường bắt buộc: " + fieldName);
    }
    return node.asText().trim();
  }

  @Override
  public String resolveProvinceCityType(String provinceCityName) {
    if (provinceCityName == null || provinceCityName.isBlank()) {
      return null;
    }
    return loadOptions().getProvinceCities().stream()
        .filter(p -> p.getName().equals(provinceCityName.trim()))
        .map(ProvinceCityOption::getType)
        .findFirst()
        .orElse(null);
  }
}
