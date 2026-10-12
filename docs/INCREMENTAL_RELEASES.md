# 機能ごとの追加・検証

## 0.4.5-alpha — 左下FPS表示

設定からFPS表示をオン／オフし、演奏画面の左下に小さく表示。設定を保存します。表示はWindow FrameMetricsで得た描画フレームの実測値で、画面の公称HzをFPSとして表示しません。

47 JVM tests・Androidビルド・Lint PASS。APK 27703265 bytes / SHA-256 `89cda023e36d773dc4baf5af461298251bc7720f00ab7ac169c1278819b59364`。FPS表示・端末上の実測値は実機未検証。120Hz更新は次の機能として実装します。 開発環境の実機／エミュレーター未接続。
