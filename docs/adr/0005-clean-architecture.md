# ADR 0005: Clean Architectureのパッケージ境界

## 状況

パッケージ名は `domain` / `application` / `adapter` に分かれていたが、次の理由で依存の向きが学習しづらかった。

- WebとOutboxワーカーが具象の`BankingService`に直接依存していた
- 出力ポートがユースケース実装と同じパッケージにあり、入力と出力の違いが見えなかった
- Outboxの取得・外部連携・確定/失敗の手順がスケジューラAdapter側にあった
- `.gitignore`の`out/`が`adapter/out`にも一致し、Persistence AdapterがGit管理から外れていた

## 決定

依存の向きを Adapter → Application → Domain に固定する。

- `domain`: 業務ルール。外側の技術に依存しない
- `application.port.in`: Webやスケジューラが呼ぶユースケース契約
- `application.port.out`: DBや外部連携の契約
- `application.service`: ユースケース実装。`@Transactional`は学習用の実用的な境界としてこの層に置く
- `adapter.in.web` / `adapter.in.scheduler`: 駆動側Adapter。入力ポートだけを呼ぶ
- `adapter.out.persistence` / `adapter.out.integration`: 被駆動側Adapter。出力ポートを実装する

口座操作と振込受付はHTTPのアクター向け入力ポート、振込確定/失敗はワーカー向け入力ポート、Outbox処理は別ユースケースにする。業務ルールの分割が目的ではないため、口座と振込の実装は`BankingService`にまとめる。

`.gitignore`はルート直下の`/out/`だけを無視し、`adapter/out`を追跡対象にする。

## 結果

Controllerとスケジューラはユースケース実装やjOOQを知らなくてよい。Outbox処理は出力ポートをモックして単体テストできる。依存ルールはArchUnitで固定する。
