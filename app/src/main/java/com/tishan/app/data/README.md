# Data layer

本目录承载 Room 数据库、JSON 导入/导出、SAF 文件访问及仓储实现。UI 与 domain 层不得直接依赖 Room Entity 或 Android 文件 API。

P02 完成了领域规则和 Room 基础；P03 增加了先预检后事务写入的题库仓储、普通更新/整库覆盖、管理统计、JSON 公共字段导出、SAF 保存和 FileProvider 分享。
