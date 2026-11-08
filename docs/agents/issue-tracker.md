# 工作项跟踪：GitHub

本仓库的工作项和规格说明记录在 `tospery/HiAndroid` 的 GitHub Issues 中。使用 `gh` CLI 操作；在本仓库目录运行时，由 Git remote 确定目标仓库。

## 常用操作

- 创建：`gh issue create --title "..." --body "..."`。多行正文优先通过文件传入。
- 查看：`gh issue view <编号> --json number,title,body,labels,comments`。
- 列出：`gh issue list --state open --json number,title,body,labels,comments`，按需加 `--label` 或调整 `--state`。
- 评论：`gh issue comment <编号> --body "..."`。
- 加、移标签：`gh issue edit <编号> --add-label "..."`、`gh issue edit <编号> --remove-label "..."`。
- 关闭：先评论说明原因，再运行 `gh issue close <编号>`。对于已关闭的工作项，仍需单独发表评论。

## 父子关系与阻塞

- 建立子工作项：支持该参数的 `gh` 版本可用 `gh issue create --parent <父编号>` 或 `gh issue edit <父编号> --add-sub-issue <子编号>`。否则使用 GitHub API；若仓库未启用子工作项，在子项正文开头写 `Part of #<父编号>`。
- 建立阻塞关系：支持该参数的 `gh` 版本可用 `gh issue edit <子编号> --add-blocked-by <阻塞项编号>`。否则使用 GitHub API 的原生依赖接口；若不可用，在子项正文开头写 `Blocked by: #<阻塞项编号>`。
- 使用 API 建立关系时，`issue_id` 或 `sub_issue_id` 是数据库 ID，可用 `gh api repos/tospery/HiAndroid/issues/<编号> --jq .id` 获取，不能直接使用 `#编号`。
- 判断是否可以开工：确认所有阻塞项已关闭；可查询 `gh api repos/tospery/HiAndroid/issues/<编号> --jq .issue_dependencies_summary.blocked_by`。若使用正文回退约定，则逐项核对阻塞项状态。

首次在会话中创建工作项之前，先运行 `gh repo view --json nameWithOwner` 核对目标仓库。只在用户自己的仓库创建工作项。

## Pull Request 分流入口

**PRs as a request surface: no.**

默认不把外部 Pull Request 当作待分流需求。若仓库以后采用这种流程，将此值改为 `yes`；届时使用相应的 `gh pr` 命令读取、评论、加标签和关闭。GitHub Issues 与 Pull Requests 共用编号，遇到 `#编号` 时应确认其类型。

## skill 用语对应

- “发布到工作项跟踪器”：创建 GitHub Issue。
- “获取相关工作项”：读取对应 GitHub Issue。

## Wayfinder 约定

- 地图是一条带 `wayfinder:map` 标签的 Issue，正文记录 Notes、Decisions-so-far 和 Fog。
- 子工作项使用 GitHub 子 Issue 关联地图；不支持时，在地图正文维护任务列表，并在子项正文开头写 `Part of #<地图编号>`。类型标签采用 `wayfinder:research`、`wayfinder:prototype`、`wayfinder:grilling` 或 `wayfinder:task`。
- 从地图顺序中选择没有未关闭阻塞项且没有负责人的首个开放子项；认领时运行 `gh issue edit <编号> --add-assignee @me`。
- 完成时先评论结论，再关闭子项，并在地图的 Decisions-so-far 中添加上下文链接。
