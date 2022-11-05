plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "net.azisaba"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://repo.blueberrymc.net/repository/maven-public/") }
}

dependencies {
    implementation("org.javassist:javassist:3.29.2-GA")
    implementation("net.blueberrymc:native-util:2.1.0")
    compileOnly("org.spigotmc:spigot-api:1.15.2-R0.1-SNAPSHOT")
}

tasks {
    shadowJar {
        relocate("javassist", "net.azisaba.combatmath.libs.javassist")
        archiveClassifier.set("")
    }
}
