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
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                password = "pk.eyJ1Ijoiam10MTIzNCIsImEiOiJjbTNxNHpzZTIwazF2MmpzYXl1cHNmb3N0In0.n_Fmbo5tWeqAV70hEoWqJA"
            }
        }
    }

    rootProject.name = "MultiWay"
    include(":app")
}