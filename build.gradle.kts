import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    // 在根工程统一加载插件，子模块应用时不再重复加载 Kotlin 插件。
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

/**
 * 根据当前 Gradle 模块路径生成稳定的模块元数据。
 */
abstract class GenerateModuleMetadataTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val moduleMetadataPackage: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputFile = outputDirectory.get()
            .file("${moduleMetadataPackage.get().replace('.', '/')}/ModuleMetadata.kt")
            .asFile

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |package ${moduleMetadataPackage.get()}
            |
            |/**
            | * 由 Gradle 根据当前模块路径生成，禁止手动修改。
            | */
            |internal object ModuleMetadata {
            |    const val path: String = "${modulePath.get()}"
            |}
            |
            """.trimMargin(),
        )
    }
}

/**
 * 为所有 Toolkit 子模块注册模块元数据生成任务。
 */
subprojects {
    val moduleProjectPath = path
    val moduleIdentifier = moduleProjectPath
        .trim(':')
        .replace(Regex("[^A-Za-z0-9_]"), "_")
        .ifBlank { "root" }
    val moduleMetadataPackageName = "com.tospery.buildmetadata.module_$moduleIdentifier"
    val generatedModuleMetadataDirectory = layout.buildDirectory.dir(
        "generated/source/module-metadata/kotlin",
    )

    val generateModuleMetadata = tasks.register<GenerateModuleMetadataTask>(
        "generateModuleMetadata",
    ) {
        modulePath.set(moduleProjectPath)
        moduleMetadataPackage.set(moduleMetadataPackageName)
        outputDirectory.set(generatedModuleMetadataDirectory)
    }

    // 将生成目录接入纯 JVM 模块的主源码集。
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            sourceSets.named("main") {
                java.srcDir(generateModuleMetadata.flatMap { it.outputDirectory })
            }
        }
    }

    // 将生成目录接入 Android Library 的 Kotlin 主源码集。
    plugins.withId("com.android.library") {
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants(selector().all()) { variant ->
                variant.sources.kotlin?.addGeneratedSourceDirectory(
                    generateModuleMetadata,
                    GenerateModuleMetadataTask::outputDirectory,
                )
            }
        }
    }

    // 确保所有 Kotlin 编译任务开始前已经生成模块元数据。
    tasks.configureEach {
        if (name.startsWith("compile") && name.endsWith("Kotlin")) {
            dependsOn(generateModuleMetadata)
        }
    }
}

/**
 * 单个 Toolkit 模块的 Maven 发布元数据。
 */
data class MavenModuleMetadata(
    val artifactId: String,
    val displayName: String,
    val description: String,
)

val toolkitGroupId = "com.tospery"
val toolkitVersion = "0.0.2"
val toolkitRepositoryUrl =
    "https://github.com/tospery/android-toolkit"

/**
 * Maven 发布模块白名单。
 *
 * Gradle 模块路径与 Maven artifactId 在此处集中映射。
 */
val toolkitPublicationModules = mapOf(
    ":base" to MavenModuleMetadata(
        artifactId = "base",
        displayName = "Tospery Base",
        description =
            "Pure Kotlin reusable base abstractions and common models.",
    ),
    ":core" to MavenModuleMetadata(
        artifactId = "core",
        displayName = "Tospery Core",
        description =
            "Reusable Android and Compose core utilities.",
    ),
    ":nav" to MavenModuleMetadata(
        artifactId = "nav",
        displayName = "Tospery Navigation",
        description =
            "Platform-independent URL and URI navigation abstractions.",
    ),
    ":net" to MavenModuleMetadata(
        artifactId = "net",
        displayName = "Tospery Network",
        description =
            "Platform-independent networking abstractions and models.",
    ),
    ":net:retrofit" to MavenModuleMetadata(
        artifactId = "net-retrofit",
        displayName = "Tospery Network Retrofit",
        description =
            "Retrofit, OkHttp and Moshi implementation for Tospery Network.",
    ),
    ":suite" to MavenModuleMetadata(
        artifactId = "suite",
        displayName = "Tospery Suite",
        description =
            "Reusable Android app utilities and Compose components.",
    ),
    ":suite-analytics-umeng" to MavenModuleMetadata(
        artifactId = "suite-analytics-umeng",
        displayName = "Tospery Suite Analytics Umeng",
        description =
            "Umeng analytics adapter for Tospery Suite.",
    ),
    ":suite-performance-umeng" to MavenModuleMetadata(
        artifactId = "suite-performance-umeng",
        displayName = "Tospery Suite Performance Umeng",
        description =
            "Umeng U-APM performance monitoring adapter for Tospery Suite.",
    ),
    ":suite-nav-compose" to MavenModuleMetadata(
        artifactId = "suite-nav-compose",
        displayName = "Tospery Suite Navigation Compose",
        description =
            "Jetpack Navigation Compose adapter for Tospery Navigation.",
    ),
    ":suite-nav-umeng-ulink" to MavenModuleMetadata(
        artifactId = "suite-nav-umeng-ulink",
        displayName = "Tospery Suite Navigation Umeng U-Link",
        description =
            "Umeng U-Link wake-up and deferred navigation adapter.",
    ),
    ":suite-share-umeng" to MavenModuleMetadata(
        artifactId = "suite-share-umeng",
        displayName = "Tospery Suite Share Umeng",
        description =
            "Umeng U-Share adapter for vendor-independent share contracts.",
    ),
    ":github:model:core" to MavenModuleMetadata(
        artifactId = "github-model-core",
        displayName = "Tospery GitHub Model Core",
        description =
            "Reusable core models for GitHub integrations.",
    ),
    ":github:trending" to MavenModuleMetadata(
        artifactId = "github-trending",
        displayName = "Tospery GitHub Trending",
        description =
            "Kotlin API and HTML parser for GitHub Trending.",
    ),
)

/**
 * 只为发布白名单中的模块配置 Maven 坐标和 POM。
 */
subprojects {
    val publicationMetadata =
        toolkitPublicationModules[path] ?: return@subprojects

    // 嵌套 model/core 与根 core 的叶子名称相同，需要使用唯一的本地组件 group。
    // Maven 发布仍由 coordinates() 固定为 com.tospery:github-model-core。
    val compositeProjectGroup =
        if (path == ":github:model:core") {
            "$toolkitGroupId.github.model"
        } else {
            toolkitGroupId
        }

    group = compositeProjectGroup
    version = toolkitVersion

    // Maven 变体使用实际发布 artifactId；Composite 变体保留本地项目身份。
    val publicationCapability =
        listOf(
            toolkitGroupId,
            publicationMetadata.artifactId,
            toolkitVersion,
        ).joinToString(":")
    val projectCapability =
        listOf(
            compositeProjectGroup,
            project.name,
            toolkitVersion,
        ).joinToString(":")
    // 为纯 JVM 模块的公开 API 和运行时变体声明发布 capability。
    plugins.withId("java-library") {
        val apiElements = configurations.named("apiElements")
        val runtimeElements = configurations.named("runtimeElements")

        listOf(apiElements, runtimeElements).forEach { configuration ->
            configuration.configure {
                outgoing.capability(publicationCapability)
            }
        }

        // artifactId 与本地项目身份不一致时，为纯 JVM 项目依赖保留独立变体。
        // 该变体不加入 Java Component，因此不会进入 Maven Module Metadata。
        if (projectCapability != publicationCapability) {
            listOf(
                Triple("compositeApiElements", apiElements, Usage.JAVA_API),
                Triple(
                    "compositeRuntimeElements",
                    runtimeElements,
                    Usage.JAVA_RUNTIME,
                ),
            ).forEach { (configurationName, sourceElements, usageName) ->
                configurations.create(configurationName) {
                    isCanBeConsumed = true
                    isCanBeResolved = false
                    extendsFrom(*sourceElements.get().extendsFrom.toTypedArray())

                    attributes {
                        attribute(
                            Category.CATEGORY_ATTRIBUTE,
                            objects.named(Category::class.java, Category.LIBRARY),
                        )
                        attribute(
                            Bundling.BUNDLING_ATTRIBUTE,
                            objects.named(Bundling::class.java, Bundling.EXTERNAL),
                        )
                        attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            objects.named(
                                LibraryElements::class.java,
                                LibraryElements.JAR,
                            ),
                        )
                        attribute(
                            Usage.USAGE_ATTRIBUTE,
                            objects.named(Usage::class.java, usageName),
                        )
                        attribute(
                            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                            objects.named(
                                TargetJvmEnvironment::class.java,
                                TargetJvmEnvironment.STANDARD_JVM,
                            ),
                        )
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
                        attribute(
                            KotlinPlatformType.attribute,
                            KotlinPlatformType.jvm,
                        )
                    }

                    outgoing.artifact(tasks.named("jar"))
                    outgoing.capability(projectCapability)
                }
            }
        }

        // Maven JAR 没有 Kotlin 平台属性，而 Composite Build 会直接选择项目变体。
        // 额外提供 androidJvm 兼容变体，使 Android 模块能够消费纯 JVM Toolkit 模块。
        listOf("debug", "release").forEach { buildType ->
            listOf(
                Triple("ApiElements", apiElements, Usage.JAVA_API),
                Triple("RuntimeElements", runtimeElements, Usage.JAVA_RUNTIME),
            ).forEach { (configurationSuffix, sourceElements, usageName) ->
                val capitalizedBuildType =
                    buildType.replaceFirstChar { it.uppercaseChar() }

                configurations.create(
                    "android$capitalizedBuildType$configurationSuffix",
                ) {
                    isCanBeConsumed = true
                    isCanBeResolved = false
                    extendsFrom(*sourceElements.get().extendsFrom.toTypedArray())

                    attributes {
                        attribute(
                            Category.CATEGORY_ATTRIBUTE,
                            objects.named(Category::class.java, Category.LIBRARY),
                        )
                        attribute(
                            Bundling.BUNDLING_ATTRIBUTE,
                            objects.named(Bundling::class.java, Bundling.EXTERNAL),
                        )
                        attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            objects.named(
                                LibraryElements::class.java,
                                LibraryElements.JAR,
                            ),
                        )
                        attribute(
                            Usage.USAGE_ATTRIBUTE,
                            objects.named(Usage::class.java, usageName),
                        )
                        attribute(
                            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                            objects.named(
                                TargetJvmEnvironment::class.java,
                                TargetJvmEnvironment.ANDROID,
                            ),
                        )
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
                        attribute(
                            KotlinPlatformType.attribute,
                            KotlinPlatformType.androidJvm,
                        )
                        attribute(
                            AgpVersionAttr.ATTRIBUTE,
                            objects.named(
                                AgpVersionAttr::class.java,
                                libs.versions.agp.get(),
                            ),
                        )
                        attribute(
                            BuildTypeAttr.ATTRIBUTE,
                            objects.named(BuildTypeAttr::class.java, buildType),
                        )
                    }

                    outgoing.artifact(tasks.named("jar"))
                    outgoing.capability(projectCapability)
                }
            }
        }
    }

    // 为 Android Library 的 debug/release API 和运行时变体声明发布 capability。
    plugins.withId("com.android.library") {
        configurations.configureEach {
            if (
                name.endsWith("ApiElements") ||
                name.endsWith("RuntimeElements")
            ) {
                outgoing.capability(publicationCapability)
            }
        }
    }

    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<MavenPublishBaseExtension> {
        // 发布到 Maven Central，首次发布完成验证后在 Portal 中人工确认。
        publishToMavenCentral()

        // 正式发布时通过 signAllPublications=true 启用 GPG 签名，
        // 避免 Maven Local 和常规 CI 在没有私钥时执行签名任务。

        coordinates(
            groupId = toolkitGroupId,
            artifactId = publicationMetadata.artifactId,
            version = toolkitVersion,
        )

        pom {
            name.set(publicationMetadata.displayName)
            description.set(publicationMetadata.description)
            inceptionYear.set("2026")
            url.set(toolkitRepositoryUrl)

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set(
                        "https://www.apache.org/licenses/LICENSE-2.0.txt",
                    )
                    distribution.set("repo")
                }
            }

            developers {
                developer {
                    id.set("tospery")
                    name.set("Tospery")
                    url.set("https://github.com/tospery")
                }
            }

            scm {
                url.set(toolkitRepositoryUrl)
                connection.set(
                    "scm:git:git://github.com/tospery/" +
                        "android-toolkit.git",
                )
                developerConnection.set(
                    "scm:git:ssh://git@github.com/tospery/" +
                        "android-toolkit.git",
                )
            }
        }
    }
}
