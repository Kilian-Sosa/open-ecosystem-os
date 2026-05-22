package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class DriveFileRepositoryTest {

  @Autowired private DriveFileRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void persistsEncryptedMetadataAndListsNewestFilesByWorkspace() {
    Instant older = Instant.parse("2026-05-22T08:00:00Z");
    Instant newer = Instant.parse("2026-05-22T09:00:00Z");

    repository.save(file("file_old", "wrk_123", "encrypted-old", older));
    repository.save(file("file_other", "wrk_other", "encrypted-other", newer));
    repository.save(file("file_new", "wrk_123", "encrypted-new", newer));

    assertThat(repository.listByWorkspace("wrk_123"))
        .extracting(DriveFileMetadata::fileId)
        .containsExactly("file_new", "file_old");
    assertThat(repository.findByIdForWorkspace("file_new", "wrk_123"))
        .get()
        .satisfies(
            metadata -> {
              assertThat(metadata.encryptedName()).isEqualTo("encrypted-new");
              assertThat(metadata.encryptedName()).doesNotContain("invoice.pdf");
              assertThat(metadata.storageKey())
                  .isEqualTo("workspaces/wrk_123/drive/file_new/original");
            });
  }

  private DriveFileMetadata file(
      String fileId, String workspaceId, String encryptedName, Instant createdAt) {
    return new DriveFileMetadata(
        fileId,
        workspaceId,
        "usr_123",
        encryptedName,
        "application/pdf",
        8,
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        "AES-256-GCM",
        "test-key",
        "content-iv",
        "name-iv",
        createdAt,
        createdAt);
  }
}
