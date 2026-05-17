package com.fptu.math_master.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** HTTP client configuration for the Python crawler service (FastAPI). */
@Data
@Configuration
@ConfigurationProperties(prefix = "crawl-data")
public class CrawlDataProperties {

  /** Base URL of the Python service, e.g. http://localhost:8001 */
  private String baseUrl = "http://localhost:8001";

  /** Sent as X-Internal-API-Key — only Java BE should know this. */
  private String internalApiKey = "change-me-in-production";

  /** Connect/read timeout in seconds. OCR triggers return fast (async), but listing pages may be slower. */
  private int timeoutSeconds = 30;

  /** Timeout for assessment PDF Mathpix extract (multi-page). */
  private int assessmentExtractTimeoutSeconds = 300;
}
