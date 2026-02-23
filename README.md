# M3U8Downloader
## Directory Structure
M3U8Downloader/
│
├── m3u8-core/                 # 核心業務邏輯與數據處理模組
│   └── src/main/java/com/tech/newbie/m3u8downloader/core/
│       ├── common/            # 共用常數與列舉
│       ├── config/            # 應用配置
│       ├── model/             # 數據模型 (例如 DownloadTask)
│       └── service/           # 核心下載與網路請求服務
│
├── m3u8-gui/                  # 圖形使用者介面模組 (基於 JavaFX)
│   └── src/main/
│       ├── java/com/tech/newbie/m3u8downloader/gui/
│       │   ├── controller/    # UI 視圖控制器，處理用戶操作事件
│       │   ├── service/       # GUI 專用服務與輔助
│       │   ├── strategy/      # GUI 狀態或策略模式實作
│       │   ├── viewmodel/     # ViewModel 層，綁定 UI 和業務數據
│       │   └── M3U8Application.java # JavaFX 應用程式入口點
│       └── resources/
│           └── fxml/          # 存放 FXML 檔案，定義 UI 布局
│
└── pom.xml                    # 父層 Maven 項目管理文件

## TODO
[] 文件重複
