package com.fptu.math_master.dto.request;

import com.fptu.math_master.enums.AssessmentType;
import java.util.UUID;
import lombok.Builder;

/** Teacher-provided metadata before PDF AI extraction. */
@Builder(toBuilder = true)
public record PdfAssessmentImportFormInput(
    String examTitle,
    String schoolYear,
    /** Legacy alias; also used as organizer preset from dropdown. */
    String department,
    String examDate,
    String examType,
    UUID schoolGradeId,
    UUID subjectId,
    String contextHint,
    UUID questionBankId,
    AssessmentType assessmentType,
    Integer timeLimitMinutes,
    String examScope,
    String organizerName,
    String organizerType,
    String provinceCity,
    String district,
    String schoolName,
    String country,
    /** Admin-configured id: questions_only | questions_with_answers */
    String pdfLayout,
    /** Admin-configured id: pdf | latex */
    String importContentMode,
    /** JSON {@link com.fptu.math_master.dto.response.AssessmentPdfExtractResponse} from wizard step 2 OCR — skips re-extract on import. */
    String preExtractedJson) {}
