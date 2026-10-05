# MDI 图标

应用界面图标通过 [Android-Iconics](https://github.com/mikepenz/Android-Iconics) 的
Community Material 字体包绘制，字体随 APK 打包，离线可用。
桌面图标、关于页和分享图中的 FimTale 品牌 Logo 保留原品牌图片；
用户头像、作品图片、勋章和评论表情属于内容图片。

依赖在 `gradle/libs.versions.toml` 中管理：

- `com.mikepenz:iconics-core:5.4.0`
- `com.mikepenz:community-material-typeface:7.0.96.0-kotlin`

这组版本适用于当前 Java + Android Views 项目，无需 Kotlin Gradle 插件，
也不引入新版 Iconics 的 Compose 依赖。字体在 `FimTaleApplication` 中注册。

## Java

```java
import com.fimtale.utils.MdiIcons;

imageView.setImageDrawable(MdiIcons.drawable(this, "account"));
button.setIcon(MdiIcons.drawable(this, "book-open-page-variant"));
```

默认大小为 20dp（统一由 `R.dimen.mdi_icon_size` 控制），颜色来自当前主题的 `colorOnSurface`。传入 Activity 或 View 的
Context 以取得正确主题，每次调用返回独立 Drawable。控件的 tint 继续处理颜色和选中状态。
`arrow-left`、`arrow-right` 会随 RTL 布局镜像。

名称支持 `account`、`mdi-account` 和 `cmd_account`。也可使用
`CommunityMaterial.Icon`、`Icon2`、`Icon3` 中的枚举，以及已有的大小/颜色重载：

```java
MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_account, 32, Color.BLUE);
```

在 [MDI 图标目录](https://pictogrammers.com/library/mdi/) 查找名称，可用图标以当前
依赖中的枚举为准。名称错误会直接报错，新增后需验证布局加载。

## XML 布局

通过 `com.fimtale.ui.icons` 下的控件指定字体图标名称，不再新建包含 `pathData` 的图标 XML：

```xml
<com.fimtale.ui.icons.MdiImageView
    android:layout_width="@dimen/mdi_icon_size"
    android:layout_height="@dimen/mdi_icon_size"
    app:mdiIcon="history"
    app:tint="?attr/colorOnSurface" />

<com.fimtale.ui.icons.MdiButton
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="搜索"
    app:iconSize="@dimen/mdi_icon_size"
    app:mdiIcon="magnify" />

<com.fimtale.ui.icons.MdiTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="100"
    app:mdiDrawableStart="eye-outline"
    app:mdiIconSize="20dp" />
```

布局根节点需声明 `xmlns:app="http://schemas.android.com/apk/res-auto"`。
`MdiImageButton` 和 `MdiShapeableImageView` 分别用于图标按钮和带形状的头像占位图。
`MdiTextView` 也支持 `mdiDrawableTop`；复合图标默认跟随文字颜色。
`MdiButton` 的显示尺寸和颜色继续使用 MaterialButton 的 `iconSize`、`iconTint`。
`MdiToolbar` 使用 `mdiNavigationIcon="arrow-left"` 设置返回图标，并自动设置 MDI 更多菜单图标。
ActionBar 的返回按钮通过 `setHomeAsUpIndicator(MdiIcons.drawable(...))` 设置。
`MdiTextInputLayout` 提供 MDI 清空/错误图标；输入框自身的错误提示使用 `MdiIcons.setError`。

## 菜单与状态

菜单 XML 只声明动作，图标在加载时设置：

```java
MdiIcons.inflateMenu(this, getMenuInflater(), R.menu.menu_search, menu);
```

`MdiToolbar.inflateMenu` 会自动处理菜单图标。已加载的菜单（例如底部导航）可调用
`MdiIcons.applyMenu(context, menu, menuRes)`。新增菜单动作时，在该方法中补充图标映射。

点赞、收藏、HP 的实心/描边状态由 `WorkActions` 更新。
阅读器使用 `MdiIcons.battery` 显示电量及充电状态，图标按 10% 分档，旁边数字保留精确百分比。
背景、圆角、渐变等非图标 drawable 继续使用原有 XML。

`MdiLayoutTest` 验证日间/夜间布局加载、菜单、字体实际绘制和所有电池状态，需 Android 测试设备：

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fimtale.MdiLayoutTest
```

Android-Iconics 代码采用 Apache-2.0；MDI 图标采用
[Pictogrammers Free License](https://pictogrammers.com/docs/general/license/)。
