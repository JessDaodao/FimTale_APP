# 审核队列

入口位于“我的”页，读取 `user/get_user_auth` 后仅对 `role_id >= 2` 的编辑开放。队列页再次校验权限；分配审核员仅对 `role_id >= 4` 的管理员开放，最终权限仍由 API 判定。

界面沿用 `activity_history.xml` 的悬浮页眉：16dp 外边距与圆角，列表内容从页眉下方开始，向上滚动时页眉保持固定并以 200ms 动画升至 4dp 阴影。筛选与分页属于同一滚动列表。普通用户不会看到审核入口。

## 对接来源

参考 `ft-front/app/pages/admin/review.vue`、`usePermissions.ts` 以及 `ft-front/schema/openapi.json` 的当前接口契约。工作区中的 `ft-go` 仍包含旧版作品审核字段，未包含独立审核队列接口；其作品可读权限和编辑角色用于交叉核对。不要把旧作品的 `status_review`（0 已通过、1 编辑中、2 审核中）当成审核记录的状态。

| 操作 | 接口 | 参数 |
| --- | --- | --- |
| 分配给我 | `GET work/get_reviews` | `status=1`、当前 `assigned_user_id` |
| 全部待审 | 同上 | `status=1` |
| 历史记录 | 同上 | 不传状态，或 `status=2/3` |
| 作品审核历史 | 同上 | `work_id`，支持清除筛选 |
| 指定审核记录 | 同上 | `review_id`，覆盖作品、状态、分配筛选 |
| 通过、退回 | `POST work/resolve_review` | JSON `review_id`、`status=2/3`；退回另传 `reason` 和可选 `resubmit_after` |
| 审核员列表 | `GET user/get_team_members` | 只展示编辑及以上成员，并保留当前分配人员 |
| 保存分配 | `POST work/set_review_assignments` | JSON `review_id`、`reviewer_user_ids` |
| 处理人姓名 | `GET user/get_username_by_id` | 缺少分配姓名时按 `user_id` 查询并缓存 |

每页 20 条，接口不返回总数；与网页相同，满页时显示下一页。通过与退回成功后重新读取当前队列，保存分配后也刷新，以更新“分配给我”视图。

## 状态与验证

`ReviewQueueViewModel` 保留筛选、页码、操作弹窗草稿和请求，屏幕旋转不会重发审核操作。退回的日期时间通过原生 MD3 日期、时间选择器选择，本地时区转换为 UTC ISO 8601 后提交。进程重建只恢复筛选和页码，不自动恢复或提交审核决定。

取消请求和过期响应不会覆盖新筛选结果；切换账户或登录失效后清空队列与审核员信息。发送审核操作后若无法确认结果，要求先刷新核对，避免直接重复提交。

`ReviewQueueTest` 使用本地 API 模拟响应测试权限、筛选、分页、审核与分配载荷、取消、旋转、登录失效和进程恢复。`ReviewQueueDeviceTest` 验证悬浮页眉、权限下的操作可见性，以及深色模式下的退回弹窗、日期时间选择器和重建。测试不操作真实审核记录。
