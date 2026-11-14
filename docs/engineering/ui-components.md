# 通用 Compose 组件契约

`:suite` 只承载不含消费项目业务语义的通用视图、视觉与交互契约。业务数据、文案、事件和项目主题由消费项目提供；仅在真实跨项目复用且各语言语义与可访问性用途一致时，才将通用文案提升到 HiAndroid 资源。

- `SuiteActionMenu` 是带箭头的公共操作菜单。`SuiteActionMenuDefaults` 使用统一近黑背景和白色前景；菜单具有 16dp 圆角、至少 48dp 触摸目标，贴近触发器并在窗口内调整位置。调用方提供菜单项、文案、选中状态和回调。
- `SuiteAnchoredTooltip` 是指向锚点的提示气泡：`inverseSurface` 底色、14dp 圆角、16×8dp 箭头，上方空间不足时改到下方，左右与容器至少保留 8dp。默认显示 5 秒后用 300ms 渐隐并回调 `onDismissRequest`；锚点变化时重新计时，`autoDismissMillis = null` 时不自动消失。点击气泡以外区域立即关闭，其他关闭时机由调用方决定。iOS 端 HiIOS 的 `suiteAnchoredTooltip` 保持同一行为。
- `SuiteSnackbarHost` 承载短时反馈。实际排版为单行的纯文案背景随内容收缩并居中；多行或含操作时在 16dp 横向安全边距内占满宽度、文案左对齐，最多显示五行并截断。横向安全边距由组件负责，调用方不重复添加。
- `SuitePullRefreshBox`、`suiteLoadMoreFooter`、`SuiteListStateLayout` 与 `SuiteListRefreshViewportEffect` 负责与业务无关的下拉刷新、页脚、首次加载/空态/错误态和按稳定 ID 保持阅读位置的效果。业务或 Paging 状态到这些组件的映射留给消费项目；不得把项目特有的缓存、登录或订阅策略下沉到 `:suite`。
- 普通页面容器、独立内容行和临时浮层的默认颜色分别遵循 Material 3 的 `surfaceContainer`、`surfaceContainerLow` 与 `surfaceContainerHigh` 语义；嵌套排版容器保持透明。菜单的高对比背景和媒体/代码画布按自身语义显式例外。
- `:suite` 自有的重试和返回可访问性资源由 `com.tospery.suite.R` 公开；消费项目不维护同义别名。修改资源时同步维护默认语言和中文，并验证浅色、深色及相关组件。
