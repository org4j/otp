package io.github.org4j.otp;

import io.github.org4j.valkey.ValkeyAutoConfiguration;
import io.github.org4j.valkey.ValkeyKeys;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ValkeyAutoConfiguration.class)
@ConditionalOnBean(ValkeyKeys.class)
@EnableConfigurationProperties(OtpProperties.class)
public class OtpAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  OtpService otpService(
      ValkeyKeys valkey, OtpProperties properties, ObjectProvider<MeterRegistry> meters) {
    return new OtpService(valkey, properties, meters.getIfAvailable(() -> Metrics.globalRegistry));
  }
}
