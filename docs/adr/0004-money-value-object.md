# ADR 0004: 取引金額をMoney値オブジェクトで表現する

## 状況

`long`をアプリケーション層とドメイン層で直接受け渡すと、金額の正数制約やオーバーフロー対策が呼び出し側へ分散する。

## 決定

- 正の円単位の取引金額を`Money`値オブジェクトで表現する。
- `Money`の生成時に0以下を拒否する。
- 加算・減算は`Math.addExact`・`Math.subtractExact`を使い、結果が不正ならドメイン例外に変換する。
- `Account`の入金、出金、予約、予約解除、振込確定は`Money`を受け取る。
- HTTP DTOとPostgreSQLは既存互換性のため`long`を保持し、ControllerとPersistence Adapterで`Money`へ変換する。
- 確定残高は0を取り得て、`SYSTEM-CLEARING`口座は負残高を取り得るため、Accountの残高フィールドは今回`long`のままにする。

## 結果

ドメイン層では「正の取引金額」と「残高」を型で区別できる。API・DBの境界は既存のJSON/SQL契約を変更せず、値オブジェクトの不変条件をドメイン内へ集約できる。
