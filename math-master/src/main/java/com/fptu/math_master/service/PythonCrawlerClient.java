package com.fptu.math_master.service;

import com.fptu.math_master.dto.request.UpdateLessonPageRequest;
import com.fptu.math_master.dto.response.LessonPageHistoryEntryResponse;
import com.fptu.math_master.dto.response.LessonPageResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Typed HTTP client for the Python crawler/OCR service. All MongoDB access happens through this
 * client — the Java BE never reads MongoDB directly. The contract here is the negotiated boundary
 * between BE and the Python service (see Phase 3 for Python implementation).
 */
public interface PythonCrawlerClient {

  /** Request body for {@link #triggerOcrWithMapping}. */
  record OcrTriggerRequest(
      UUID bookId,
      String pdfPath,
      Integer ocrPageFrom,
      Integer ocrPageTo,
      List<MappingItem> mappings) {

    public record MappingItem(UUID lessonId, Integer pageStart, Integer pageEnd) {}
  }

  /** Response from {@link #triggerOcrWithMapping}. */
  record OcrTriggerResult(String status, String message, Integer totalPagesQueued) {}

  /** Request body for {@link #triggerSinglePageOcr}. Built server-side from Postgres book + mapping. */
  record OcrSinglePageTriggerRequest(
      UUID bookId,
      UUID lessonId,
      Integer pageNumber,
      String pdfPath,
      Integer ocrPageFrom,
      Integer ocrPageTo,
      List<OcrTriggerRequest.MappingItem> mappings) {}

  /**
   * Asynchronously starts OCR for a book using the provided lesson→page mapping. Returns
   * immediately; poll {@link #getBookOcrStatus(UUID)} for progress.
   */
  OcrTriggerResult triggerOcrWithMapping(OcrTriggerRequest request);

  /**
   * Re-OCR one mapped PDF page for one lesson (verify wizard). Does not flip Postgres book status to
   * OCR_RUNNING.
   */
  OcrTriggerResult triggerSinglePageOcr(OcrSinglePageTriggerRequest request);

  /** Cooperatively asks the Python pipeline to stop (Mongo cancel flag). Safe if idle. */
  void cancelOcr(UUID bookId);

  /**
   * Snapshot from Mongo/Python while OCR runs — includes coarse pipeline phase and counters for the
   * admin UI.
   */
  record OcrStatus(
      String status,
      Integer processedPages,
      Integer totalPages,
      String errorMessage,
      Integer progressPercent,
      String currentPhase) {}

  OcrStatus getBookOcrStatus(UUID bookId);

  /** All OCR'd pages for a (book, lesson). Empty list if OCR has not run yet. */
  List<LessonPageResponse> getPagesByBookAndLesson(UUID bookId, UUID lessonId);

  /**
   * All OCR'd pages for a lesson, optionally filtered by book. When {@code bookId} is null, returns
   * pages from every book that maps to this lesson — used by the Gemini prompt builder when it
   * doesn't care which book the content came from.
   */
  List<LessonPageResponse> getPagesByLesson(UUID lessonId, UUID bookId);

  Optional<LessonPageResponse> getPage(UUID bookId, UUID lessonId, int pageNumber);

  List<LessonPageHistoryEntryResponse> getPageHistory(
      UUID bookId, UUID lessonId, int pageNumber, int limit);

  /** Patch one page (content blocks and/or verified flag). Returns the updated page. */
  LessonPageResponse updatePage(
      UUID bookId, UUID lessonId, int pageNumber, UpdateLessonPageRequest request, UUID actorId);

  /** Drops every OCR'd page for a book (called on book delete). */
  void deleteAllPagesForBook(UUID bookId);

  /** True iff every page of every (book, lesson) has verified=true. */
  boolean isBookFullyVerified(UUID bookId);

  /**
   * Mathpix per-page OCR via Python (no Gemini). Used for assessment PDF import (Cách 2).
   */
  com.fptu.math_master.dto.response.AssessmentPdfExtractResponse extractAssessmentFromPdf(
      org.springframework.web.multipart.MultipartFile file, String pdfLayout, String sourceFileName);

  com.fptu.math_master.dto.response.AssessmentPdfInfoResponse getAssessmentPdfInfo(
      org.springframework.web.multipart.MultipartFile file, String fileKey, String draftId);

  com.fptu.math_master.dto.response.AssessmentPdfOcrPageResponse ocrAssessmentPdfPage(
      org.springframework.web.multipart.MultipartFile file,
      int pageNumber,
      String fileKey,
      String draftId);

  com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse getPdfImportDraft(
      String draftId);

  com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse getPdfImportDraftByFileKey(
      String fileKey);
}
