package com.edushift.modules.students.provision;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
class SyntheticEmailFactoryTest {
 @Test void schoolDomainAndLegacyRecognition() {
  String email = SyntheticEmailFactory.generate("María José", "Pérez", "12345678", "semillitasinicialprueba", e -> false);
  assertThat(email).matches("mariajose.perez.12345678.[a-f0-9]{6}@slud-del-colegio\\.com");
  assertThat(SyntheticEmailFactory.isSyntheticAddress(email)).isTrue();
  assertThat(SyntheticEmailFactory.looksLikeRealEmail(email)).isFalse();
  assertThat(SyntheticEmailFactory.isSyntheticAddress("old@school.accounts.edushift.local")).isTrue();
  assertThat(SyntheticEmailFactory.looksLikeRealEmail("familia@school.com")).isTrue();
 }
 @Test void collisionsAndLongNames() {
  AtomicInteger tries = new AtomicInteger();
  String email = SyntheticEmailFactory.generate("a".repeat(200), "b".repeat(200), "9".repeat(50), "school", e -> tries.incrementAndGet() < 3);
  assertThat(tries.get()).isEqualTo(3);
  assertThat(email.split("@")[0].length()).isLessThanOrEqualTo(64);
  assertThat(SyntheticEmailFactory.isSyntheticAddress(email)).isTrue();
  assertThat(SyntheticEmailFactory.isSyntheticAddress(SyntheticEmailFactory.generate("Pérez", "López", null, "other", e -> true))).isTrue();
 }
}
