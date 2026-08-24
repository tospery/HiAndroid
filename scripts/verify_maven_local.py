#!/usr/bin/env python3
"""校验 HiAndroid 发布到 Maven Local 的构件和内部依赖。"""

from pathlib import Path
from xml.etree import ElementTree

GROUP_ID = "com.tospery"
VERSION = "0.0.2"
REPOSITORY = Path.home() / ".m2/repository/com/tospery"

MODULES = {
    "base": ("jar", set()),
    "core": ("aar", {("base", "compile")}),
    "nav": ("jar", {("base", "compile")}),
    "net": ("jar", {("base", "compile")}),
    "net-retrofit": (
        "jar",
        {
            ("base", "compile"),
            ("net", "compile"),
        },
    ),
    "suite": (
        "aar",
        {
            ("base", "compile"),
            ("core", "compile"),
            ("nav", "compile"),
            ("net", "compile"),
            ("net-retrofit", "runtime"),
        },
    ),
    "suite-aliyun-emas-core": (
        "aar",
        {("base", "compile")},
    ),
    "suite-umeng-core": (
        "aar",
        {("base", "compile")},
    ),
    "suite-analytics-umeng": (
        "aar",
        {
            ("base", "compile"),
            ("suite-umeng-core", "compile"),
        },
    ),
    "suite-performance-umeng": (
        "aar",
        {
            ("base", "compile"),
            ("suite-umeng-core", "compile"),
        },
    ),
    "suite-nav-compose": (
        "aar",
        {("nav", "compile")},
    ),
    "suite-nav-umeng-ulink": (
        "aar",
        {
            ("base", "compile"),
            ("suite-umeng-core", "compile"),
        },
    ),
    "suite-share-umeng": (
        "aar",
        {
            ("base", "compile"),
            ("suite-umeng-core", "compile"),
        },
    ),
    "github-model-core": ("jar", set()),
    "github-trending": (
        "jar",
        {("github-model-core", "compile")},
    ),
}


def local_name(element):
    """移除 Maven POM XML 元素的命名空间。"""
    return element.tag.rsplit("}", 1)[-1]


def child_text(parent, name):
    """读取指定直接子元素的文本。"""
    for child in parent:
        if local_name(child) == name:
            return child.text
    return None


def verify_module(artifact_id, main_extension, expected_dependencies):
    """校验单个模块的文件、坐标和内部依赖。"""
    artifact_directory = REPOSITORY / artifact_id / VERSION

    required_files = (
        f"{artifact_id}-{VERSION}.{main_extension}",
        f"{artifact_id}-{VERSION}-sources.jar",
        f"{artifact_id}-{VERSION}-javadoc.jar",
        f"{artifact_id}-{VERSION}.pom",
        f"{artifact_id}-{VERSION}.module",
    )

    missing_files = [
        filename
        for filename in required_files
        if not (artifact_directory / filename).is_file()
    ]

    if missing_files:
        raise RuntimeError(
            f"{artifact_id} 缺少发布文件：{missing_files}"
        )

    pom_path = artifact_directory / f"{artifact_id}-{VERSION}.pom"
    pom_root = ElementTree.parse(pom_path).getroot()

    actual_coordinate = (
        child_text(pom_root, "groupId"),
        child_text(pom_root, "artifactId"),
        child_text(pom_root, "version"),
    )
    expected_coordinate = (GROUP_ID, artifact_id, VERSION)

    if actual_coordinate != expected_coordinate:
        raise RuntimeError(
            f"{artifact_id} 坐标错误："
            f"实际 {actual_coordinate}，"
            f"预期 {expected_coordinate}"
        )

    actual_dependencies = set()

    for dependency in pom_root.iter():
        if local_name(dependency) != "dependency":
            continue

        if child_text(dependency, "groupId") != GROUP_ID:
            continue

        dependency_artifact = child_text(
            dependency,
            "artifactId",
        )
        dependency_version = child_text(
            dependency,
            "version",
        )
        dependency_scope = child_text(
            dependency,
            "scope",
        )

        if dependency_version != VERSION:
            raise RuntimeError(
                f"{artifact_id} 的内部依赖版本错误："
                f"{dependency_artifact}:{dependency_version}"
            )

        actual_dependencies.add(
            (dependency_artifact, dependency_scope)
        )

    if actual_dependencies != expected_dependencies:
        raise RuntimeError(
            f"{artifact_id} 内部依赖错误："
            f"实际 {sorted(actual_dependencies)}，"
            f"预期 {sorted(expected_dependencies)}"
        )

    print(
        f"{GROUP_ID}:{artifact_id}:{VERSION} "
        f"-> {main_extension} + sources + javadoc + pom + module"
    )


def main():
    """校验全部 Maven Local 发布模块。"""
    for artifact_id, configuration in MODULES.items():
        main_extension, expected_dependencies = configuration
        verify_module(
            artifact_id,
            main_extension,
            expected_dependencies,
        )

    print(f"{len(MODULES)} 个 Maven Local 构件及内部依赖全部校验通过")


if __name__ == "__main__":
    main()
