# AppHarness

A minimal Android app skeleton (Kotlin + Jetpack Compose) paired with a GitHub Actions CI pipeline. The workflow builds a debug APK on every push to `main` (and on manual dispatch); pull the built APK from the run's **Actions artifacts** under the `debug-apk` artifact. It is debug-signed only and not intended for distribution.
