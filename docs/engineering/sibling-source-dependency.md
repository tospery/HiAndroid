# 消费项目的同级源码依赖门禁

本规则适用于选择直接编译 HiAndroid 源码的任何消费项目。已发布的 Maven 制品是另一种接入方式，不适用本规则。

## 布局与版本身份

- 将 HiAndroid 作为独立 Git 仓库检出在消费仓库的同级目录，使用 Gradle composite build 接入；不以 Git submodule 内嵌源码。
- 消费仓库提交版本锁，记录 HiAndroid 的公开 HTTPS 仓库地址和 **40 位完整 commit SHA**。分支名、tag、Maven 版本号、当前本地 HEAD 或未提交文件都不能代替版本锁。
- 优先解析消费仓库同级的 `HiAndroid/`；linked worktree 可回落到主 worktree 同级目录。显式绝对路径仅改变检出位置，不放宽校验。路径是会执行 Gradle 逻辑的受信任输入。
- 消费模块用公开 Maven 坐标声明依赖，由消费仓库根 `settings.gradle.kts` 统一做 composite substitution；不直接引用 HiAndroid 的 Gradle project path。只替换实际消费的坐标。

## 必须接入的硬性门禁

消费项目必须在自己的构建或发布入口实现以下检查；只写在发布清单中、依赖开发者手动记得运行，不算完成门禁。

| 入口 | 必须阻断的状态 |
| --- | --- |
| Verification、CI | 缺少检出或无效版本锁；HEAD 不等于锁定 SHA；HiAndroid 工作区有已暂存、未暂存或未跟踪改动 |
| Release 制品的打包、签名、上传或发布 | 上述任一状态；锁定 SHA 不在可信远端跟踪分支上 |
| Debug 与 IDE 同步 | 可以使用偏离版本锁的本地源码，但必须明确警告；结果不得当作锁定版本的验收证据，也不得产出 Release 制品 |

检查应在实际任务执行前失败；不能只由 CI 约定或命令文档承担。远端可达性判断使用已获取的可信 remote-tracking refs；必要时先 fetch。绝对路径覆盖、worktree 回落或缓存不能绕过检查。仓库应提供可重复运行的 `status`、`bootstrap`、`lock` 等操作，供新机器和云端环境恢复锁定检出。

## 更新顺序与验证

1. 在 HiAndroid 提交并推送修改，确认 SHA 可从可信远端到达。
2. 消费仓库更新版本锁到该 SHA。
3. 分别运行 HiAndroid 自身门禁和消费项目的根门禁，核对 Debug 警告与 Verification/Release 失败分支。
4. 最后提交并推送消费仓库的版本锁。不得使远端消费提交指向尚未推送的 HiAndroid 提交。

AtlasHub_Android 的锁文件、脚本和 Gradle adapter 是一个消费项目实例；其他项目须按自己的构建入口落实相同不变量。
