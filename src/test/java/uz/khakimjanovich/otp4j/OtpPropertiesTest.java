package uz.khakimjanovich.otp4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uz.khakimjanovich.valkey4j.ValkeyKeys;

class OtpPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
          .withBean(ValkeyKeys.class, () -> mock(ValkeyKeys.class))
          .withPropertyValues(
              "otp4j.secret=test-secret",
              "otp4j.length=6",
              "otp4j.ttl=5m",
              "otp4j.max-attempts=5",
              "otp4j.max-sends=3",
              "otp4j.send-window=15m",
              "otp4j.resend-after=60s",
              "otp4j.key-prefix=otp");

  @Test
  void everyLimitSetStarts() {
    runner.run(context -> assertThat(context).hasSingleBean(OtpService.class));
  }

  @Test
  void aMissingLimitStopsStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
        .withBean(ValkeyKeys.class, () -> mock(ValkeyKeys.class))
        .withPropertyValues("otp4j.secret=test-secret")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("otp4j")
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
        .withPropertyValues("otp4j.length=0")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("otp4j")
                    .hasMessageContaining("length"));
  }

  @Test
  void aCodeThatNeverLivesStopsStartup() {
    runner
        .withPropertyValues("otp4j.ttl=0s")
        .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("ttl"));
  }
}
