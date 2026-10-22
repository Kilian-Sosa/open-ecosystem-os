package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.openecosystem.os.worker.drive.DriveLifecycleFence;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcDriveOcrMetadataRepositoryTest {
  @Test
  void inactiveSourceNeverLoadsEncryptedMetadataOrStoragePointer() {
    var jdbc = mock(JdbcTemplate.class);
    var lifecycle = mock(DriveLifecycleFence.class);
    var repository = new JdbcDriveOcrMetadataRepository(jdbc, lifecycle);
    assertThat(repository.findByFileIdAndWorkspaceId("file_test", "wrk_test")).isEmpty();
    verify(lifecycle).isActiveFile("wrk_test", "file_test");
    verifyNoInteractions(jdbc);
  }
}
