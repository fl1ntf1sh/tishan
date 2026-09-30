package com.tishan.app.core.navigation

enum class TishanDestination(
    val route: String,
    val label: String,
) {
    Practice(route = "practice", label = "刷题"),
    Exam(route = "exam", label = "模拟"),
    Review(route = "review", label = "复习"),
    Manage(route = "manage", label = "管理"),
    Settings(route = "settings", label = "设置"),
}
