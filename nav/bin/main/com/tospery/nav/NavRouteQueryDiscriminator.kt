package com.tospery.nav

/**
 * 根据查询参数名判断某个路由定义是否取得当前 URL 的解释权。
 *
 * 该类型只负责选择路由族，不验证参数值或完整参数组合；参数的业务约束仍由
 * 对应路由解析器负责。
 */
data class NavRouteQueryDiscriminator(
    val parameterNames: Set<String>,
) {
    init {
        require(parameterNames.isNotEmpty()) {
            "Query discriminator parameter names must not be empty."
        }
        require(parameterNames.none(String::isBlank)) {
            "Query discriminator parameter names must not be blank."
        }
    }

    fun matches(queryParameters: Map<String, String>): Boolean {
        return queryParameters.keys.any(parameterNames::contains)
    }
}
