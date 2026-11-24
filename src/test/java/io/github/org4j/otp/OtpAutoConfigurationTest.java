package io.github.org4j.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.org4j.valkey.ValkeyKeys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OtpAutoConfigurationTest {

  private static final String[] LIMITS = {
    "org4j.otp.secret=test-secret",
    "org4j.otp.length=6",
    "org4j.otp.ttl=5m",
    "org4j.otp.max-attempts=5",
    "org4j.otp.max-sends=3",
    "org4j.otp.send-window=15m",
    "org4j.otp.resend-after=60s",
    "org4j.otp.key-prefix=otp"
  };

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
          .withBean(ValkeyKeys.class, () -> mock(ValkeyKeys.class));

  @Nested
  class Startup {

    @Test
    void everyLimitSetStarts() {
      runner
          .withPropertyValues(LIMITS)
          .run(context -> assertThat(context).hasSingleBean(OtpService.class));
    }

    @Test
    void withoutValkeyThereIsNoService() {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OtpAutoConfiguration.class))
          .withPropertyValues(LIMITS)
          .run(context -> assertThat(context).doesNotHaveBean(OtpService.class));
    }

    @Test
    void anAppsOwnServiceIsKept() {
      var own =
          new OtpService(
              mock(ValkeyKeys.class),
              new OtpProperties(
                  "own",
                  6,
                  Duration.ofMinutes(5),
                  5,
                  3,
                  Duration.ofMinutes(15),
                  Duration.ZERO,
                  "otp"),
              new SimpleMeterRegistry());
      runner
          .withPropertyValues(LIMITS)
          .withBean(OtpService.class, () -> own)
          .run(context -> assertThat(context.getBean(OtpService.class)).isSameAs(own));
    }
  }

  @Nested
  class Limits {

    @Test
    void aMissingLimitStopsStartupNamingIt() {
      runner.run(
          context ->
              assertThat(context)
                  .getFailure()
                  .rootCause()
                  .hasMessageContaining("org4j.otp")
                  .hasMessageContaining("secret")
                  .hasMessageContaining("length")
                  .hasMessageContaining("ttl")
                  .hasMessageContaining("maxAttempts")
                  .hasMessageContaining("maxSends")
                  .hasMessageContaining("sendWindow")
                  .hasMessageContaining("resendAfter")
                  .hasMessageContaining("keyPrefix"));
    }

    @ParameterizedTest
    @CsvSource({
      "secret, ' '",
      "length, 3",
      "length, 11",
      "ttl, 0s",
      "max-attempts, 0",
      "max-sends, 0",
      "send-window, 0s",
      "resend-after, -1s",
      "key-prefix, ' '"
    })
    void anUnworkableLimitStopsStartup(String property, String value) {
      runner
          .withPropertyValues(LIMITS)
          .withPropertyValues("org4j.otp." + property + "=" + value)
          .run(context -> assertThat(context).hasFailed());
    }

    // Valkey counts in milliseconds: a send window of 0 ms deletes the counters as they are
    // written, so no limit holds, and a lifetime of 0 ms makes every issue fail.
    @ParameterizedTest
    @CsvSource({"ttl", "send-window"})
    void aWindowShorterThanAMillisecondStopsStartup(String property) {
      runner
          .withPropertyValues(LIMITS)
          .withPropertyValues("org4j.otp." + property + "=500us")
          .run(
              context ->
                  assertThat(context)
                      .getFailure()
                      .rootCause()
                      .hasMessageContaining(property.equals("ttl") ? "ttl" : "sendWindow"));
    }

    @Test
    void noWaitBetweenCodesStarts() {
      runner
          .withPropertyValues(LIMITS)
          .withPropertyValues("org4j.otp.resend-after=0s")
          .run(context -> assertThat(context).hasSingleBean(OtpService.class));
    }
  }
}
