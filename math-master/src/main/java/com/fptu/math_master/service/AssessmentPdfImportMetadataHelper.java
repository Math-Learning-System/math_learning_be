package com.fptu.math_master.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.math_master.dto.request.PdfAssessmentImportFormInput;
import com.fptu.math_master.dto.request.UpdateAssessmentPdfImportMetadataRequest;
import com.fptu.math_master.dto.response.AssessmentImportFormOptionsResponse;
import com.fptu.math_master.dto.response.AssessmentPdfImportMetadataResponse;
import com.fptu.math_master.dto.response.importconfig.CodeLabelOption;
import com.fptu.math_master.dto.response.pdfimport.PdfImportedExamDto;
import com.fptu.math_master.enums.AssessmentType;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public final class AssessmentPdfImportMetadataHelper {

  private AssessmentPdfImportMetadataHelper() {}

  public static AssessmentPdfImportMetadataResponse parseMetadataJson(
      String json, ObjectMapper objectMapper) {
    if (!StringUtils.hasText(json)) {
      return null;
    }
    try {
      return objectMapper.readValue(json, AssessmentPdfImportMetadataResponse.class);
    } catch (Exception ex) {
      log.warn("Invalid pdf_import_metadata_json: {}", ex.getMessage());
      return null;
    }
  }

  public static String toJson(
      AssessmentPdfImportMetadataResponse metadata, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not serialize pdf import metadata", ex);
    }
  }

  public static AssessmentPdfImportMetadataResponse buildFromImport(
      PdfAssessmentImportFormInput form,
      PdfImportedExamDto exam,
      String sourceFileName,
      String schoolGradeName,
      String subjectName,
      String questionBankName,
      AssessmentImportFormOptionsResponse options) {
    AssessmentImportFormOptionsResponse opts =
        options != null ? options : new AssessmentImportFormOptionsResponse();

    String examScope = firstNonBlank(form != null ? form.examScope() : null, exam != null ? exam.getExamScope() : null);
    String organizerType =
        firstNonBlank(form != null ? form.organizerType() : null, exam != null ? exam.getOrganizerType() : null);
    String pdfLayout = form != null ? form.pdfLayout() : null;
    String importContentMode = form != null ? form.importContentMode() : null;
    AssessmentType aType = form != null ? form.assessmentType() : null;

    Integer minutes =
        form != null && form.timeLimitMinutes() != null
            ? form.timeLimitMinutes()
            : exam != null ? exam.getDurationMinutes() : null;

    return AssessmentPdfImportMetadataResponse.builder()
        .examTitle(firstNonBlank(form != null ? form.examTitle() : null, exam != null ? exam.getExamTitle() : null))
        .schoolYear(firstNonBlank(form != null ? form.schoolYear() : null, exam != null ? exam.getSchoolYear() : null))
        .examType(firstNonBlank(form != null ? form.examType() : null, exam != null ? exam.getExamType() : null))
        .examScope(examScope)
        .examScopeLabel(resolveCodeLabel(opts.getExamScopes(), examScope))
        .organizerType(organizerType)
        .organizerTypeLabel(resolveCodeLabel(opts.getOrganizerTypes(), organizerType))
        .provinceCity(
            firstNonBlank(form != null ? form.provinceCity() : null, exam != null ? exam.getProvinceCity() : null))
        .district(firstNonBlank(form != null ? form.district() : null, exam != null ? exam.getDistrict() : null))
        .schoolName(
            firstNonBlank(form != null ? form.schoolName() : null, exam != null ? exam.getSchoolName() : null))
        .department(
            firstNonBlank(
                form != null ? form.department() : null,
                exam != null ? firstNonBlank(exam.getDepartment(), exam.getOrganizerName()) : null))
        .organizerName(
            firstNonBlank(
                form != null ? form.organizerName() : null, exam != null ? exam.getOrganizerName() : null))
        .examDate(firstNonBlank(form != null ? form.examDate() : null, exam != null ? exam.getExamDate() : null))
        .schoolGradeName(schoolGradeName)
        .subjectName(
            firstNonBlank(subjectName, exam != null ? exam.getSubject() : null))
        .contextHint(form != null ? form.contextHint() : null)
        .questionBankName(questionBankName)
        .assessmentType(aType != null ? aType.name() : null)
        .assessmentTypeLabel(aType != null ? formatAssessmentType(aType) : null)
        .timeLimitMinutes(minutes)
        .pdfLayout(pdfLayout)
        .pdfLayoutLabel(resolveCodeLabel(opts.getPdfLayouts(), pdfLayout))
        .importContentMode(importContentMode)
        .importContentModeLabel(resolveCodeLabel(opts.getImportContentModes(), importContentMode))
        .sourceFileName(sourceFileName)
        .build();
  }

  private static String resolveCodeLabel(List<CodeLabelOption> options, String id) {
    if (!StringUtils.hasText(id) || options == null) {
      return null;
    }
    for (CodeLabelOption opt : options) {
      if (opt != null && id.equals(opt.getId())) {
        return opt.getLabel();
      }
    }
    return id;
  }

  private static String formatAssessmentType(AssessmentType type) {
    return switch (type) {
      case QUIZ -> "Kiểm tra";
      case TEST -> "Bài kiểm tra";
      case EXAM -> "Đề thi";
      case HOMEWORK -> "Bài tập về nhà";
    };
  }

  private static String firstNonBlank(String preferred, String fallback) {
    if (StringUtils.hasText(preferred)) {
      return preferred.trim();
    }
    if (StringUtils.hasText(fallback)) {
      return fallback.trim();
    }
    return null;
  }

  public static AssessmentPdfImportMetadataResponse mergeUpdate(
      AssessmentPdfImportMetadataResponse base,
      UpdateAssessmentPdfImportMetadataRequest req,
      AssessmentImportFormOptionsResponse options,
      String schoolGradeName,
      String subjectName,
      String questionBankName) {
    AssessmentPdfImportMetadataResponse current =
        base != null ? base : AssessmentPdfImportMetadataResponse.builder().build();
    if (req == null) {
      return current;
    }
    AssessmentImportFormOptionsResponse opts =
        options != null ? options : new AssessmentImportFormOptionsResponse();

    String examScope = pick(req.getExamScope(), current.getExamScope());
    String organizerType = pick(req.getOrganizerType(), current.getOrganizerType());
    String pdfLayout = pick(req.getPdfLayout(), current.getPdfLayout());
    String importContentMode = pick(req.getImportContentMode(), current.getImportContentMode());
    AssessmentType aType = req.getAssessmentType() != null ? req.getAssessmentType() : parseType(current.getAssessmentType());
    Integer minutes =
        req.getTimeLimitMinutes() != null ? req.getTimeLimitMinutes() : current.getTimeLimitMinutes();

    return AssessmentPdfImportMetadataResponse.builder()
        .examTitle(pick(req.getExamTitle(), current.getExamTitle()))
        .schoolYear(pick(req.getSchoolYear(), current.getSchoolYear()))
        .examType(pick(req.getExamType(), current.getExamType()))
        .examScope(examScope)
        .examScopeLabel(resolveCodeLabel(opts.getExamScopes(), examScope))
        .organizerType(organizerType)
        .organizerTypeLabel(resolveCodeLabel(opts.getOrganizerTypes(), organizerType))
        .provinceCity(pick(req.getProvinceCity(), current.getProvinceCity()))
        .district(pick(req.getDistrict(), current.getDistrict()))
        .schoolName(pick(req.getSchoolName(), current.getSchoolName()))
        .department(
            pick(
                firstNonBlank(req.getDepartment(), req.getOrganizerName()),
                firstNonBlank(current.getDepartment(), current.getOrganizerName())))
        .organizerName(
            pick(
                firstNonBlank(req.getOrganizerName(), req.getDepartment()),
                firstNonBlank(current.getOrganizerName(), current.getDepartment())))
        .examDate(pick(req.getExamDate(), current.getExamDate()))
        .schoolGradeName(
            firstNonBlank(schoolGradeName, current.getSchoolGradeName()))
        .subjectName(firstNonBlank(subjectName, current.getSubjectName()))
        .contextHint(req.getContextHint() != null ? req.getContextHint() : current.getContextHint())
        .questionBankName(firstNonBlank(questionBankName, current.getQuestionBankName()))
        .assessmentType(aType != null ? aType.name() : current.getAssessmentType())
        .assessmentTypeLabel(aType != null ? formatAssessmentType(aType) : current.getAssessmentTypeLabel())
        .timeLimitMinutes(minutes)
        .pdfLayout(pdfLayout)
        .pdfLayoutLabel(resolveCodeLabel(opts.getPdfLayouts(), pdfLayout))
        .importContentMode(importContentMode)
        .importContentModeLabel(resolveCodeLabel(opts.getImportContentModes(), importContentMode))
        .sourceFileName(current.getSourceFileName())
        .build();
  }

  private static String pick(String requested, String current) {
    return requested != null ? requested.trim() : current;
  }

  private static AssessmentType parseType(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      return AssessmentType.valueOf(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /** Partial metadata when legacy imports have no {@code pdf_import_metadata_json}. */
  public static AssessmentPdfImportMetadataResponse buildFallbackFromAssessment(
      com.fptu.math_master.entity.Assessment assessment) {
    if (assessment == null) {
      return null;
    }
    AssessmentType type = assessment.getAssessmentType();
    return AssessmentPdfImportMetadataResponse.builder()
        .examTitle(assessment.getTitle())
        .timeLimitMinutes(assessment.getTimeLimitMinutes())
        .assessmentType(type != null ? type.name() : null)
        .assessmentTypeLabel(type != null ? formatAssessmentType(type) : null)
        .sourceFileName(assessment.getSourcePdfOriginalName())
        .build();
  }
}
