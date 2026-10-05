# 原生登录与验证码

`LoginActivity` 使用原生 Material 3 表单，`LoginViewModel` 调用 API 并在获取有效的当前用户资料后一次性保存会话。账号密码、邮箱口令、注册和找回密码均不加载网站登录页。密码和口令仅驻留内存，配置变更期间由 ViewModel 保留，不写入偏好设置或实例恢复状态。

文章手册、已发表作品等网站内容由 `SiteActivity` 承载；网站的登录导航会转至原生登录页。成功登录后同步网站 Cookie，继续查看网站内容。

## 接口

按 `ft-front/schema/openapi.json` 对接：

| 操作 | 接口 | 验证码 |
| --- | --- | --- |
| 账号密码登录 | `POST user/login`，JSON `username` 或 `email`、`password` | 需要 |
| 发送登录邮件 | `POST user/request_email_login`，JSON `email` | 需要 |
| 邮箱口令登录 | `GET user/verify_email_login?token=…` | 无需再次验证 |
| 注册 | `POST user/register`，JSON `username`、`email`、`password` | 需要 |
| 发送重置邮件 | `POST user/request_reset_password`，JSON `email` | 需要 |

验证码通过 `captcha_response`、`captcha_type` 查询参数传递。访客请求显式使用空的 `Token`，避免附带或清除此前的会话。获得登录令牌后调用 `user/get_user` 确认身份。

## 复用验证码

独立组件位于 `com.fimtale.ui.captcha`，资源为 `dialog_captcha.xml`、`captcha_strings.xml` 与 `assets/captcha.html`。标题、提示、进度和操作按钮均为原生 MD3 控件，仅供应商验证控件运行在 WebView 内。

在 Activity 创建时连接保留的页面状态：

```java
captcha = new CaptchaDialogHost(this, "screen_captcha_result",
        () -> model.captchaRequested, model::captchaResult);
```

页面每次渲染以及 `onPostResume()` 时调用 `captcha.sync()`。调用方使用独立结果键，回调接收 `(token, provider)`；取消时两个参数均为 `null`。请求进行中禁止重复提交，处理回调后清除 `captchaRequested`。

组件默认选择 Cloudflare。出错后原生按钮在尚未尝试的供应商中随机切换，全部尝试后开始下一轮；成功的供应商会作为后续验证的首选，登录和编辑器共享该偏好。弹窗重建保留调用方结果键和轮换状态，拒绝旧文档回调。

## 验证

`LoginActivityTest` 使用进程内 API 模拟响应，覆盖验证顺序、两种登录、错误重试、取消、旋转、进程重建以及注册、找回密码。`CaptchaDialogTest` 覆盖组件状态与结果隔离。`NativeLoginDeviceTest` 与 `CaptchaDialogDeviceTest` 使用真实 Android WebView 和本地脚本替身验证界面与回调，不发送真实登录或邮件请求。
