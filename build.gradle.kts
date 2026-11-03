plugins {
    id("java-library")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
    // 2.14 is needed to compile the 26.3 packet path; servers below 26.3 still run on 2.13.
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    // Bundled with every Minecraft server, so it is only needed at compile time.
    compileOnly("it.unimi.dsi:fastutil:8.5.15")

    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
