package com.fptu.math_master.service;

import com.fptu.math_master.dto.request.PdfAssessmentImportFormInput;
import com.fptu.math_master.dto.response.AssessmentImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AssessmentImportService {

  AssessmentImportResponse importAssessmentFromPdf(
      MultipartFile file, PdfAssessmentImportFormInput form);
}
