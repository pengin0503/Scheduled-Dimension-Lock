# Scheduled Dimension Lock — DESIGN v1.0

- mod_id: `scheduleddimensionlock`
- パッケージ: `com.pengin0503.scheduleddimensionlock`
- ローダー: NeoForge のみ（Fabric は当面なし）
- 対応バージョン: Minecraft 1.21.1 / 26.1.2 / 26.2 / 26.3（IML と同じマルチバージョン構成。NeoForge / Java の正確な版数は実装時に neoforged.net と Maven で再確認すること: 1.21.1 = NeoForge 21.1.x + Java 21、26.x = Java 25）
- 導入形態: **サーバーサイド専用**。バニラクライアントのまま参加可能（クライアントに Mod 不要）
- 言語: en_us / ja_jp の言語ファイルを最初から同梱
- ライセンス: リポジトリの LICENSE に従う（IML と同じ方針を pen が別途指定しない限り All Rights Reserved）

## 1. 目的と差別化（RESEARCH.md の結論に基づく）

ネザー / エンド / Mod 追加ディメンションの解禁・閉鎖を「日時スケジュール」で管理するサーバー運営 Mod。
既存の Gate Keep（日時解禁のみ）や DimensionBlocker（相対タイマーのみ）と差別化する核は次の 4 点で、v1.0 の必須要件とする。

1. **開放ウィンドウ**: 解禁日時と再ロック日時をペアで設定でき、イベント終了後に自動で閉まる
2. **カウントダウン通知**: 解禁 / 再ロックまでの残り時間が段階閾値を切るたびに全体通知する
3. **公開ステータス**: 非 OP プレイヤーでも `/sdl status` で各ディメンションの状態と次回解禁までの残り時間を見られる
4. **ゲーム内完結**: スケジュールの設定・変更・解除をすべて OP コマンドで行える（config 編集と再起動が不要）

## 2. 状態モデル

ディメンションごとに次の状態を持つ（対象は任意のディメンション ID。ただしオーバーワールド `minecraft:overworld` はロック不可として常に拒否する）。

- `locked` (boolean): 現在ロック中か
- `unlockAt` (Instant, nullable): 次回自動解禁の絶対時刻
- `lockAt` (Instant, nullable): 次回自動再ロックの絶対時刻
- `announcedThresholds` (Set<String>): 現在の遷移について通知済みのカウントダウン閾値（重複通知防止。再スケジュール時にリセット）

不変条件:
- `unlockAt` と `lockAt` の両方がある場合、`unlockAt < lockAt` でなければならない（ウィンドウ）。バリデーションで違反を拒否する
- 時刻を過ぎた `unlockAt` / `lockAt` は遷移実行後にクリアする
- 状態の正は **SavedData**（ワールドごと、`scheduleddimensionlock.dat`）。config は初期値と動作設定のみを持ち、正にはならない

初期状態: 初回ワールド読み込み時に config の `defaultSchedules` を SavedData へ取り込む。以後は SavedData が優先され、config を変えても既存ワールドの状態は変わらない（`/sdl reload` は動作設定のみ再読込）。

## 3. ロック中の侵入防止（サーバー側で完結）

- 主防御: NeoForge の `EntityTravelToDimensionEvent` をキャンセルする。対象はプレイヤーに限らず全エンティティ（アイテムやモブのポータル移動も止める）。これでポータル、エンドゲートウェイ、`/tp`、`/execute in`、他 Mod の標準テレポートを一括で止める
- OP バイパス: 権限レベル 2 以上のプレイヤーは config `opBypass`（既定 true）のとき通過できる。バイパスはプレイヤーのみに適用し、非プレイヤーエンティティには適用しない
- 副防御（取りこぼし対策）: サーバー tick で 20 tick ごとに「ロック中ディメンション内にいる非バイパスプレイヤー」を検査し、オーバーワールドのワールドスポーン（復活地点が有効ならそちらではなく常にワールドスポーンに統一し、無限ループを避ける）へ退避させる。ログイン直後のプレイヤーも同じ検査で処理される（専用のログインイベントでも即時検査する）
- ロック発生時の退避: 手動ロック / 自動再ロックの瞬間に、そのディメンション内にいる非バイパスプレイヤー全員を同様に退避させる（config `evacuateOnLock`、既定 true）
- ブロック時のフィードバック: 移動をキャンセルされたプレイヤーには言語キーでメッセージを送る。ロック中で `unlockAt` がある場合は残り時間も含める。同一プレイヤーへの連続表示は 5 秒のクールダウンで抑制する

注意: イベントを経由しない特殊な移動（Mod の独自実装など）は主防御で止められないことがある。副防御の tick 検査が最終防衛線になる旨を README に明記する。

## 4. スケジューラとカウントダウン

- 判定はサーバー tick から 20 tick（約 1 秒）ごとに実行する。実時間（`Instant.now()`）で判定し、ゲーム内時刻には依存しない。サーバー停止中に時刻を過ぎた場合は、起動後の最初の判定で遷移を実行する（catch-up）
- 遷移:
  - `locked && unlockAt != null && now >= unlockAt` → 解禁し全体アナウンス、`unlockAt` をクリア
  - `!locked && lockAt != null && now >= lockAt` → 再ロックし全体アナウンス＋退避、`lockAt` をクリア
- カウントダウン: config `countdownThresholds`（既定: 7d, 1d, 12h, 1h, 10m, 5m, 1m）。次回遷移までの残り時間が閾値を下回ったとき 1 回だけ全体通知する（通知済み管理は §2 の `announcedThresholds`）。対象は解禁・再ロックの両方
- アナウンスはチャット全体通知（broadcast）。タイトル表示やボスバーは v1.0 では入れない（将来候補）
- タイムゾーン: config `timezone`（既定はサーバーのシステムデフォルト。例 `Asia/Tokyo`）。日時入力と表示はすべてこのタイムゾーンで行う

## 5. コマンド

ルートコマンドは `/sdl`、エイリアス `/scheduleddimensionlock`。`/dimlock` は既存 Mod（Dimensional Lock）と衝突するため使わない。

公開コマンド（権限レベル 0、全プレイヤー）:
- `/sdl status` — 全対象ディメンションの状態（ロック中 / 解禁中、次回遷移と残り時間）を一覧表示
- `/sdl status <dimension>` — 1 件の詳細表示

OP コマンド（権限レベル 2）:
- `/sdl lock <dimension>` — 即時ロック（退避あり）
- `/sdl unlock <dimension>` — 即時解禁（`unlockAt` / `lockAt` はクリアしない。スケジュールは別途 clear する設計とし、実行結果メッセージでその旨を伝える）
- `/sdl schedule <dimension> unlock at <yyyy-MM-dd> <HH:mm>` — 絶対日時で解禁を予約
- `/sdl schedule <dimension> unlock in <duration>` — 相対指定（例 `7d`, `2d12h`, `30m`。書式は §6）
- `/sdl schedule <dimension> lock at <yyyy-MM-dd> <HH:mm>` — 絶対日時で再ロックを予約
- `/sdl schedule <dimension> lock in <duration>` — 相対指定で再ロックを予約
- `/sdl schedule <dimension> window <unlock日時> <lock日時>` — 開放ウィンドウを一括設定（両方とも `yyyy-MM-dd HH:mm` 形式。unlock < lock を検証）
- `/sdl schedule <dimension> clear` — そのディメンションの予約をすべて解除
- `/sdl evacuate <dimension>` — 内部の非バイパスプレイヤーを手動退避
- `/sdl reload` — 動作設定（config）を再読込

`<dimension>` はディメンション ID のサジェスト付き引数にする（ロード済みディメンションを補完）。

## 6. 入力書式

- 日時: `yyyy-MM-dd HH:mm`（config のタイムゾーンで解釈）。秒は扱わない
- 期間: `(\d+d)?(\d+h)?(\d+m)?(\d+s)?` の連結（例 `2d12h30m`、`7d`、`45m`）。最低 1 要素、合計が正であること。DimensionBlocker 等と同じ慣習的な書式に合わせる
- パースと状態遷移のロジックは Minecraft API に依存しない純粋な Java クラスに切り出し、単体テスト可能にする（§9）

## 7. Config（NeoForge サーバー config、ModConfigSpec）

ファイル: `scheduleddimensionlock-server.toml`（ワールドごとの server config の慣習に従う）

- `timezone` (String, 既定 `""` = システムデフォルト): 例 `Asia/Tokyo`
- `opBypass` (boolean, 既定 true)
- `evacuateOnLock` (boolean, 既定 true)
- `announceCountdown` (boolean, 既定 true)
- `countdownThresholds` (List<String>, 既定 `["7d","1d","12h","1h","10m","5m","1m"]`)
- `blockedMessageCooldownSeconds` (int, 既定 5)
- `defaultSchedules`: ディメンションごとの初期スケジュール。要素は文字列で `"minecraft:the_nether;unlock=2027-01-01 10:00;lock=2027-01-03 22:00"` のような単純な形式とし、unlock / lock はそれぞれ省略可。初回取り込み時のみ有効（§2）

## 8. 永続化

- SavedData に NBT で保存する: ディメンション ID ごとに `locked`, `unlockAt`（epoch 秒、無しは -1）, `lockAt`, `announcedThresholds`
- 変更のたびに `setDirty()` を呼ぶ。スケジューラ判定での変更も同様
- 破損耐性: 読み込み時に不正なエントリ（存在しない時刻形式など）があってもサーバーを落とさず、そのエントリを捨ててログに警告を出す（IML 1.4.0 のデータ安全方針と同水準）

## 9. テスト要件

- 単体テスト（JUnit 5、IML と同構成）: 期間パース、日時パースとタイムゾーン解釈、ウィンドウのバリデーション、状態遷移（解禁 / 再ロック / catch-up）、カウントダウン閾値の通知済み管理
- ビルド確認: 全対応バージョンで `compileJava` + `test` が通ること。実機確認は pen の検証環境に委ね、ヘッドレスで確認できる範囲（サーバー起動スモーク）は実装側で可能な限り行う
- 手動確認項目（リリース前に pen が確認）: ネザーポータル / エンドポータル / エンドゲートウェイでのブロック、`/tp` でのブロック、ロック中ログイン時の退避、再ロック時の退避

## 10. v1.0 の範囲外（将来候補として記録のみ）

- 週次などの繰り返しスケジュール（Paper の NatureGate / PortalLock にはある。需要を見て v1.1 候補）
- GUI 管理画面、ボスバー / タイトル表示のカウントダウン
- Discord Webhook 通知
- 許可アイテムやプレイヤー個別のバイパスリスト
- Fabric 版

## 11. ストア掲載時の説明方針

- 「Gate Keep との違い: 開放ウィンドウ（自動再ロック＋退避）とカウントダウン通知、ゲーム内コマンド完結、1.21.1 + 26.x 両対応」を説明文に明記する
- Modrinth のプロジェクトページに AI 生成画像を使わない（Modrinth 規則）。スクリーンショットは実機撮影のみ
