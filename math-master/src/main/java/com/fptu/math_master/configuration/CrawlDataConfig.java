package com.fptu.math_master.configuration;

import com.fptu.math_master.configuration.properties.CrawlDataProperties;
import java.time.Duration;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlDataConfig {

  CrawlDataProperties properties;

  @Bean(name = "crawlDataRestClient")
  public RestClient crawlDataRestClient() {
    int timeoutMs = (int) Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis();
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(timeoutMs))
            .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
            .build();
    CloseableHttpClient httpClient =
        HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .defaultHeader("X-Internal-API-Key", properties.getInternalApiKey())
        .requestFactory(factory)
        .build();
  }

  @Bean(name = "crawlDataLongRestClient")
  public RestClient crawlDataLongRestClient() {
    int timeoutMs =
        (int) Duration.ofSeconds(properties.getAssessmentExtractTimeoutSeconds()).toMillis();
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(timeoutMs))
            .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
            .build();
    CloseableHttpClient httpClient =
        HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .defaultHeader("X-Internal-API-Key", properties.getInternalApiKey())
        .requestFactory(factory)
        .build();
  }
}
