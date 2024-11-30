pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                password = "pk.eyJ1Ijoiam10MTIzNCIsImEiOiJjbTNxNHpzZTIwazF2MmpzYXl1cHNmb3N0In0.n_Fmbo5tWeqAV70hEoWqJA"
            }
        }
    }

    rootProject.name = "MultiWay"
    include(":app")
}