package com.openecosystem.os.worker.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openecosystem.search")
public record SearchProperties(
    String meilisearchHost, String meilisearchMasterKey, String indexName, int maxAttempts) {

  public SearchProperties {
    meilisearchHost = defaultText(meilisearchHost, "http://localhost:7700");
    meilisearchMasterKey = defaultText(meilisearchMasterKey, "openecosystem_dev_master_key");
    indexName = defaultText(indexName, "openecosystem_documents");
    maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
  }

  private static String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
