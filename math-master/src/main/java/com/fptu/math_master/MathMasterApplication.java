package com.fptu.math_master;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class MathMasterApplication {

  private static final Logger log = LoggerFactory.getLogger(MathMasterApplication.class);

  @Value("${app.backend-url:http://localhost:8080}")
  private String backendUrl;

  public static void main(String[] args) {
    SpringApplication.run(MathMasterApplication.class, args);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    String green  = "\u001B[32m";
    String bold   = "\u001B[1m";
    String reset  = "\u001B[0m";
    String line   = "=".repeat(52);
    log.info("\n{}{}\n{}\n{}  APPLICATION STARTED SUCCESSFULLY\n{}  URL : {}\n{}{}\n{}",
        bold, green,
        line,
        "",
        "",
        backendUrl,
        line,
        reset,
        "");
  }
}
