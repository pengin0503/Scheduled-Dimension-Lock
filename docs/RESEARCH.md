# Phase 0 調査結果 — Scheduled Dimension Lock（2026-11-26）

調査方法: Modrinth API v2（実データ: DL数・ローダー・対応バージョン）、CurseForge ページ実読、GitHub API 検索。
カタログ（Minecraft_Low_Competition_Mod_Ideas #11）の主張「Paper には PortalLock / Dimension Control があるが Mod はほぼ空白」は **裏取りの結果、否定された**。詳細は §3 の結論を参照。

## 1. 直接競合（Mod、スケジュール機能あり）

| Mod | ストア | ローダー / バージョン | DL | スケジュール機能 | 弱点（= SDL の差別化余地） |
|---|---|---|---|---|---|
| **Gate Keep**（frikinjay） | CurseForge + Modrinth（slug: gate-keep） | Fabric / NeoForge / Quilt、1.21.1〜1.21.9（**26.x なし**） | CF 不明 / Modrinth 1,621 | 絶対日時で解禁（unlockDateTime + タイムゾーン）、解禁時に全体アナウンス | 解禁のみで**自動再ロック（開放ウィンドウ）なし**、カウントダウンなし、設定は config JSON のみ（ゲーム内スケジュールコマンドなし）、最終更新 2025-04-26 |
| **Lock End**（vwtfafa） | Modrinth（slug: lock-end） | Paper/Folia/Fabric/**NeoForge**/datapack、1.20〜**26.3** | 7,697 | ロック/解禁のスケジュール（プラグイン・Mod 版） | **エンド専用**（ネザーなし）。Paper 版のみ退避・履歴等の追加機能 |
| **PortalLock**（Antehor） | Modrinth（slug: portal-lock-paper） | **Paper のみ**、26.2 / 26.3 | 187 | 解禁＋閉鎖のスケジュール、開放ウィンドウ、カウントダウン（PlaceholderAPI）、閉鎖時の帰還、Discord Webhook | Mod ではない（Paper プラグイン）。26.x のみ、1.21.1 なし |

## 2. 直接競合（Mod、手動ロック / 相対タイマーのみ）

| Mod | ストア | ローダー / バージョン | DL | 備考 |
|---|---|---|---|---|
| **DimensionBlocker** | CurseForge | NeoForge **1.21.1 のみ**、サーバー専用 | 著者総計 2.3K（プロジェクト単体は不明） | ネザー/エンドのみ。相対タイマー解禁（30s〜2d 等）・永続化あり。`/status` は「予定機能」で未実装。絶対日時・自動閉鎖・カウントダウンなし。更新 2026-10-22 |
| **Dimensional Lock** | Modrinth（slug: dimensional-lock） | NeoForge **1.21.1 のみ**、サーバー専用 | 37 | 任意ディメンションをコマンドで手動ロック、内部プレイヤーをスポーンへ退避、SavedData 永続化。スケジュールなし。公開 2026-11-04 と新しい |
| **Dimension Locker** | Modrinth（slug: dimension-locker） | Forge/NeoForge/Fabric/Quilt、〜1.21.4 | 830 | 任意ディメンションの手動ロック＋許可アイテム（データコンポーネント）。スケジュールなし |
| **Dimension Blocker** | Modrinth（slug: dimension-blocker） | Fabric、1.20.1〜1.21.8 | 10,413 | 手動ブロック＋権限連携。スケジュールなし |
| **dimension lock (server-side)** | Modrinth（slug: dimension-lock_server-side） | Fabric、1.21.1〜1.21.7 | 406 | GUI 付き手動ロック。スケジュールなし |
| **Disable Dimensions / Reimagined** | CurseForge | Mod + データパック | — | 条件付き無効化（進捗・日数・ゲームモード）。日時スケジュールではない |
| **Dimension Authority / DimensionControl 系** | CurseForge / Modrinth | 各種 | Dimension Control (Modrinth, Paper系) 27,745 | 手動トグルのみ |

Paper プラグイン側は他にも Dimension Control（27,745 DL）、ToggleDimensions、CdrDimensionGuard、NatureGate（Spigot、週次スケジュール＋VIP）等があり、需要自体は実在する。

## 3. 結論（重要）

- **「Mod はほぼ空白」は誤り。** スケジュール解禁だけなら Gate Keep が NeoForge で既に存在する。手動ロック系は 1.21.1 だけでも 3 本以上ある。
- ただし市場全体は小さい（最大でも 1 万 DL 級、大半は 3 桁〜4 桁）。「低競合」のうち**低需要でもある**可能性は否めない。
- それでも空白として残っている組み合わせはある:
  1. **開放ウィンドウ**（解禁日時＋自動再ロック＋閉鎖時の退避）を Mod でやるものは無い（Paper の PortalLock のみ）
  2. **カウントダウン通知**（残り 1日/1時間/10分…の段階アナウンス）を Mod でやるものは無い
  3. **NeoForge 1.21.1 と 26.x の両対応**でスケジュール系は無い（Gate Keep は 26.x なし、Lock End はエンド専用）
  4. **非 OP プレイヤー向けの公開ステータスコマンド**（DimensionBlocker ですら未実装の「予定機能」）
  5. **ゲーム内コマンドでのスケジュール設定**（Gate Keep は config 編集のみ）
  6. **en/ja 標準対応**のスケジュール系 Mod は無い
- → 方針: カタログの想定スコープに「開放ウィンドウ＋カウントダウン＋公開ステータス」を核として差別化し、ストア説明文でも Gate Keep 等との違いを明記できる設計にする（DESIGN.md 参照）。単なる「日時解禁 Mod」として出すと Gate Keep の劣化コピーに見えるリスクがある。

## 4. 名前衝突チェック（2026-11-26 時点）

| 対象 | 結果 |
|---|---|
| Modrinth slug `scheduled-dimension-lock` | **空き**（API で 404 を確認） |
| Modrinth タイトル完全一致 "Scheduled Dimension Lock" | なし |
| GitHub リポジトリ検索 "Scheduled Dimension Lock" / "scheduled-dimension-lock" | **0 件** |
| CurseForge タイトル完全一致 | 検索で該当なし（slug の最終確定はプロジェクト作成時にのみ可能） |
| 類似名 | Dimensional Lock / Dimension Locker / Dimension Lock 等は存在するが、名称の混同リスクは許容範囲。「Scheduled」が識別子になる |

mod_id 候補: `scheduleddimensionlock`、コマンド: `/sdl`（Dimensional Lock の `/dimlock` と衝突回避のため `/dimlock` は使わない）。
