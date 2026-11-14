pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos SDK 저장소: Pleos Playground 개발 문서(Getting Started)에 안내된 주소와 접근 정보를 넣으세요.
        // 지금 버전의 앱은 Pleos SDK 없이도 빌드되도록 만들었습니다.
        // maven { url = uri("PLEOS_MAVEN_URL") }
    }
}
rootProject.name = "SalsalDrive"
include(":app")
