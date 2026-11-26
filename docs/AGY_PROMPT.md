# agy 用実装プロンプト（pen がそのまま agy に貼る用）

使い方: 下の ``` ブロック内を全文コピーして agy に貼る。リポジトリは pengin0503/Scheduled-Dimension-Lock（作成済みの場合はそのクローン先で作業するよう冒頭のパスを調整する）。

---

```text
あなたは Minecraft Java Mod の実装担当です。以下の仕様に従い、新規 Mod「Scheduled Dimension Lock」を実装してください。仕様の全文はリポジトリ内の docs/DESIGN.md にあり、このプロンプトと矛盾する場合は DESIGN.md を正としてください。設計判断が必要な点が出たら、勝手に仕様を広げず、実装後に「要確認事項」として報告してください。

## プロジェクト概要
- Mod 名: Scheduled Dimension Lock
- mod_id: scheduleddimensionlock
- パッケージ: com.pengin0503.scheduleddimensionlock
- ローダー: NeoForge のみ
- 対応バージョン: Minecraft 1.21.1 / 26.1.2 / 26.2 / 26.3
  - マルチバージョン構成にすること。26.x は単一 jar でカバーできる構成を優先し、1.21.1 は API 差異に応じて分けること
  - NeoForge と Java の正確な版数は、実装時点で neoforged.net と NeoForged Maven を確認して決めること（1.21.1 は Java 21、26.x は Java 25 が前提。最大値を別ブランチから流用するような推測はしないこと）
- 導入形態: サーバーサイド専用。バニラクライアント（Mod なし）のまま参加できること。クライアント側に必須処理を置かないこと
- ビルド: Gradle + ModDevGradle。GitHub Actions で全バージョンの build + test が回る CI を用意すること

## 機能仕様（v1.0、DESIGN.md §1〜§8 が正）
1. ディメンションごとのロック状態管理: locked / unlockAt（次回自動解禁の絶対時刻）/ lockAt（次回自動再ロックの絶対時刻）/ カウントダウン通知済み閾値の集合。オーバーワールドはロック不可
2. 侵入防止: NeoForge の EntityTravelToDimensionEvent をキャンセルして、ロック中ディメンションへの移動を全エンティティについて止めること。権限レベル 2 以上のプレイヤーは config opBypass（既定 true）のとき通過可（バイパスはプレイヤーのみ）
3. 副防御: 20 tick ごとの検査で、ロック中ディメンション内にいる非バイパスプレイヤーをオーバーワールドのワールドスポーンへ退避させること。ログイン直後も即時検査すること。ロック発生時（手動・自動とも）は内部プレイヤーを退避させること（config evacuateOnLock、既定 true）
4. スケジューラ: 20 tick ごとに実時間（Instant）で判定し、unlockAt 到達で解禁＋全体アナウンス、lockAt 到達で再ロック＋全体アナウンス＋退避を行うこと。サーバー停止中に過ぎた予約は起動後の初回判定で実行すること（catch-up）。遷移後は対応する予約をクリアすること
5. カウントダウン: config countdownThresholds（既定 7d, 1d, 12h, 1h, 10m, 5m, 1m）について、次回遷移までの残り時間が閾値を下回ったら全体通知を 1 回だけ送ること（解禁・再ロック両方。再スケジュールで通知済みをリセット）
6. コマンド（ルート /sdl、エイリアス /scheduleddimensionlock。/dimlock は既存 Mod と衝突するので使わないこと）:
   - 全員: /sdl status [dimension]
   - OP (権限レベル 2): /sdl lock <dimension>、/sdl unlock <dimension>、/sdl schedule <dimension> unlock at <yyyy-MM-dd> <HH:mm>、/sdl schedule <dimension> unlock in <duration>、/sdl schedule <dimension> lock at ...、/sdl schedule <dimension> lock in ...、/sdl schedule <dimension> window <解禁日時> <再ロック日時>、/sdl schedule <dimension> clear、/sdl evacuate <dimension>、/sdl reload
   - <dimension> にはロード済みディメンションのサジェストを付けること
   - unlockAt と lockAt の両方がある場合は unlockAt < lockAt を検証し、違反はエラーにすること
7. 入力書式: 日時は yyyy-MM-dd HH:mm（config のタイムゾーンで解釈）。期間は 2d12h30m のような連結書式（d/h/m/s、最低 1 要素、合計が正）
8. 永続化: 状態はワールドごとの SavedData（NBT）に保存し、変更時に setDirty() すること。不正なエントリがあってもサーバーを落とさず、捨てて警告ログを出すこと
9. Config: NeoForge のサーバー config（ModConfigSpec）で timezone（既定はシステムデフォルト）、opBypass、evacuateOnLock、announceCountdown、countdownThresholds、blockedMessageCooldownSeconds（既定 5 秒。ブロック時のメッセージ連打抑制）、defaultSchedules（初回ワールド読み込み時にだけ SavedData へ取り込む初期スケジュール）を持つこと
10. 言語ファイル: en_us と ja_jp を用意し、プレイヤー向けメッセージはすべて言語キー経由にすること。ブロック時メッセージには、unlockAt がある場合に残り時間を含めること

## 実装上の制約
- 時刻・期間のパース、ウィンドウ検証、状態遷移、カウントダウン判定のロジックは、Minecraft API に依存しない純粋な Java クラスに分離し、JUnit 5 の単体テストを書くこと
- 対応バージョン間で共有できるコードは共有し、バージョン差異は最小限の分岐に閉じ込めること
- 既存の他 Mod のコードをコピーしないこと。API の使い方は NeoForge 公式ドキュメントと MDK テンプレートの範囲で書くこと
- GUI、繰り返しスケジュール（週次など）、Discord 連携、許可アイテムは v1.0 の範囲外なので実装しないこと

## 成果物と報告
- リポジトリにコード一式、README（英語。機能、コマンド、config、Gate Keep など既存 Mod との違いを含む）、docs/DESIGN.md の仕様どおりであることの自己チェック結果を含めること
- 完了報告には次を含めること: (1) 全バージョンのビルド結果 (2) 単体テストの実行結果（件数と成否） (3) 使用した NeoForge / Java の正確な版数とその確認元 (4) DESIGN.md から逸脱した点があればその一覧 (5) 実機（ゲーム内）で未確認の項目の一覧
- バージョンごとにビルドが失敗する場合は、失敗したバージョンとエラーの要点を報告し、成功したバージョンだけでも成果物として残すこと
```

---

## pen 向けメモ（プロンプト外）
- agy の作業ディレクトリはリポジトリのクローン先を指定する
- 完了報告が返ってきたら、このチャットに貼ってもらえれば Muse が diff とテスト結果を検証する（Muse が agy を直接起動することはしない）
