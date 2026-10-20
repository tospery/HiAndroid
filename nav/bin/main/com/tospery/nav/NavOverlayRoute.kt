package com.tospery.nav

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

private const val MAX_OVERLAY_TITLE_LENGTH = 120
private const val MAX_OVERLAY_MESSAGE_LENGTH = 2_000
private const val MAX_OVERLAY_ACTION_TITLE_LENGTH = 48
private const val MAX_OVERLAY_ACTION_COUNT = 4
private val overlayIdentifierPattern = Regex("[A-Za-z0-9._-]{1,64}")

/**
 * 预置弹层的稳定标识，由 App 或 feature 的白名单映射到具体 UI 与业务动作。
 */
@JvmInline
value class NavOverlayId(
    val value: String,
) {
    init {
        require(overlayIdentifierPattern.matches(value)) {
            "NavOverlayId must contain 1 to 64 URL-safe characters."
        }
    }

    override fun toString(): String = value
}

/**
 * 自动生成弹层中单个动作的稳定结果标识。
 */
@JvmInline
value class NavOverlayActionId(
    val value: String,
) {
    init {
        require(overlayIdentifierPattern.matches(value)) {
            "NavOverlayActionId must contain 1 to 64 URL-safe characters."
        }
    }

    override fun toString(): String = value
}

enum class NavOverlayActionType {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE,
    DISMISS,
}

data class NavOverlayAction(
    val id: NavOverlayActionId,
    val type: NavOverlayActionType,
    val title: String,
) {
    init {
        require(title.isNotBlank()) { "NavOverlayAction title must not be blank." }
        require(title.length <= MAX_OVERLAY_ACTION_TITLE_LENGTH) {
            "NavOverlayAction title is too long."
        }
    }
}

/**
 * 页面级 Dialog/Sheet 的 URL 路由模型。
 *
 * [Predefined] 只携带白名单 id；[Generated] 只携带受限展示数据和动作结果 id，
 * 不允许 URL 携带回调、类名或任意可执行逻辑。
 */
sealed interface NavOverlayRoute {
    val presentation: NavPresentation

    fun toNavRoute(): NavRoute

    fun toUri(scheme: UrlScheme): String =
        "${scheme.normalized()}://${toNavRoute().value}"

    data class Predefined(
        override val presentation: NavPresentation,
        val id: NavOverlayId,
    ) : NavOverlayRoute {
        init {
            presentation.requireOverlayPresentation()
        }

        override fun toNavRoute(): NavRoute =
            navRoute(presentation.overlayPath) {
                query(NavOverlayRouteArguments.ID, id.value)
            }
    }

    data class Generated(
        override val presentation: NavPresentation,
        val title: String,
        val message: String? = null,
        val actions: List<NavOverlayAction>,
    ) : NavOverlayRoute {
        init {
            presentation.requireOverlayPresentation()
            require(title.isNotBlank()) { "Generated overlay title must not be blank." }
            require(title.length <= MAX_OVERLAY_TITLE_LENGTH) {
                "Generated overlay title is too long."
            }
            require(message == null || message.isNotBlank()) {
                "Generated overlay message must be null or non-blank."
            }
            require(message == null || message.length <= MAX_OVERLAY_MESSAGE_LENGTH) {
                "Generated overlay message is too long."
            }
            require(actions.size in 1..MAX_OVERLAY_ACTION_COUNT) {
                "Generated overlay must contain 1 to 4 actions."
            }
            require(actions.map(NavOverlayAction::id).distinct().size == actions.size) {
                "Generated overlay action ids must be unique."
            }
        }

        override fun toNavRoute(): NavRoute =
            navRoute(presentation.overlayPath) {
                query(NavOverlayRouteArguments.TITLE, title)
                query(NavOverlayRouteArguments.MESSAGE, message)
                query(
                    NavOverlayRouteArguments.ACTIONS,
                    NavOverlayActionsJsonAdapter.toJson(actions),
                )
            }
    }

    companion object {
        fun parseOrNull(route: NavRoute): NavOverlayRoute? =
            runCatching { parse(route.parse()) }.getOrNull()

        fun parseOrNull(route: ParsedNavRoute): NavOverlayRoute? =
            runCatching { parse(route) }.getOrNull()

        private fun parse(route: ParsedNavRoute): NavOverlayRoute {
            val presentation = navPresentationFromOverlayPath(route.path)
            val parameters = route.queryParameters
            val id = parameters[NavOverlayRouteArguments.ID]

            if (id != null) {
                require(parameters.keys == setOf(NavOverlayRouteArguments.ID)) {
                    "Predefined overlay routes only accept the id parameter."
                }
                return Predefined(
                    presentation = presentation,
                    id = NavOverlayId(id),
                )
            }

            require(
                parameters.keys.all(NavOverlayRouteArguments.generatedParameterNames::contains),
            ) {
                "Generated overlay route contains unknown parameters."
            }

            val title = requireNotNull(parameters[NavOverlayRouteArguments.TITLE]) {
                "Generated overlay route requires a title."
            }
            val actionsJson = requireNotNull(parameters[NavOverlayRouteArguments.ACTIONS]) {
                "Generated overlay route requires actions."
            }
            val actions = requireNotNull(NavOverlayActionsJsonAdapter.fromJson(actionsJson)) {
                "Generated overlay route actions must not be null."
            }

            return Generated(
                presentation = presentation,
                title = title,
                message = parameters[NavOverlayRouteArguments.MESSAGE],
                actions = actions,
            )
        }
    }
}

/**
 * Navigation Compose 等平台 adapter 注册弹层 destination 时使用的稳定参数名。
 */
object NavOverlayRouteArguments {
    const val ID: String = "id"
    const val TITLE: String = "title"
    const val MESSAGE: String = "message"
    const val ACTIONS: String = "actions"

    internal val generatedParameterNames = setOf(TITLE, MESSAGE, ACTIONS)
    internal val discriminatorParameterNames = generatedParameterNames + ID
}

fun navOverlayRoutePattern(presentation: NavPresentation): String {
    presentation.requireOverlayPresentation()
    return buildString {
        append(navOverlayRoutePath(presentation))
        append("?")
        append(NavOverlayRouteArguments.ID)
        append("={")
        append(NavOverlayRouteArguments.ID)
        append("}&")
        append(NavOverlayRouteArguments.TITLE)
        append("={")
        append(NavOverlayRouteArguments.TITLE)
        append("}&")
        append(NavOverlayRouteArguments.MESSAGE)
        append("={")
        append(NavOverlayRouteArguments.MESSAGE)
        append("}&")
        append(NavOverlayRouteArguments.ACTIONS)
        append("={")
        append(NavOverlayRouteArguments.ACTIONS)
        append("}")
    }
}

fun navOverlayRoutePath(presentation: NavPresentation): String {
    presentation.requireOverlayPresentation()
    return presentation.overlayPath
}

fun navOverlayRouteDefinition(
    presentation: NavPresentation,
    requiresAuth: Boolean = false,
): NavRouteDefinition {
    presentation.requireOverlayPresentation()
    return NavRouteDefinition(
        id = NavRouteId(presentation.overlayPath),
        path = presentation.overlayPath,
        queryDiscriminator =
            NavRouteQueryDiscriminator(
                parameterNames =
                    NavOverlayRouteArguments.discriminatorParameterNames,
            ),
        requiresAuth = requiresAuth,
        presentation = presentation,
    )
}

private val NavPresentation.overlayPath: String
    get() =
        when (this) {
            NavPresentation.DIALOG -> "dialog"
            NavPresentation.SHEET -> "sheet"
            NavPresentation.SCREEN -> error("Screen presentation is not an overlay route.")
        }

private fun NavPresentation.requireOverlayPresentation() {
    require(this != NavPresentation.SCREEN) {
        "NavOverlayRoute only supports dialog and sheet presentations."
    }
}

private fun navPresentationFromOverlayPath(path: String): NavPresentation {
    return when (path.trim().trim('/')) {
        NavPresentation.DIALOG.overlayPath -> NavPresentation.DIALOG
        NavPresentation.SHEET.overlayPath -> NavPresentation.SHEET
        else -> throw IllegalArgumentException("Unknown overlay route path.")
    }
}

private object NavOverlayActionsJsonAdapter : JsonAdapter<List<NavOverlayAction>>() {
    private val fieldOptions = JsonReader.Options.of("id", "type", "title")

    override fun fromJson(reader: JsonReader): List<NavOverlayAction>? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }

        val actions = mutableListOf<NavOverlayAction>()
        reader.beginArray()
        while (reader.hasNext()) {
            var id: String? = null
            var type: String? = null
            var title: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.selectName(fieldOptions)) {
                    0 -> id = reader.nextString()
                    1 -> type = reader.nextString()
                    2 -> title = reader.nextString()
                    -1 -> {
                        reader.skipName()
                        reader.skipValue()
                    }
                }
            }
            reader.endObject()

            val actionType =
                runCatching {
                    NavOverlayActionType.valueOf(requireNotNull(type).uppercase())
                }.getOrElse {
                    throw JsonDataException("Unknown overlay action type.")
                }
            actions +=
                NavOverlayAction(
                    id = NavOverlayActionId(requireNotNull(id)),
                    type = actionType,
                    title = requireNotNull(title),
                )
        }
        reader.endArray()
        return actions
    }

    override fun toJson(
        writer: JsonWriter,
        value: List<NavOverlayAction>?,
    ) {
        if (value == null) {
            writer.nullValue()
            return
        }

        writer.beginArray()
        value.forEach { action ->
            writer.beginObject()
            writer.name("id").value(action.id.value)
            writer.name("type").value(action.type.name.lowercase())
            writer.name("title").value(action.title)
            writer.endObject()
        }
        writer.endArray()
    }
}
