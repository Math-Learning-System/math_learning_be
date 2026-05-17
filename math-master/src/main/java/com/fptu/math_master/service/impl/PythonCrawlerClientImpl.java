package com.fptu.math_master.service.impl;

import com.fptu.math_master.configuration.properties.MinioProperties;
import com.fptu.math_master.dto.request.UpdateLessonPageRequest;
import com.fptu.math_master.dto.response.ContentBlockDto;
import com.fptu.math_master.dto.response.AssessmentPdfExtractResponse;
import com.fptu.math_master.dto.response.LessonPageHistoryEntryResponse;
import com.fptu.math_master.dto.response.LessonPageResponse;
import com.fptu.math_master.exception.AppException;
import com.fptu.math_master.exception.ErrorCode;
import com.fptu.math_master.service.PythonCrawlerClient;
import com.fptu.math_master.service.UploadService;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
public class PythonCrawlerClientImpl implements PythonCrawlerClient {

  private static final String BASE = "/api/v1";
  private static final String STATIC_PREFIX = "/static/";
  private static final String STATIC_PROXY_PREFIX = "/api/v1/crawl-data/static/";

  private final RestClient restClient;
  private final RestClient longRestClient;
  private final UploadService uploadService;
  private final MinioProperties minioProperties;

  public PythonCrawlerClientImpl(
      @Qualifier("crawlDataRestClient") RestClient restClient,
      @Qualifier("crawlDataLongRestClient") RestClient longRestClient,
      UploadService uploadService,
      MinioProperties minioProperties) {
    this.restClient = restClient;
    this.longRestClient = longRestClient;
    this.uploadService = uploadService;
    this.minioProperties = minioProperties;
  }

  @Override
  public com.fptu.math_master.dto.response.AssessmentPdfInfoResponse getAssessmentPdfInfo(
      org.springframework.web.multipart.MultipartFile file, String fileKey, String draftId) {
    long startedMs = System.currentTimeMillis();
    String filename = safePdfFilename(file);
    log.info("Assessment PDF import [pdf-info]: đếm trang — file={}", filename);
    String ownerId = currentUserIdString();
    com.fptu.math_master.dto.response.AssessmentPdfInfoResponse result =
        postAssessmentPdfMultipart(
            "/assessments/pdf-info",
            file,
            b -> addPdfDraftFields(b, ownerId, fileKey, draftId),
            com.fptu.math_master.dto.response.AssessmentPdfInfoResponse.class,
            "Không đọc được thông tin PDF");
    log.info(
        "Assessment PDF import [pdf-info]: xong — {} trang, file={}, {}ms",
        result.getTotalPages(),
        result.getSourceFile() != null ? result.getSourceFile() : filename,
        System.currentTimeMillis() - startedMs);
    return result;
  }

  @Override
  public com.fptu.math_master.dto.response.AssessmentPdfOcrPageResponse ocrAssessmentPdfPage(
      org.springframework.web.multipart.MultipartFile file,
      int pageNumber,
      String fileKey,
      String draftId) {
    long startedMs = System.currentTimeMillis();
    String filename = safePdfFilename(file);
    log.info(
        "Assessment PDF import [ocr-pdf-page]: bắt đầu trang {} — file={}",
        pageNumber,
        filename);
    String ownerId = currentUserIdString();
    com.fptu.math_master.dto.response.AssessmentPdfOcrPageResponse result =
        postAssessmentPdfMultipart(
            "/assessments/ocr-pdf-page",
            file,
            b -> {
              b.add("pageNumber", String.valueOf(pageNumber));
              addPdfDraftFields(b, ownerId, fileKey, draftId);
            },
            com.fptu.math_master.dto.response.AssessmentPdfOcrPageResponse.class,
            "OCR trang PDF thất bại");
    int textLen = result.getText() != null ? result.getText().length() : 0;
    log.info(
        "Assessment PDF import [ocr-pdf-page]: xong trang {} — {}ms, success={}, source={}, confidence={}, chars={}",
        pageNumber,
        System.currentTimeMillis() - startedMs,
        result.isSuccess(),
        result.getOcrSource(),
        result.getConfidence(),
        textLen);
    return result;
  }

  @Override
  public com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse getPdfImportDraft(
      String draftId) {
    String ownerId = currentUserIdString();
    try {
      com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse result =
          longRestClient
              .get()
              .uri(
                  BASE
                      + "/assessments/pdf-import-draft/{draftId}?ownerId={ownerId}",
                  draftId,
                  ownerId)
              .retrieve()
              .body(com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse.class);
      if (result == null) {
        throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
      }
      return result;
    } catch (org.springframework.web.client.RestClientResponseException ex) {
      if (ex.getStatusCode() == org.springframework.http.HttpStatus.NOT_FOUND) {
        return null;
      }
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE, parsePythonDetail(ex.getResponseBodyAsString()));
    } catch (Exception ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE, ex.getMessage());
    }
  }

  @Override
  public com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse
      getPdfImportDraftByFileKey(String fileKey) {
    String ownerId = currentUserIdString();
    try {
      return longRestClient
          .get()
          .uri(
              BASE + "/assessments/pdf-import-draft?ownerId={ownerId}&fileKey={fileKey}",
              ownerId,
              fileKey)
          .retrieve()
          .body(com.fptu.math_master.dto.response.AssessmentPdfImportDraftResponse.class);
    } catch (org.springframework.web.client.RestClientResponseException ex) {
      if (ex.getStatusCode() == org.springframework.http.HttpStatus.NOT_FOUND) {
        return null;
      }
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE, parsePythonDetail(ex.getResponseBodyAsString()));
    } catch (Exception ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE, ex.getMessage());
    }
  }

  private static void addPdfDraftFields(
      org.springframework.util.MultiValueMap<String, Object> body,
      String ownerId,
      String fileKey,
      String draftId) {
    if (ownerId != null && !ownerId.isBlank()) {
      body.add("ownerId", ownerId);
    }
    if (fileKey != null && !fileKey.isBlank()) {
      body.add("fileKey", fileKey);
    }
    if (draftId != null && !draftId.isBlank()) {
      body.add("draftId", draftId);
    }
  }

  private static String currentUserIdString() {
    return com.fptu.math_master.util.SecurityUtils.getCurrentUserId().toString();
  }

  private static String safePdfFilename(org.springframework.web.multipart.MultipartFile file) {
    if (file == null) {
      return "exam.pdf";
    }
    String name = file.getOriginalFilename();
    return name != null && !name.isBlank() ? name : "exam.pdf";
  }

  private <T> T postAssessmentPdfMultipart(
      String path,
      org.springframework.web.multipart.MultipartFile file,
      java.util.function.Consumer<org.springframework.util.MultiValueMap<String, Object>> extra,
      Class<T> responseType,
      String errorPrefix) {
    try {
      byte[] bytes = file.getBytes();
      String filename = file.getOriginalFilename();
      org.springframework.core.io.ByteArrayResource resource =
          new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
              return filename != null ? filename : "exam.pdf";
            }
          };
      org.springframework.util.LinkedMultiValueMap<String, Object> body =
          new org.springframework.util.LinkedMultiValueMap<>();
      body.add("file", resource);
      if (extra != null) {
        extra.accept(body);
      }
      T result =
          longRestClient
              .post()
              .uri(BASE + path)
              .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
              .body(body)
              .retrieve()
              .body(responseType);
      if (result == null) {
        throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
      }
      return result;
    } catch (AppException ex) {
      throw ex;
    } catch (org.springframework.web.client.ResourceAccessException ex) {
      log.error("Python service unreachable for {}", path, ex);
      throw new AppException(
          ErrorCode.CRAWLER_UNAVAILABLE,
          "Không kết nối được service Python. Chạy math_learning_AI trên cổng 8001.");
    } catch (org.springframework.web.client.RestClientResponseException ex) {
      log.error("Python {} returned {}: {}", path, ex.getStatusCode(), ex.getResponseBodyAsString());
      String detail = parsePythonDetail(ex.getResponseBodyAsString());
      if (ex.getStatusCode() == org.springframework.http.HttpStatus.BAD_REQUEST) {
        throw new AppException(ErrorCode.INVALID_KEY, detail);
      }
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE, errorPrefix + ": " + detail);
    } catch (Exception ex) {
      log.error("{} failed", path, ex);
      throw new AppException(ErrorCode.INVALID_KEY, errorPrefix + ": " + ex.getMessage());
    }
  }

  @Override
  public AssessmentPdfExtractResponse extractAssessmentFromPdf(
      MultipartFile file, String pdfLayout, String sourceFileName) {
    long startedMs = System.currentTimeMillis();
    String filename = safePdfFilename(file);
    log.info(
        "Assessment PDF import [extract-pdf]: gọi Python OCR toàn bộ — file={}, layout={}",
        filename,
        pdfLayout);
    try {
      byte[] bytes = file.getBytes();
      String uploadFilename =
          sourceFileName != null && !sourceFileName.isBlank()
              ? sourceFileName
              : file.getOriginalFilename();
      ByteArrayResource resource =
          new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
              return uploadFilename != null ? uploadFilename : "exam.pdf";
            }
          };

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", resource);
      body.add("pdfLayout", pdfLayout != null ? pdfLayout : "questions_only");
      if (sourceFileName != null && !sourceFileName.isBlank()) {
        body.add("sourceFileName", sourceFileName);
      }

      AssessmentPdfExtractResponse result =
          longRestClient
              .post()
              .uri(BASE + "/assessments/extract-pdf")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body)
              .retrieve()
              .body(AssessmentPdfExtractResponse.class);
      if (result == null) {
        throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
      }
      int questionCount =
          result.getQuestions() != null ? result.getQuestions().size() : 0;
      log.info(
          "Assessment PDF import [extract-pdf]: xong — {}ms, questions={}, confidence={}, warnings={}",
          System.currentTimeMillis() - startedMs,
          questionCount,
          result.getConfidenceScore(),
          result.getWarnings() != null ? result.getWarnings().size() : 0);
      return result;
    } catch (AppException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      log.error("Python service unreachable for assessment PDF extract", ex);
      throw new AppException(
          ErrorCode.CRAWLER_UNAVAILABLE,
          "Không kết nối được service Python (Mathpix). Chạy math_learning_AI trên cổng 8001.");
    } catch (RestClientResponseException ex) {
      log.error(
          "Python assessment extract returned {}: {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString());
      String detail = ex.getResponseBodyAsString();
      if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
        throw new AppException(ErrorCode.INVALID_KEY, parsePythonDetail(detail));
      }
      throw new AppException(
          ErrorCode.CRAWLER_UNAVAILABLE,
          "Trích PDF qua Python thất bại: " + parsePythonDetail(detail));
    } catch (Exception ex) {
      log.error("Assessment PDF extract failed", ex);
      throw new AppException(ErrorCode.INVALID_KEY, "Trích PDF thất bại: " + ex.getMessage());
    }
  }

  private String parsePythonDetail(String body) {
    if (body == null || body.isBlank()) {
      return "Lỗi không xác định từ Python";
    }
    if (body.contains("\"detail\"")) {
      int idx = body.indexOf("\"detail\"");
      int start = body.indexOf(':', idx) + 1;
      int q1 = body.indexOf('"', start);
      int q2 = body.indexOf('"', q1 + 1);
      if (q1 >= 0 && q2 > q1) {
        return body.substring(q1 + 1, q2);
      }
    }
    return body.length() > 300 ? body.substring(0, 300) : body;
  }

  @Override
  public OcrTriggerResult triggerOcrWithMapping(OcrTriggerRequest request) {
    try {
      OcrTriggerResult result =
          restClient
              .post()
              .uri(BASE + "/books/{bookId}/ocr-with-mapping", request.bookId())
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(OcrTriggerResult.class);
      return result == null ? new OcrTriggerResult("UNKNOWN", null, 0) : result;
    } catch (ResourceAccessException ex) {
      log.error("Crawler unreachable when triggering OCR for book {}", request.bookId(), ex);
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    } catch (RestClientResponseException ex) {
      log.error(
          "Crawler returned {} for OCR trigger of book {}: {}",
          ex.getStatusCode(),
          request.bookId(),
          ex.getResponseBodyAsString());
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    }
  }

  @Override
  public OcrTriggerResult triggerSinglePageOcr(OcrSinglePageTriggerRequest request) {
    try {
      OcrTriggerResult result =
          restClient
              .post()
              .uri(BASE + "/books/{bookId}/ocr-single-page", request.bookId())
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(OcrTriggerResult.class);
      return result == null ? new OcrTriggerResult("UNKNOWN", null, 0) : result;
    } catch (ResourceAccessException ex) {
      log.error(
          "Crawler unreachable when triggering single-page OCR for book {}", request.bookId(), ex);
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    } catch (RestClientResponseException ex) {
      log.error(
          "Crawler returned {} for single-page OCR of book {}: {}",
          ex.getStatusCode(),
          request.bookId(),
          ex.getResponseBodyAsString());
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    }
  }

  @Override
  public void cancelOcr(UUID bookId) {
    try {
      restClient
          .post()
          .uri(BASE + "/books/{bookId}/ocr-cancel", bookId)
          .retrieve()
          .toBodilessEntity();
    } catch (ResourceAccessException ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    } catch (RestClientResponseException ex) {
      log.warn(
          "Crawler returned {} for OCR cancel of book {}: {}",
          ex.getStatusCode(),
          bookId,
          ex.getResponseBodyAsString());
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    }
  }

  @Override
  public OcrStatus getBookOcrStatus(UUID bookId) {
    try {
      OcrStatus status =
          restClient
              .get()
              .uri(BASE + "/books/{bookId}/ocr-status", bookId)
              .retrieve()
              .body(OcrStatus.class);
      return status == null ? new OcrStatus("UNKNOWN", 0, 0, null, null, null) : status;
    } catch (ResourceAccessException ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return new OcrStatus("NOT_STARTED", 0, 0, null, null, null);
      }
      log.warn(
          "Crawler returned {} for OCR status book={}: {}",
          ex.getStatusCode(),
          bookId,
          shortResponseBody(ex));
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    } catch (RestClientException ex) {
      log.warn("Rest client error reading OCR status book={}: {}", bookId, ex.getMessage());
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    }
  }

  @Override
  public List<LessonPageResponse> getPagesByBookAndLesson(UUID bookId, UUID lessonId) {
    try {
      List<LessonPageResponse> pages =
          restClient
              .get()
              .uri(BASE + "/books/{bookId}/lessons/{lessonId}/pages", bookId, lessonId)
              .retrieve()
              .body(new ParameterizedTypeReference<List<LessonPageResponse>>() {});
      return pages == null ? Collections.emptyList() : normalizePages(pages);
    } catch (ResourceAccessException ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Collections.emptyList();
      }
      log.warn(
          "Crawler returned {} listing pages for book={}, lesson={}: {}",
          ex.getStatusCode(),
          bookId,
          lessonId,
          shortResponseBody(ex));
      return Collections.emptyList();
    } catch (RestClientException ex) {
      log.warn(
          "Rest client error listing pages for book={}, lesson={}: {}",
          bookId,
          lessonId,
          ex.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public List<LessonPageResponse> getPagesByLesson(UUID lessonId, UUID bookId) {
    try {
      List<LessonPageResponse> pages =
          restClient
              .get()
              .uri(
                  uriBuilder -> {
                    var b = uriBuilder.path(BASE + "/lessons/{lessonId}/pages");
                    if (bookId != null) {
                      b.queryParam("book_id", bookId);
                    }
                    return b.build(lessonId);
                  })
              .retrieve()
              .body(new ParameterizedTypeReference<List<LessonPageResponse>>() {});
      return pages == null ? Collections.emptyList() : normalizePages(pages);
    } catch (ResourceAccessException ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Collections.emptyList();
      }
      log.warn(
          "Crawler returned {} listing pages for lesson={}, bookId={}: {}",
          ex.getStatusCode(),
          lessonId,
          bookId,
          shortResponseBody(ex));
      return Collections.emptyList();
    } catch (RestClientException ex) {
      log.warn(
          "Rest client error listing pages for lesson={}, bookId={}: {}",
          lessonId,
          bookId,
          ex.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public Optional<LessonPageResponse> getPage(UUID bookId, UUID lessonId, int pageNumber) {
    try {
      LessonPageResponse page =
          restClient
              .get()
              .uri(
                  BASE + "/books/{bookId}/lessons/{lessonId}/pages/{pageNumber}",
                  bookId,
                  lessonId,
                  pageNumber)
              .retrieve()
              .body(LessonPageResponse.class);
      return Optional.ofNullable(page).map(this::normalizePage);
    } catch (HttpClientErrorException.NotFound nf) {
      return Optional.empty();
    } catch (ResourceAccessException ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    }
  }

  @Override
  public List<LessonPageHistoryEntryResponse> getPageHistory(
      UUID bookId, UUID lessonId, int pageNumber, int limit) {
    try {
      List<LessonPageHistoryEntryResponse> entries =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(BASE + "/books/{bookId}/lessons/{lessonId}/pages/{pageNumber}/history")
                          .queryParam("limit", Math.max(1, limit))
                          .build(bookId, lessonId, pageNumber))
              .retrieve()
              .body(new ParameterizedTypeReference<List<LessonPageHistoryEntryResponse>>() {});
      return entries == null ? Collections.emptyList() : entries;
    } catch (HttpClientErrorException.NotFound nf) {
      return Collections.emptyList();
    } catch (ResourceAccessException ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    }
  }

  @Override
  public LessonPageResponse updatePage(
      UUID bookId, UUID lessonId, int pageNumber, UpdateLessonPageRequest request, UUID actorId) {
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        LessonPageResponse updated =
            restClient
                .patch()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path(BASE + "/books/{bookId}/lessons/{lessonId}/pages/{pageNumber}")
                            .queryParamIfPresent("actor_id", Optional.ofNullable(actorId))
                            .build(bookId, lessonId, pageNumber))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(
                    HttpStatusCode::is4xxClientError,
                    (req, resp) -> {
                      if (resp.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new AppException(ErrorCode.LESSON_PAGE_NOT_FOUND);
                      }
                      throw new AppException(ErrorCode.INVALID_REQUEST);
                    })
                .body(LessonPageResponse.class);
        if (updated == null) {
          throw new AppException(ErrorCode.LESSON_PAGE_NOT_FOUND);
        }
        return normalizePage(updated);
      } catch (ResourceAccessException ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
          root = root.getCause();
        }
        String rootMsg =
            root.getClass().getSimpleName() + (root.getMessage() == null ? "" : (": " + root.getMessage()));
        if (attempt >= 2) {
          log.warn(
              "Crawler unreachable when updating page. bookId={}, lessonId={}, pageNumber={}, cause={}",
              bookId,
              lessonId,
              pageNumber,
              rootMsg);
          throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
        }
        log.warn(
            "Crawler unreachable on attempt {}/2 when updating page. Retrying... bookId={}, lessonId={}, pageNumber={}, cause={}",
            attempt,
            bookId,
            lessonId,
            pageNumber,
            rootMsg);
        try {
          Thread.sleep(250L);
        } catch (InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
          throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
        }
      }
    }
    throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
  }

  @Override
  public void deleteAllPagesForBook(UUID bookId) {
    try {
      restClient
          .delete()
          .uri(BASE + "/books/{bookId}/pages", bookId)
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.NotFound nf) {
      log.debug("No pages to delete for book {}", bookId);
    } catch (ResourceAccessException ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    }
  }

  @Override
  public boolean isBookFullyVerified(UUID bookId) {
    try {
      VerifyState state =
          restClient
              .get()
              .uri(BASE + "/books/{bookId}/verification", bookId)
              .retrieve()
              .body(VerifyState.class);
      return state != null && Boolean.TRUE.equals(state.fullyVerified());
    } catch (HttpClientErrorException.NotFound nf) {
      return false;
    } catch (ResourceAccessException ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE);
    }
  }

  /** Internal — must match Python /verification response shape. */
  private record VerifyState(Boolean fullyVerified, Integer totalPages, Integer verifiedPages) {}

  private List<LessonPageResponse> normalizePages(List<LessonPageResponse> pages) {
    Map<String, String> mediaUrlCache = new HashMap<>();
    pages.forEach(page -> normalizePage(page, mediaUrlCache));
    return pages;
  }

  private LessonPageResponse normalizePage(LessonPageResponse page) {
    return normalizePage(page, new HashMap<>());
  }

  private LessonPageResponse normalizePage(LessonPageResponse page, Map<String, String> mediaUrlCache) {
    if (page == null) {
      return null;
    }
    page.setRawImageUrl(resolveMediaUrl(page.getRawImageUrl(), mediaUrlCache));
    if (page.getContentBlocks() != null) {
      page.getContentBlocks().forEach(block -> normalizeBlockUrls(block, mediaUrlCache));
    }
    return page;
  }

  private void normalizeBlockUrls(ContentBlockDto block, Map<String, String> mediaUrlCache) {
    String imageUrl = resolveMediaUrl(block.getImageUrl(), mediaUrlCache);
    String imagePath = resolveMediaUrl(block.getImagePath(), mediaUrlCache);
    String thumbnailUrl = resolveMediaUrl(block.getThumbnailUrl(), mediaUrlCache);

    block.setImageUrl(firstNonBlank(imageUrl, imagePath));
    block.setImagePath(imagePath);
    block.setThumbnailUrl(thumbnailUrl);
  }

  private static String shortResponseBody(RestClientResponseException ex) {
    String body = ex.getResponseBodyAsString();
    if (body == null || body.isEmpty()) {
      return "";
    }
    int max = 500;
    return body.length() <= max ? body : body.substring(0, max) + "...";
  }

  private String resolveMediaUrl(String value, Map<String, String> mediaUrlCache) {
    String normalized = rewriteStaticPath(value);
    if (normalized == null || normalized.isBlank()) {
      return normalized;
    }
    if (normalized.startsWith(STATIC_PROXY_PREFIX)
        || normalized.startsWith("http://")
        || normalized.startsWith("https://")) {
      return normalized;
    }

    String cached = mediaUrlCache.get(normalized);
    if (cached != null) {
      return cached;
    }

    // Remaining non-static strings are treated as private object keys.
    try {
      String presigned = uploadService.getPresignedUrl(normalized, minioProperties.getOcrContentBucket());
      mediaUrlCache.put(normalized, presigned);
      return presigned;
    } catch (Exception ex) {
      log.warn("Failed to presign OCR media key '{}': {}", normalized, ex.getMessage());
      mediaUrlCache.put(normalized, normalized);
      return normalized;
    }
  }

  private String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    return second;
  }

  private String rewriteStaticPath(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }
    if (path.startsWith(STATIC_PROXY_PREFIX) || path.startsWith("http://") || path.startsWith("https://")) {
      return path;
    }
    if (path.startsWith(STATIC_PREFIX)) {
      return STATIC_PROXY_PREFIX + path.substring(STATIC_PREFIX.length());
    }
    return path;
  }
}
