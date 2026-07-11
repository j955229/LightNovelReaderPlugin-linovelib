# LightNovelReaderPlugin-linovelib

為 [LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) 提供 Linovelib 網站資料的第三方插件。

## 功能

- 探索首頁、排行榜及完本列表
- 依小說名稱、小說 ID 或網址搜尋
- 讀取書籍資訊、目錄、章節文字及插圖
- 還原網站的章節順序並合併分頁內容
- 章節段落加入首行縮排與段落間距
- 支援 APP 原生整本快取及 EPUB 導出流程
- 自動限制請求速度，遇到 HTTP 429 時等待並重試
- 為需要 Referer 的插圖提供本機圖片代理

## 安裝

1. 從 [Releases](https://github.com/j955229/LightNovelReaderPlugin-linovelib/releases) 下載 `.lnrp`，也可使用 GitHub Actions 的建置產物或在電腦自行建置。
2. 開啟 LightNovelReader 的「設定」→「擴充外掛」。
3. 選擇「安裝外掛」，匯入 `.lnrp` 檔案。
4. 安裝後啟用 `Linovelib TW`。

目前插件 API 版本為 `2`，已核對 LightNovelReader `1.2.0` 與 `1.2.1` 開發分支的相容規則。插件保留 API 2，讓兩個 APP 版本都能載入。

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

插件功能與 Linovelib 解析程式依照上述模板提供的 LightNovelReader API 2 介面實作。此倉庫沒有保留 GitHub Fork 關係，方便獨立維護及發布版本。建置已加入新版模板使用的 KSP 自動註冊清單。

模板目前使用的公開 API 快照已變更部分介面。為了相容 LightNovelReader `1.2.0`，倉庫內保留該版本的 `api` 編譯介面，並以 `compileOnly` 連結；產生的插件不會重複封裝 APP API。

## 已知限制

- 網站的搜尋驗證或頁面結構變更後，搜尋及章節讀取可能暫時失效。
- 搜尋結果會額外讀取書籍詳情頁，以取得正確日期、字數及完結狀態。
- 以 Android 應用方式安裝插件時，含圖片 EPUB 會使用插件圖片代理；僅匯入 `.lnrp` 檔案時，圖片下載仍受 APP 原生下載器是否傳送 Referer 限制。
- 插件只提供資料來源；字體、字級、行距、頁面模式及主題由 LightNovelReader 控制。

## 聲明

此插件由第三方維護，與 Linovelib 網站沒有隸屬關係。請遵守網站條款及所在地法規。

程式碼依照 [Apache License 2.0](LICENSE) 授權發布。
