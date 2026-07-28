# bank-api

TDD、Clean Architecture、RESTful API、冪等性、残高整合性、同時実行制御、Transactional Outboxを学ぶための銀行APIです。

## 技術スタック

| 技術 | バージョン | 用途 |
| --- | --- | --- |
| Java | 25 LTS | 実行基盤・実装言語 |
| Spring Boot | 4.1.0 | Web、DI、トランザクション、テスト |
| Gradle | 9.6.1 | ビルド・テスト |
| PostgreSQL | 18.4 | 実DB、制約、行ロック |
| jOOQ | 3.21.6 | 型安全なSQL、コード生成 |
| Flyway | 13.0.0 | DB migration |
| springdoc-openapi | 3.0.1 | OpenAPI・Swagger UI |

バージョンは `gradle/libs.versions.toml` に集約しています。Spring Bootが管理する依存関係は、原則としてSpring BootのBOMに従います。

## 起動

Java 25はmiseで管理します。

```powershell
mise install
docker compose up -d --wait postgres
mise exec -- .\gradlew.bat bootRun
```

APIは `http://localhost:8080` で起動します。

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Outboxワーカーを有効にする場合は、次の環境変数を設定します。

```powershell
$env:BANK_OUTBOX_SCHEDULER_ENABLED='true'
mise exec -- .\gradlew.bat bootRun
```

## テスト

統合テストはDocker上のPostgreSQL 18.4を使用します。

```powershell
docker compose up -d --wait postgres
# 既存のPostgreSQLボリュームを使う場合も、bank_testが存在することを確認する
docker compose exec -T postgres psql -U bank -d postgres -c "SELECT 'CREATE DATABASE bank_test OWNER bank' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_test')\gexec"
mise exec -- .\gradlew.bat test
mise exec -- .\gradlew.bat check
```

テストは開発用の`bank`とは分離した`bank_test`へ接続します。新規ボリュームでは`docker/postgres-init/01-create-test-database.sql`が作成し、既存ボリュームでは上記の確認コマンドを一度実行してください。テストの前処理は`bank_test`内のデータだけを削除します。

テスト対象には次を含みます。

- ドメインの金額・残高・予約ルール
- 同一Idempotency-Keyの再送
- 同一キーへの異なるリクエスト
- 振込のOutbox処理
- 外部連携の恒久失敗と予約解除
- 同時出金による残高不足・ロストアップデート防止

## API例

口座を作成します。

```powershell
$account = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/accounts `
  -ContentType 'application/json' `
  -Body '{"accountNumber":"A-100"}'
```

入金します。金額は円単位の整数です。

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/accounts/$($account.id)/deposits" `
  -Headers @{'Idempotency-Key'='deposit-001'} `
  -ContentType 'application/json' `
  -Body '{"amountMinor":1000}'
```

振込を受け付けます。受付直後は `PENDING` で、Outboxワーカーが処理すると `COMPLETED` または `FAILED` になります。

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/transfers `
  -Headers @{'Idempotency-Key'='transfer-001'} `
  -ContentType 'application/json' `
  -Body '{"sourceAccountId":"<source-id>","destinationAccountId":"<destination-id>","amountMinor":500}'
```

## 設計上の要点

### 冪等性

入金・出金・振込は `Idempotency-Key` とリクエストハッシュを保存します。同じキー・同じ内容は既存リソースを返し、同じキー・異なる内容は `409 Conflict` とします。一意制約を最後の防衛線にしています。

### 残高と仕訳

`accounts.balance_minor` は照会用の現在残高です。根拠となる記録は `ledger_transactions` と `ledger_entries` であり、残高更新と仕訳登録は同一トランザクションで実行します。
入出金では顧客口座と`SYSTEM-CLEARING`口座へ相反する仕訳を作成し、清算口座の残高も同じトランザクションで更新します。

### 同時実行制御

入出金では対象口座を `FOR UPDATE` でロックします。振込では送金元と送金先をUUID順にロックし、ロック順序を統一します。

### Outbox

振込受付時に、振込レコード・金額予約・Outboxイベントを同じトランザクションで作成します。外部連携の成功後に仕訳を確定するため、恒久失敗時は予約解除で戻せます。

## 学習資料

- [要件](docs/requirements.md)
- [学習ノート](docs/learning-notes.md)
- [ADR一覧](docs/adr/)
