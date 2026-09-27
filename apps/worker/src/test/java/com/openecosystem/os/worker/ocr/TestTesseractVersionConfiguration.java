package com.openecosystem.os.worker.ocr;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestTesseractVersionConfiguration {

  @Bean
  @Primary
  TesseractVersionProbe tesseractVersionProbe() {
    return new TesseractVersionProbe("5.5.1");
  }
}
