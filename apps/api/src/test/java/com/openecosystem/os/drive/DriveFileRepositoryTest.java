package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openecosystem.os.OpenEcosystemApiApplication;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
  void archiveFileReadsAreBoundedAndWorkspaceScoped() {
    Instant now = Instant.parse("2026-10-20T08:00:00Z");
    repository.save(file("file_a", "wrk_123", "encrypted-a", now));
    repository.save(file("file_b", "wrk_123", "encrypted-b", now.plusSeconds(1)));
    repository.save(file("file_c", "wrk_other", "encrypted-c", now));
    assertThat(repository.listByFolder("wrk_123", null, 1))
        .extracting(DriveFileMetadata::fileId)
        .containsExactly("file_b");
    assertThat(repository.listByFolder("wrk_other", null, 1))
        .extracting(DriveFileMetadata::fileId)
        .containsExactly("file_c");
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

  @Test
  void persistsExplicitVisibilityAndListsOnlyRequestedWorkspaceIdsNewestFirst() {
    Instant older = Instant.parse("2026-05-22T08:00:00Z");
    Instant newer = Instant.parse("2026-05-22T09:00:00Z");

    repository.save(
        file("file_private", "wrk_123", "encrypted-private", older, DriveFileVisibility.PRIVATE));
    repository.save(
        file(
            "file_workspace",
            "wrk_123",
            "encrypted-workspace",
            newer,
            DriveFileVisibility.WORKSPACE));
    repository.save(
        file("file_other", "wrk_other", "encrypted-other", newer, DriveFileVisibility.PRIVATE));

    assertThat(repository.findByIdForWorkspace("file_private", "wrk_123"))
        .get()
        .extracting(DriveFileMetadata::visibility)
        .isEqualTo(DriveFileVisibility.PRIVATE);
    assertThat(repository.findByIdForWorkspace("file_workspace", "wrk_123"))
        .get()
        .extracting(DriveFileMetadata::visibility)
        .isEqualTo(DriveFileVisibility.WORKSPACE);
    assertThat(
            repository.listByIdsForWorkspace(
                "wrk_123", java.util.List.of("file_workspace", "file_private", "file_other")))
        .extracting(DriveFileMetadata::fileId)
        .containsExactly("file_workspace", "file_private");
  }

  @Test
  void listsImmediateFilesAndMovesToRootWithoutChangingContentMetadata() {
    Instant createdAt = Instant.parse("2026-10-20T08:00:00Z");
    var folders = new DriveFolderRepository(jdbcTemplate);
    folders.save(
        new DriveFolderMetadata(
            "folder_files",
            "wrk_dev_placeholder",
            "usr_dev_placeholder",
            null,
            "synthetic-ciphertext",
            "synthetic-iv",
            createdAt,
            createdAt));
    var original = file("file_placed", "wrk_dev_placeholder", "encrypted-original", createdAt);
    repository.save(original);

    assertThat(repository.updateFolder("wrk_other", "file_placed", "folder_files", createdAt))
        .isFalse();
    assertThat(
            repository.updateFolder(
                "wrk_dev_placeholder", "file_placed", "folder_files", createdAt.plusSeconds(1)))
        .isTrue();
    assertThat(repository.listByFolder("wrk_dev_placeholder", null)).isEmpty();
    assertThat(repository.listByFolder("wrk_dev_placeholder", "folder_files"))
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.folderId()).isEqualTo("folder_files");
              assertThat(file.storageKey()).isEqualTo(original.storageKey());
              assertThat(file.encryptedName()).isEqualTo(original.encryptedName());
              assertThat(file.contentIv()).isEqualTo(original.contentIv());
              assertThat(file.checksumSha256()).isEqualTo(original.checksumSha256());
              assertThat(file.updatedAt()).isEqualTo(createdAt.plusSeconds(1));
            });
    repository.updateFolder("wrk_dev_placeholder", "file_placed", null, createdAt.plusSeconds(2));
    assertThat(repository.listByFolder("wrk_dev_placeholder", null))
        .extracting(DriveFileMetadata::fileId)
        .containsExactly("file_placed");
    assertThat(repository.listByFolder("wrk_dev_placeholder", "folder_files")).isEmpty();
    jdbcTemplate.update("delete from drive_files");
    jdbcTemplate.update("delete from drive_folders where folder_id = 'folder_files'");
  }

  @Test
  void crossWorkspaceFolderReferenceRejectedForFileSaveAndMove() {
    Instant now = Instant.parse("2026-10-20T08:00:00Z");
    new DriveFolderRepository(jdbcTemplate)
        .save(
            new DriveFolderMetadata(
                "folder_foreign_files",
                "wrk_dev_placeholder",
                "usr_dev_placeholder",
                null,
                "synthetic-ciphertext",
                "synthetic-iv",
                now,
                now));
    repository.save(file("file_foreign", "wrk_other", "encrypted-original", now));
    assertThatThrownBy(
            () -> repository.updateFolder("wrk_other", "file_foreign", "folder_foreign_files", now))
        .isInstanceOf(DataIntegrityViolationException.class);
    var placed =
        new DriveFileMetadata(
            "file_invalid",
            "wrk_other",
            "usr_123",
            DriveFileVisibility.PRIVATE,
            "folder_foreign_files",
            "encrypted-name",
            "text/plain",
            10,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "opaque-key",
            "AES-256-GCM",
            "test-key",
            "content-iv",
            "name-iv",
            now,
            now);
    assertThatThrownBy(() -> repository.save(placed))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(repository.findByIdForWorkspace("file_foreign", "wrk_other"))
        .get()
        .extracting(DriveFileMetadata::folderId)
        .isNull();
    jdbcTemplate.update("delete from drive_files");
    jdbcTemplate.update("delete from drive_folders where folder_id = 'folder_foreign_files'");
  }

  private DriveFileMetadata file(
      String fileId, String workspaceId, String encryptedName, Instant createdAt) {
    return file(fileId, workspaceId, encryptedName, createdAt, DriveFileVisibility.PRIVATE);
  }

  private DriveFileMetadata file(
      String fileId,
      String workspaceId,
      String encryptedName,
      Instant createdAt,
      DriveFileVisibility visibility) {
    return new DriveFileMetadata(
        fileId,
        workspaceId,
        "usr_123",
        visibility,
        null,
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
