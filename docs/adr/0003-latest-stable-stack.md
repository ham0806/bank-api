# ADR 0003: 最新安定版の採用

## 決定

実装時点の最新安定版を採用し、SNAPSHOTとRelease Candidateは使用しません。直接指定するバージョンは `gradle/libs.versions.toml` に集約します。

2026年7月28日時点では、Java 25 LTS、Spring Boot 4.1.0、Gradle 9.6.1、jOOQ 3.21.6、Flyway 13.0.0、PostgreSQL 18.4、springdoc-openapi 3.0.1を採用しています。

## 理由

最新版を使いながらも、LTSのJavaとSpring BootのBOM管理を利用して、学習時の再現性と依存関係の互換性を確保するためです。

