# otp4j

One-time codes for any module: issued and checked in Valkey by one Lua script each, so limits hold
and a code works once even under concurrent requests. Valkey keeps only HMAC-SHA256 hashes of the
code and of the subject (an email), keyed with a secret and kept apart (`code:` and `subject:`
before hashing). A subject's keys share a Valkey Cluster hash tag (`otp:<flow>:{<hash>}:<part>`), so
the scripts run on a cluster too.

```groovy
implementation libs.otp4j // needs Valkey (valkey4j.* and spring.data.redis.*)
```

```java
var code = otp.issue("registration", email); // the caller delivers it
otp.verify("registration", email, typedCode); // returns normally if right; never twice
```

Wrong codes count for the whole send window, across codes, so a lockout lasts until the window
ends and a locked subject gets no new code. A new code waits `resend-after` after the last one.
Without Valkey nothing is issued or accepted, and a warning logs Valkey's own message.

## Properties

None has a default: each is the service's policy, so the service sets every one, and a missing or
unworkable value stops startup, naming the property.

| Property | What |
|---|---|
| `otp4j.secret` | key of the stored hashes, from Vault |
| `otp4j.length` | digits in a code, 4 to 10 |
| `otp4j.ttl` | how long a code stays valid |
| `otp4j.max-attempts` | wrong codes allowed per send window |
| `otp4j.max-sends` | codes sent to one subject per send window |
| `otp4j.send-window` | the window sends and wrong codes count in |
| `otp4j.resend-after` | wait between two codes; `0s` for none |
| `otp4j.key-prefix` | first part of every Valkey key |

## Errors

| Code | Status | When |
|---|---|---|
| `OTP_EXPIRED` | 410 | the code expired, was used, or was never sent |
| `OTP_INVALID` | 400 | the code is wrong |
| `OTP_ATTEMPTS_EXCEEDED` | 429 | too many wrong codes in this window; `Retry-After` |
| `OTP_SEND_LIMIT` | 429 | too many codes sent in this window; `Retry-After` |
| `OTP_RESEND_TOO_EARLY` | 429 | a code was just sent; `Retry-After` |
| `OTP_UNAVAILABLE` | 503 | Valkey can't be reached |

## Metrics

`otp4j.issued{flow}`, `otp4j.verified{flow}`, `otp4j.rejected{flow,reason}` in the app's
meter registry.

## Beans you can replace

`OtpService`.

## Tests

- `./gradlew -p platform :otp:test`: codes, how every script answer becomes an error with
  `Retry-After`, Valkey down, the property checks.
- `./gradlew -p platform :otp:integrationTest`: a real Valkey (`@ServiceConnection`): single use under
  20 concurrent checks, nothing readable stored, lockout that holds across codes, cooldown, send
  limit, Valkey down, counters.
