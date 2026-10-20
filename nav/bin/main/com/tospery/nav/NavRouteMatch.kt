package com.tospery.nav

/**
 * 已注册路由定义与实际 path 匹配后的结果。
 *
 * [pathParameters] 只包含 path template 中声明的动态 segment；
 * query 参数仍由 [NavRoute.parse] 解析，二者保持不同协议边界。
 */
data class NavRouteMatch(
    val definition: NavRouteDefinition,
    val pathParameters: Map<String, String> = emptyMap(),
)
