# 项目主页与 GitHub Pages

主页地址：<https://chenzhiyong1994.github.io/hush-wake/>。

`site/` 是独立的纯静态主页，沿用应用品牌颜色与图标，不依赖 Node 包、第三方字体、统计服务或网络音频。HTML/CSS 绘制的手机界面和声景用于展示，页面不读取设备状态、不设置真实闹钟、不播放声音。关闭 JavaScript 后，介绍、下载链接和 FAQ 仍可使用。

## 本地预览与验证

在仓库根目录运行：

```powershell
python -m http.server 4173 --bind 127.0.0.1 --directory site
```

访问 <http://127.0.0.1:4173/>。修改后刷新浏览器，并检查桌面与窄屏布局、闹钟/助眠切换、四种声景选择、FAQ、键盘焦点及下载链接。

```powershell
python scripts/verify-site.py
node --check site/app.js
git diff --check
```

静态检查覆盖本地资源、锚点、项目子路径、SVG、发布文件范围，以及主页与中英文 README 的 APK 版本一致性。网络下载地址与最终线上页面还需在发布时核实。这里只改主页时，不需要重新构建 Android 应用；修改应用代码时仍遵循项目原有门禁。

## 部署与维护

GitHub 仓库 Settings → Pages 的 Source 使用 **GitHub Actions**。`.github/workflows/pages.yml` 在主页、校验脚本或工作流推送至 `main` 时执行验证，通过后仅上传 `site/` 并发布；相关 Pull Request 只验证，不部署。也可从 Actions → Project homepage 手动运行。

Actions 使用固定 commit，部署权限仅授予发布 job，目标环境为 `github-pages`。发布方式依据 [GitHub Pages 自定义工作流文档](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)。

发布新 APK 后，同步 `site/index.html`、`README.md`、`README.zh-CN.md` 的版本、下载链接与测试版说明，并运行检查。新版本的 APK 与校验文件必须已经出现在对应 GitHub Release 中。主页不自动选取“latest”，避免把预发布版本与稳定版混淆。修改兼容等级、隐私承诺或功能说明时，先核对当前 PRD 和实现证据。

仓库 About 的 Website 与两份 README 均指向上述主页。APK 仍由 GitHub Releases 分发，主页发布不改变 Android beta 的实体机验收门禁。
