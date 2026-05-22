package com.openecosystem.os;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OpenEcosystemApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(OpenEcosystemApiApplication.class, args);
  }
}
