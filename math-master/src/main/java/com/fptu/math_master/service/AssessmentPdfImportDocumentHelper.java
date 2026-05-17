package com.fptu.math_master.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.math_master.dto.response.AssessmentPdfImportDocumentResponse;
import com.fptu.math_master.dto.response.ContentBlockDto;
import com.fptu.math_master.dto.response.pdfimport.PdfImportPageDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** Serialize / seed structured PDF import document blocks on assessments. */
@Slf4j
public final class AssessmentPdfImportDocumentHelper {

  private AssessmentPdfImportDocumentHelper() {}

  public static AssessmentPdfImportDocumentResponse emptyDocument() {
    return AssessmentPdfImportDocumentResponse.builder()
        .questionBlocks(new ArrayList<>())
        .answerBlocks(new ArrayList<>())
        .build();
  }

  public static AssessmentPdfImportDocumentResponse parseDocumentJson(
      String json, ObjectMapper objectMapper) {
    if (json == null || json.isBlank()) {
      return emptyDocument();
    }
    try {
      JsonNode root = objectMapper.readTree(json);
      return AssessmentPdfImportDocumentResponse.builder()
          .questionBlocks(readBlocks(root.get("questionBlocks"), objectMapper))
          .answerBlocks(readBlocks(root.get("answerBlocks"), objectMapper))
          .build();
    } catch (Exception ex) {
      log.warn("Invalid pdf_import_document_json: {}", ex.getMessage());
      return emptyDocument();
    }
  }

  public static String toJson(
      AssessmentPdfImportDocumentResponse doc, ObjectMapper objectMapper) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("questionBlocks", doc.getQuestionBlocks() != null ? doc.getQuestionBlocks() : List.of());
      payload.put("answerBlocks", doc.getAnswerBlocks() != null ? doc.getAnswerBlocks() : List.of());
      return objectMapper.writeValueAsString(payload);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not serialize pdf import document", ex);
    }
  }

  public static AssessmentPdfImportDocumentResponse seedFromPages(
      List<PdfImportPageDto> pages, String pdfLayout) {
    if (pages == null || pages.isEmpty()) {
      return emptyDocument();
    }
    String merged = mergePageTexts(pages);
    if (merged.isBlank()) {
      return emptyDocument();
    }
    List<ContentBlockDto> single =
        List.of(
            ContentBlockDto.builder().order(1).type("text").content(merged).build());
    return AssessmentPdfImportDocumentResponse.builder()
        .questionBlocks(single)
        .answerBlocks(new ArrayList<>())
        .build();
  }

  private static String mergePageTexts(List<PdfImportPageDto> pages) {
    StringBuilder sb = new StringBuilder();
    for (PdfImportPageDto page : pages) {
      if (page == null) {
        continue;
      }
      String text = page.getText() != null ? page.getText().trim() : "";
      if (text.isEmpty()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append("\n\n");
      }
      sb.append(text);
    }
    return sb.toString();
  }

  private static List<ContentBlockDto> readBlocks(JsonNode node, ObjectMapper objectMapper) {
    if (node == null || !node.isArray()) {
      return new ArrayList<>();
    }
    List<ContentBlockDto> out = new ArrayList<>();
    for (JsonNode item : node) {
      try {
        out.add(objectMapper.treeToValue(item, ContentBlockDto.class));
      } catch (Exception ex) {
        log.debug("Skip invalid content block node: {}", ex.getMessage());
      }
    }
    return out;
  }
}
