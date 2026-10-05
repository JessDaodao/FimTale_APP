# MDI 图标

项目通过 [Android-Iconics](https://github.com/mikepenz/Android-Iconics) 的
Community Material 字体包接入 [Material Design Icons（MDI）](https://pictogrammers.com/library/mdi/)。
字体随 APK 打包，使用时不需要联网。

依赖在 `gradle/libs.versions.toml` 中统一管理：

- `com.mikepenz:iconics-core:5.4.0`
- `com.mikepenz:community-material-typeface:7.0.96.0-kotlin`

这组版本适用于当前 Java + Android Views 项目，避免引入新版 Iconics 的 Compose 依赖。
虽然字体包版本带有 `kotlin` 后缀，但 Java 可直接调用，无需添加 Kotlin Gradle 插件。
字体在 `FimTaleApplication` 中注册。

## Java 中使用

```java
import android.graphics.Color;
import com.fimtale.utils.MdiIcons;
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial;

// 默认 24dp，颜色取当前主题的 colorOnSurface。
imageView.setImageDrawable(
        MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_account));

// MaterialButton 和菜单项也可以直接使用。
button.setIcon(MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_book_open_page_variant));
menu.findItem(R.id.nav_profile).setIcon(
        MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_account));

// 自定义大小和颜色，颜色参数为 @ColorInt，而非 R.color 资源 ID。
imageView.setImageDrawable(
        MdiIcons.drawable(this, CommunityMaterial.Icon.cmd_account, 32, Color.BLUE));
```

传入当前 Activity 或 View 的 Context，以便读取正确的日间/夜间主题。
每次调用返回独立 Drawable；控件自己的 tint（包括导航选中状态和按钮状态）仍然生效。
当前主界面的文章、个人导航及对应标题图标已使用这套入口。

## 查找图标

MDI 的 `mdi-account` 对应 Iconics 的 `cmd_account`，`mdi-book-open-page-variant`
对应 `cmd_book_open_page_variant`。图标按字母顺序分布在 `CommunityMaterial.Icon`、
`Icon2`、`Icon3` 中，可通过 IDE 补全查找。可用集合以当前依赖中的枚举为准，
MDI 网站上的新图标不一定已包含在该版本中。

已有 XML 布局可继续使用原来的 ImageView、MaterialButton 和菜单，通过 Java 设置图标；
字体包不会自动生成 `@drawable/mdi_*` 资源。

Android-Iconics 代码使用 Apache-2.0 许可证；MDI 图标采用
[Pictogrammers Free License](https://pictogrammers.com/docs/general/license/)。
