package io.github.org4j.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.org4j.valkey.ValkeyKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OtpPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
          .withBean(ValkeyKeys.class, () -> mock(ValkeyKeys.class))
          .withPropertyValues(
              "org4j.otp.secret=test-secret",
              "org4j.otp.length=6",
              "org4j.otp.ttl=5m",
              "org4j.otp.max-attempts=5",
              "org4j.otp.max-sends=3",
              "org4j.otp.send-window=15m",
              "org4j.otp.resend-after=60s",
              "org4j.otp.key-prefix=otp");

  @Test
  void everyLimitSetStarts() {
    runner.run(context -> assertThat(context).hasSingleBean(OtpService.class));
  }

  @Test
  void aMissingLimitStopsStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
        .withBean(ValkeyKeys.class, () -> mock(ValkeyKeys.class))
        .withPropertyValues("org4j.otp.secret=test-secret")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("org4j.otp")
                    .hasMessageContaining("length")
                    .hasMessageContaining("ttl")
                    .hasMessageContaining("maxAttempts")
                    .hasMessageContaining("maxSends")
                    .hasMessageContaining("sendWindow")
                    .hasMessageContaining("resendAfter")
                    .hasMessageContaining("keyPrefix"));
  }

  @Test
  void aZeroLengthCodeStopsStartup() {
    runner
        .withPropertyValues("org4j.otp.length=0")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("org4j.otp")
                    .hasMessageContaining("length"));
  }

  @Test
  void aCodeThatNeverLivesStopsStartup() {
    runner
        .withPropertyValues("org4j.otp.ttl=0s")
        .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("ttl"));
  }
}
