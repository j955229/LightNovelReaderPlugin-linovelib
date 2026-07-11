# LightNovelReaderPlugin-linovelib

為 [LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) 提供 Linovelib 網站資料的第三方插件。

## 功能

- 探索首頁、排行榜及完本列表
- 依小說名稱、小說 ID 或網址搜尋
- 讀取書籍資訊、目錄、章節文字及插圖
- 將網站簡體內容轉成繁體中文
- 還原網站的章節順序並合併分頁內容
- 章節段落加入首行縮排與段落間距

## 安裝

1. 從 GitHub Actions 的建置產物取得 `plugin-debug.apk.lnrp`，或在電腦自行建置。
2. 開啟 LightNovelReader 的「設定」→「擴充外掛」。
3. 選擇「安裝外掛」，匯入 `.lnrp` 檔案。
4. 安裝後啟用 `Linovelib TW`。

目前插件 API 版本為 `2`，測試使用 LightNovelReader `1.2.0`。

## 自行建置

Windows：

```powershell
.\gradlew.bat :plugin:testDebugUnitTest :plugin:assembleDebug
```

Linux 或 macOS：

```bash
./gradlew :plugin:testDebugUnitTest :plugin:assembleDebug
```

輸出檔案位於：

```text
plugin/build/outputs/apk/debug/plugin-debug.apk.lnrp
```

## 專案來源

本專案的獨立 Gradle 建置結構參考官方示例：

- [dmzz-yyhyy/LightNovelReaderPlugin-Template](https://github.com/dmzz-yyhyy/LightNovelReaderPlugin-Template)
- 使用參考分支：[`potato_lib`](https://github.com/dmzz-yyhyy/LightNovelReaderPlugin-Template/tree/potato_lib)

插件功能與 Linovelib 解析程式依照上述模板提供的 LightNovelReader API 2 介面實作。此倉庫沒有保留 GitHub Fork 關係，方便獨立維護及發布版本。

模板目前使用的公開 API 快照已變更部分介面。為了相容 LightNovelReader `1.2.0`，倉庫內保留該版本的 `api` 編譯介面，並以 `compileOnly` 連結；產生的插件不會重複封裝 APP API。

## 已知限制

- 網站的搜尋驗證或頁面結構變更後，搜尋及章節讀取可能暫時失效。
- 搜尋結果會額外讀取書籍詳情頁，以取得正確日期、字數及完結狀態。
- 插件只提供資料來源；字體、字級、行距、頁面模式及主題由 LightNovelReader 控制。

## 聲明

此插件由第三方維護，與 Linovelib 網站沒有隸屬關係。請遵守網站條款及所在地法規。

程式碼依照 [Apache License 2.0](LICENSE) 授權發布。
