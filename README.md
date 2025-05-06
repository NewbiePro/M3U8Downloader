# M3U8Downloader
M3U8Downloader/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── tech/
│   │   │           └── newbie/
│   │   │               └── m3u8downloader/
│   │   │                   ├── controller/            # 控制器層，處理 UI 事件
│   │   │                   │   └── M3U8Controller.java
│   │   │                   ├── model/                 # 模型層，存放業務邏輯類
│   │   │                   │   └── DownloadTask.java
│   │   │                   ├── service/               # 服務層，負責數據處理，像是 HTTP 請求和檔案操作
│   │   │                   │   ├── M3U8Service.java
│   │   │                   │   └── FileService.java
│   │   │                   ├── view/                  # 視圖層，負責呈現 UI
│   │   │                   │   └── M3U8View.java
│   │   │                   ├── viewmodel/             # ViewModel 層，綁定 UI 和業務邏輯
│   │   │                   │   └── M3U8ViewModel.java
│   │   │                   └── utils/                 # 工具類，提供輔助功能
│   │   │                       └── FileUtils.java
│   │   └── resources/           # 靜態資源，如 fxml 文件、圖片等
│   │       └── view/            # 存放 FXML 檔案，定義 UI 布局
│   │           └── M3U8View.fxml
│   └── test/
│       └── java/
│           └── com/
│               └── tech/
│                   └── newbie/
│                       └── m3u8downloader/
│                           ├── controller/            # 控制器層測試
│                           ├── service/               # 服務層測試
│                           └── viewmodel/             # ViewModel 層測試
└── pom.xml (或 build.gradle)    # 項目管理文件