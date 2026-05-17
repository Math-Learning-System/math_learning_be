package com.fptu.math_master.dto.response.pdfimport;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfImportedTableDataDto {
  private String tableTitle;
  private List<String> headers;
  private List<List<Object>> rows;
  private String tableRawHtml;
  private String tableMarkdown;
}
