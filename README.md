# otp

One-time codes for any module. Each issue and each check is one Lua script in Valkey, so the limits
hold and a code works once, even under concurrent requests.

```groovy
implementation libs.otp // needs Valkey (org4j.valkey.* and spring.data.redis.*)
```

```java
var code = otp.issue("registration", email); // the caller delivers it
otp.verify("registration", email, typedCode); // returns normally if right; never twice
```

The subject is compared exactly. Normalize it before both calls (lower-case an email, say), or each
spelling gets limits of its own.

## How a code behaves

| When | Then |
|---|---|
| a code is issued | it replaces the subject's last code in that flow and lives `ttl` |
| the right code is checked | it is accepted once and the wrong guesses are cleared |
| two requests check the same code at once | one is accepted, the other gets `OTP_EXPIRED` |
| a code is checked in another flow | `OTP_EXPIRED`: each flow has its own codes and limits |
| `max-attempts` wrong codes in a send window | the subject is locked until the window ends, across codes: no new code, not even the right one |
| `max-sends` codes in a send window | no more codes until the window ends |
| a code was sent less than `resend-after` ago | no new code yet |
| Valkey can't be reached | nothing is issued or accepted, `OTP_UNAVAILABLE` |

## What Valkey keeps

| Key | Value |
|---|---|
| `<key-prefix>:<flow>:{<subject hash>}:code` | hash of the current code, for `ttl` |
| `…:attempts` | wrong codes in this send window |
| `…:sends` | codes sent in this send window |
| `…:last` | set for `resend-after` after a code |

Both hashes are HMAC-SHA256 keyed with `org4j.otp.secret`, with `code:` and `subject:` before
hashing, so neither the code nor the subject can be read back. A subject's keys share one hash tag,
so the scripts run on a Valkey Cluster too.

## Properties

None has a default: each is the service's policy, so the service sets every one. A missing or
unworkable value stops startup, naming the property.

| Property | What |
|---|---|
| `org4j.otp.secret` | key of the stored hashes, from Vault |
| `org4j.otp.length` | digits in a code, 4 to 10 |
| `org4j.otp.ttl` | how long a code stays valid; at least `1ms` |
| `org4j.otp.max-attempts` | wrong codes allowed per send window |
| `org4j.otp.max-sends` | codes sent to one subject per send window |
| `org4j.otp.send-window` | the window sends and wrong codes count in; at least `1ms` |
| `org4j.otp.resend-after` | wait between two codes; `0s` for none |
| `org4j.otp.key-prefix` | first part of every Valkey key |

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

In the app's meter registry, or the global one without it.

| Counter | Tags |
|---|---|
| `org4j.otp.issued` | `flow` |
| `org4j.otp.verified` | `flow` |
| `org4j.otp.rejected` | `flow`, `reason` (the error code) |

## Beans you can replace

| Bean | Default |
|---|---|
| `OtpService` | built from the properties once Valkey is configured |

## Tests

| Command | Covers |
|---|---|
| `./gradlew test` | codes and keys, how every script answer becomes an error with `Retry-After`, Valkey down, the property checks |
| `./gradlew integrationTest` | a real Valkey: single use under 20 concurrent checks, flows apart, nothing readable stored, lockout across codes, send limit, cooldown, Valkey down, counters |
