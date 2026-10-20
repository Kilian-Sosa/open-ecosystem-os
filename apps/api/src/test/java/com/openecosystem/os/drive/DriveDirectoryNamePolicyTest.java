package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openecosystem.os.common.errors.ApiException;
import org.junit.jupiter.api.Test;

class DriveDirectoryNamePolicyTest {
  @Test
  void comparesCaseAndCanonicalUnicodeWithoutPersistingNormalization() {
    var policy = new DriveDirectoryNamePolicy();
    assertThat(policy.normalize("CAFÉ")).isEqualTo(policy.normalize("cafe\u0301"));
  }

  @Test
  void rejectsInvalidDirectoryNames() {
    var policy = new DriveDirectoryNamePolicy();
    for (String name :
        new String[] {"", " ", ".", "..", "a/b", "a\\b", "a\u0000b", "x".repeat(256)}) {
      assertThatThrownBy(() -> policy.normalize(name)).isInstanceOf(ApiException.class);
    }
  }
}
