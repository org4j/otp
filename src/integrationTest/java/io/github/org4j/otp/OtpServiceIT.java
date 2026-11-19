package io.github.org4j.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.org4j.error.GeneralException;
import io.github.org4j.valkey.ValkeyAutoConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = OtpServiceIT.App.class,
    properties = {
      "org4j.valkey.service=otp-service",
      "org4j.otp.secret=test-secret",
      "org4j.otp.length=6",
      "org4j.otp.ttl=5m",
      "org4j.otp.max-attempts=3",
      "org4j.otp.max-sends=2",
      "org4j.otp.send-window=15m",
      "org4j.otp.resend-after=0s",
      "org4j.otp.key-prefix=otp"
    })
@Testcontainers
class OtpServiceIT {

  private static final String FLOW = "registration";
  private static final String EMAIL = "aziza@example.uz";

  private static final String[] LIMITS = {
    "org4j.valkey.service=otp-service",
    "org4j.otp.length=6",
    "org4j.otp.ttl=5m",
    "org4j.otp.max-attempts=5",
    "org4j.otp.max-sends=3",
    "org4j.otp.send-window=15m",
    "org4j.otp.resend-after=60s",
    "org4j.otp.key-prefix=otp"
  };

  @Container
  @ServiceConnection(name = "redis")
  static final GenericContainer<?> VALKEY =
      new GenericContainer<>(ResourceBundle.getBundle("images").getString("valkey"))
          .withExposedPorts(6379);

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class App {
    @Bean
    SimpleMeterRegistry meters() {
      return new SimpleMeterRegistry();
    }
  }

  @Autowired StringRedisTemplate redis;
  @Autowired OtpService otp;
  @Autowired SimpleMeterRegistry meters;

  @BeforeEach
  void emptyValkey() {
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @Test
  void aCodeWorksOnce() {
    var code = otp.issue(FLOW, EMAIL);
    otp.verify(FLOW, EMAIL, code);
    assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, code))
        .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_EXPIRED.getType()));
  }

  @Test
  void neitherTheCodeNorTheEmailIsStored() {
    var code = otp.issue(FLOW, EMAIL);
    var keys = redis.keys("otp:*");
    assertThat(keys).isNotEmpty().noneMatch(key -> key.contains(EMAIL));
    assertThat(keys.stream().map(key -> redis.opsForValue().get(key))).noneMatch(code::equals);
  }

  @Test
  void aCodeWorksOnceEvenUnderConcurrentRequests() throws Exception {
    var code = otp.issue(FLOW, EMAIL);
    var accepted = new AtomicInteger();
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      for (int i = 0; i < 20; i++) {
        pool.submit(
            () -> {
              start.await();
              try {
                otp.verify(FLOW, EMAIL, code);
                accepted.incrementAndGet();
              } catch (GeneralException ignored) {
                // expired: someone else used it first
              }
              return null;
            });
      }
      start.countDown();
    }
    assertThat(accepted).hasValue(1);
  }

  @Test
  void aServiceWithoutASecretRefusesToStart() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataRedisAutoConfiguration.class,
                ValkeyAutoConfiguration.class,
                OtpAutoConfiguration.class))
        .withPropertyValues(LIMITS)
        .withPropertyValues("org4j.otp.secret= ")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void wrongCodesLockAfterTheLimit() {
    var code = otp.issue(FLOW, EMAIL);
    var wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, wrong))
          .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_INVALID.getType()));
    }
    assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, code))
        .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED.getType()));
  }

  @Test
  void sendsAreCapped() {
    otp.issue(FLOW, EMAIL);
    otp.issue(FLOW, EMAIL);
    assertThatThrownBy(() -> otp.issue(FLOW, EMAIL))
        .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_SEND_LIMIT.getType()));
  }

  @Test
  void aNewCodeAfterALockoutStaysLocked() {
    var code = otp.issue(FLOW, EMAIL);
    var wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, wrong)).isInstanceOf(GeneralException.class);
    }
    assertThatThrownBy(() -> otp.issue(FLOW, EMAIL))
        .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED.getType()))
        .satisfies(e -> assertThat(((GeneralException) e).getRetryAfter()).isPositive());
    assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, code))
        .as("not even the right code: guesses per window stay at max-attempts")
        .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_ATTEMPTS_EXCEEDED.getType()));
  }

  @Test
  void aSecondCodeInsideTheCooldownWaits() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataRedisAutoConfiguration.class,
                ValkeyAutoConfiguration.class,
                OtpAutoConfiguration.class))
        .withPropertyValues(LIMITS)
        .withPropertyValues(
            "spring.data.redis.host=" + VALKEY.getHost(),
            "spring.data.redis.port=" + VALKEY.getMappedPort(6379),
            "org4j.otp.secret=test-secret",
            "org4j.otp.resend-after=1m")
        .run(
            context -> {
              var service = context.getBean(OtpService.class);
              service.issue(FLOW, "cooldown@example.uz");
              assertThatThrownBy(() -> service.issue(FLOW, "cooldown@example.uz"))
                  .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_RESEND_TOO_EARLY.getType()))
                  .satisfies(
                      e ->
                          assertThat(((GeneralException) e).getRetryAfter())
                              .isPositive()
                              .isLessThanOrEqualTo(Duration.ofMinutes(1)));
            });
  }

  @Test
  void withoutValkeyCodesFailClosed() throws Exception {
    int closed;
    try (var socket = new ServerSocket(0)) {
      closed = socket.getLocalPort();
    }
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataRedisAutoConfiguration.class,
                ValkeyAutoConfiguration.class,
                OtpAutoConfiguration.class))
        .withPropertyValues(LIMITS)
        .withPropertyValues(
            "spring.data.redis.port=" + closed,
            "spring.data.redis.timeout=1s",
            "org4j.otp.secret=test-secret")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(OtpService.class).issue(FLOW, EMAIL))
                    .satisfies(e -> assertCode(e, OtpExceptionStatus.OTP_UNAVAILABLE.getType())));
  }

  @Test
  void theCountersMove() {
    var code = otp.issue(FLOW, EMAIL);
    assertThatThrownBy(() -> otp.verify(FLOW, EMAIL, code.equals("000000") ? "111111" : "000000"))
        .isInstanceOf(GeneralException.class);
    otp.verify(FLOW, EMAIL, code);
    assertThat(meters.counter("org4j.otp.issued", "flow", FLOW).count()).isPositive();
    assertThat(meters.counter("org4j.otp.verified", "flow", FLOW).count()).isPositive();
    assertThat(meters.counter("org4j.otp.rejected", "flow", FLOW, "reason", "OTP_INVALID").count())
        .isPositive();
  }

  private static void assertCode(Throwable e, String code) {
    assertThat(((GeneralException) e).getStatus().getType()).isEqualTo(code);
  }
}
