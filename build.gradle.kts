buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath ("ru.vyarus:gradle-pom-plugin:2.1.0")
    }
}

plugins {
    kotlin("jvm") version "1.3.70"
    `maven-publish`
    id("ru.vyarus.pom") version "2.1.0"
    maven
}

group = "space.iseki"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}


publishing {
    repositories {
        maven {
            name = "iniHelperKt"
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("https://maven.pkg.github.com/cpdyj/iniHelperKt")
            credentials {
                username = System.getenv("GithubUserId")
                password = System.getenv("GithubAPIKey")
            }
        }

    }
    publications {
        register("mavenJava", MavenPublication::class) {
            artifact("./build/libs/${rootProject.name}-$version.jar")

        }
    }
}
