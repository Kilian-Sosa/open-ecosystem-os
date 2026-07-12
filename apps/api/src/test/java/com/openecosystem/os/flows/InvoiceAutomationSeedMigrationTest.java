package com.openecosystem.os.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class InvoiceAutomationSeedMigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void advancesTheSeededInvoiceWorkflowToTheProductionSafeVersion() {
    String currentVersion =
        jdbcTemplate.queryForObject(
            "select current_version_id from workflows where workflow_id ="
                + " 'flow_invoice_automation'",
            String.class);
    String definition =
        jdbcTemplate.queryForObject(
            "select definition_json from workflow_versions where version_id = ?",
            String.class,
            currentVersion);
    String description =
        jdbcTemplate.queryForObject(
            "select description from workflows where workflow_id = 'flow_invoice_automation'",
            String.class);

    assertThat(currentVersion).isEqualTo("wfv_invoice_automation_v3");
    assertThat(definition)
        .contains("extract_invoice_fields", "request_search_indexing")
        .doesNotContain("fake", "test data", "demo");
    assertThat(description).doesNotContain("fake").doesNotContain("demo");
  }
}
