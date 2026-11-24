package io.github.org4j.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.org4j.error.GeneralException;
import io.github.org4j.valkey.ValkeyExceptionStatus;
import io.github.org4j.valkey.ValkeyKeys;
import io.github.org4j.valkey.ValkeyScript;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class OtpServiceTest {

  private final ValkeyKeys valkey = mock(ValkeyKeys.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final OtpService otp = new OtpService(valkey, properties("test-secret", 6), meters);

  @Nested
  class Issuing {

    @Test
    void aCodeHasTheConfiguredLengthAndOnlyDigits() {
      scriptAnswers(1, 0);
      assertThat(otp.issue("registration", "aziz@example.uz")).matches("\\d{6}");
      var eight = new OtpService(valkey, properties("test-secret", 8), meters);
      assertThat(eight.issue("registration", "aziz@example.uz")).matches("\\d{8}");
      assertThat(meters.counter("org4j.otp.issued", "flow", "registration").count()).isEqualTo(2);
    }

    @Test
    void aSubjectsKeysShareOneClusterSlotAndHideTheSubject() {
      scriptAnswers(1, 0);
      otp.issue("registration", "aziz@example.uz");
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
      verify(valkey)
          .execute(
              ArgumentMatchers.<ValkeyScript<List<Long>>>any(),
              keys.capture(),
              any(Object[].class));
      assertThat(keys.getValue())
          .hasSize(4)
          .allMatch(key -> key.matches("otp:registration:\\{[0-9a-f]{64}}:\\w+"))
          .noneMatch(key -> key.contains("aziz"));
      assertThat(keys.getValue().stream().map(key -> key.substring(0, key.indexOf('}'))).distinct())
          .hasSize(1);
    }

    @Test
    void eachRefusalIsItsCodeWithRetryAfter() {
      scriptAnswers(-1, 600_000);
      assertRefused(() -> otp.issue("f", "s"), OtpExceptionStatus.OTP_SEND_LIMIT, 600_000);
      scriptAnswers(-2, 41_500);
      assertRefused(() -> otp.issue("f", "s"), OtpExceptionStatus.OTP_RESEND_TOO_EARLY, 41_500);
      scriptAnswers(-3, 120_000);
      assertRefused(() -> otp.issue("f", "s"), OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED, 120_000);
    }

    @Test
    void aRefusalLiftingNowStillWaitsAMillisecond() {
      scriptAnswers(-1, -2);
      assertRefused(() -> otp.issue("f", "s"), OtpExceptionStatus.OTP_SEND_LIMIT, 1);
    }
  }

  @Nested
  class Checking {

    @Test
    void aRightCodeIsCounted() {
      scriptAnswers(1, 0);
      otp.verify("f", "s", "123456");
      assertThat(meters.counter("org4j.otp.verified", "flow", "f").count()).isEqualTo(1);
    }

    @Test
    void aWrongOrExpiredCodeHasNoRetryAfter() {
      scriptAnswers(-1, 0);
      assertRejected(() -> otp.verify("f", "s", "000000"), OtpExceptionStatus.OTP_INVALID);
      scriptAnswers(0, 0);
      assertRejected(() -> otp.verify("f", "s", "000000"), OtpExceptionStatus.OTP_EXPIRED);
      assertThat(meters.counter("org4j.otp.rejected", "flow", "f", "reason", "OTP_INVALID").count())
          .isEqualTo(1);
      assertThat(meters.counter("org4j.otp.rejected", "flow", "f", "reason", "OTP_EXPIRED").count())
          .isEqualTo(1);
    }

    @Test
    void aLockedSubjectWaitsForTheWindow() {
      scriptAnswers(-2, 30_000);
      assertRefused(
          () -> otp.verify("f", "s", "123456"), OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED, 30_000);
    }
  }

  @Nested
  class WithoutValkey {

    @Test
    void nothingIsIssuedOrAccepted() {
      scriptFails(new GeneralException(ValkeyExceptionStatus.VALKEY_UNAVAILABLE));
      assertUnavailable(() -> otp.issue("f", "s"));
      assertUnavailable(() -> otp.verify("f", "s", "123456"));
    }

    @Test
    void anyOtherFailureIsLoggedAndClosesToo(CapturedOutput output) {
      scriptFails(new IllegalStateException("no reply"));
      assertUnavailable(() -> otp.issue("f", "s"));
      assertThat(output).contains("One-time codes unavailable: no reply");
    }

    @Test
    void valkeysOtherErrorsPassThrough() {
      scriptFails(new GeneralException(ValkeyExceptionStatus.VALKEY_INVALID_ARGUMENT));
      assertThatThrownBy(() -> otp.issue("f", "s"))
          .isInstanceOfSatisfying(
              GeneralException.class,
              e ->
                  assertThat(e.getStatus())
                      .isEqualTo(ValkeyExceptionStatus.VALKEY_INVALID_ARGUMENT));
    }
  }

  @Test
  void aServiceWithoutASecretRefusesToStart() {
    assertThatThrownBy(() -> new OtpService(valkey, properties(" ", 6), meters))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("org4j.otp.secret");
  }

  private static OtpProperties properties(String secret, int length) {
    return new OtpProperties(
        secret,
        length,
        Duration.ofMinutes(5),
        5,
        3,
        Duration.ofMinutes(15),
        Duration.ofSeconds(60),
        "otp");
  }

  private void scriptAnswers(long status, long millis) {
    when(valkey.execute(
            ArgumentMatchers.<ValkeyScript<List<Long>>>any(), anyList(), any(Object[].class)))
        .thenReturn(List.of(status, millis));
  }

  private void scriptFails(RuntimeException failure) {
    when(valkey.execute(
            ArgumentMatchers.<ValkeyScript<List<Long>>>any(), anyList(), any(Object[].class)))
        .thenThrow(failure);
  }

  private static void assertRefused(Runnable call, OtpExceptionStatus status, long millis) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(status);
              assertThat(e.getRetryAfter()).isEqualTo(Duration.ofMillis(millis));
            });
  }

  private static void assertRejected(Runnable call, OtpExceptionStatus status) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(status);
              assertThat(e.getRetryAfter()).isNull();
            });
  }

  private static void assertUnavailable(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            GeneralException.class,
            e -> assertThat(e.getStatus()).isEqualTo(OtpExceptionStatus.OTP_UNAVAILABLE));
  }
}
