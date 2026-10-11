<h1 align="center">Kite for Android</h1>

<p align="center">在安卓手机和平板上，发现大家在写什么。</p>

<p align="center">
  <a href="README.md">English</a> · <strong>简体中文</strong>
</p>

Kite 是 [Explore](https://github.com/kite-plus/explore) 的原生安卓客户端：独立博客的最新文章汇成一条信息流。它和 [Kite for iOS](https://github.com/kite-plus/explore-ios) 是一对，用 Kotlin、Jetpack Compose 和 Material 3 编写，支持 Android 10 及以上。和网站一样，App 只展示标题、日期、简短摘要和缩略图，每篇文章都在作者自己的网站打开。

## 功能

- **发现**：和网站一样，三条文章流做成标签：所有收录博客的最新文章、推荐文章，以及你订阅的博客。左右滑动切换，下划线跟着走。文章按时间倒序连成一条列表，每篇都标着发布时间，可按博客语言和标签筛选，支持下拉刷新和无限翻页。Explore 发布的公告插在最新文章之间，Explore 自己的公告在 App 里原生显示。每篇文章旁显示 Explore 最近一次链接检测的结果，从未检测过的文章可以当场检测。
- **订阅**：订阅博客，在发现页的「订阅」标签里追更；订阅列表在「我的」里管理，可以导入其他阅读器导出的 OPML，也可以导出为 OPML 带到任何 RSS 阅读器。
- **博客**：按最近更新排列的博客目录，可以搜索全部博客；每个博客都有自己的页面，展示订阅源、文章、语言和博客系统。
- **搜索**：点发现页右上角的搜索按钮进入搜索页，可以浏览 Explore 的标签，并按名称、地址、简介即时搜索博客。
- **我的**：服务器提供 Kite Plus 账号时用它登录，也可以用 Explore 账号登录（没有 Kite Plus 的服务器上可以在 App 里创建账号），修改名称和密码，删除账号，用 DNS 记录认领自己的博客，提交博客并跟踪审核进度，还可以连接其他 Explore 服务器。Kite Plus 账号在它自己的页面删除，「我的」里有入口。
- **阅读**：和网站一样，点开文章先显示一个不到一秒的过渡页（可以关掉），写着博客名和文章标题，然后在 App 内的浏览器标签页（Custom Tabs）中打开，也可以改为直接用浏览器打开。标签页的菜单里可以分享文章卡片、订阅博客，或在 App 中查看博客。链接和网站一样只加上 `utm_source`，不添加任何其他内容。打开过的文章在列表里会变淡，这些记录只留在设备上。
- **分享**：文章和博客可以生成带 Kite 标志和二维码的卡片，存到相册或发给别人。扫码后，文章打开它在 Explore 上的分享页，博客打开它在 Explore 上的页面。
- **小组件**：在主屏幕上显示最新文章，三种尺寸，可以只看中文或英文博客。点一下文章，就在作者的网站打开。
- **语言**：英文和简体中文界面。文章标题和摘要保持作者原文的语言。

App 和 iOS 版逐屏对应，控件用安卓自己的：Material 3 组件、系统返回手势，iOS 用 Safari 视图的地方改用 Custom Tabs。iOS 上有两项在安卓上没有对应，所以没有做：截屏时藏在灵动岛下的标志，以及锁定屏幕小组件。

## 隐私

App 没有统计，也不请求任何第三方：文章缩略图和博客图标都来自 Explore 服务器。阅读不需要账号，也不发送任何凭据。登录后，会话令牌用 Android Keystore 里的密钥加密保存，以 Bearer 令牌的形式只发给它所属的服务器，也不会被备份。用 Kite Plus 账号登录是在浏览器标签页里打开服务器的页面完成的，App 看不到 Kite Plus 密码，也不持有 Kite Plus 的令牌：服务器只交给 App 一个一次性授权码，只有 App 自己的 PKCE verifier 能兑换。文章链接从不经过跳转。

## 构建

需要 JDK 21 和带 platform 37 的 Android SDK（Android Studio 两者都有）。构建并安装调试版，它以 `plus.kite.explore.dev` 安装，和正式版互不影响：

```bash
./gradlew installDebug
```

App 只依赖 AndroidX、Jetpack Compose 和 Kotlin 官方库，二维码编码器是自己写的。代码都在 `app` 模块的 `app/src/main/java/plus/kite/explore` 下：

| 包 | 内容 |
|---|---|
| `app` | App 模型、路由、各标签的页面栈、深度链接和根界面 |
| `data` | API 客户端、数据模型、错误和时间解析 |
| `core` | 格式化、链接标记、偏好设置和令牌存储、图片加载、Custom Tabs、OPML 和二维码 |
| `design` | 主题、头像、表单和共用组件，包括主按钮的黑白样式 |
| `discover`、`feed`、`blogs`、`search`、`account`、`submit`、`share` | 每个功能区一个包 |
| `widget` | 「最新文章」小组件和它的设置 |

小组件读取 explore.kite.plus；在 App 里选择的服务器只对 App 生效。

客户端遵循 Explore 的 [docs/design/api.md](https://github.com/kite-plus/explore/blob/main/docs/design/api.md) 中描述的接口。界面文字沿用 iOS 版的措辞，简体中文也直接沿用 iOS 版，和网站用词一致。

## 测试

单元测试在 JVM 上运行，覆盖数据解码、时间解析、链接标记、头像颜色、公告在信息流中的位置、深度链接、搜索、OPML、二维码编码器（用 ZXing 把生成的码读回来核对）和登录：PKCE、登录地址、回调、等待中的登录，以及和本机回环地址上的测试服务器之间收发的令牌和请求头：

```bash
./gradlew testDebugUnitTest
```

CI 在每次推送和拉取请求时运行它们，另外还有 Android lint（缺中文翻译的字符串也会报错）、注释检查（`python3 scripts/check-comments.py app/src`）和正式版构建。[RELEASE.md](RELEASE.md) 说明了这些检查和发版方式。

## 链接到 App

`explore://` 链接可以直接打开某个页面，路径和网站相同：`explore://blogs/{host}`、`explore://topics/{slug}`、`explore://submissions/{id}`、`explore://notices/{id}`、`explore://submit`、`explore://sign-in`、`explore://recommended`、`explore://following`、`explore://search` 和 `explore://about`。

Kite Plus 账号登录完成后，在 explore.kite.plus 上经 App Link 回到 `https://explore.kite.plus/app/auth/done`，在其他服务器上回到 `explore://app/auth/done`。两者都只在 App 正在等待自己发起的登录时才会处理；App Link 需要服务器做什么，见 [RELEASE.md](RELEASE.md#kite-plus-sign-in-and-app-links)。

## 许可证

Kite for Android 采用 [Apache License 2.0](LICENSE) 许可证。
