import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission
import versioning.BuildConfig

plugins {
    `maven-publish`
    void.`base-conventions`
    void.`shadow-conventions`
    id("de.eldoria.plugin-yml.bukkit") version "0.8.0"
    id("xyz.jpenilla.run-paper") version "3.0.0-beta.1"
}

repositories {
    // 1. Fallback for non-exclusive deps (e.g. Maven Central deps)
    if (BuildConfig.mavenLocalOverride) mavenLocal()

    // 2. Exclusive Repositories (One HTTP request per dep)
    exclusive("https://repo.papermc.io/repository/maven-public/", { name = "papermc" }) {
        includeGroup("io.papermc.paper")
        includeGroup("net.md-5")
    }

    exclusive("https://libraries.minecraft.net", { mavenContent { releasesOnly() } }) {
        includeModule("com.mojang", "brigadier")
    }

    exclusive("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        includeGroup("me.clip")
    }

    exclusive("https://repo." + "g" + "rim" + ".ac/snapshots") {
        includeGroup("ac.voidac")
        includeGroup("com.github.retrooper")
    }

    exclusive("https://nexus.scarsz.me/content/repositories/releases", { mavenContent { releasesOnly() } }) {
        includeGroup("github.scarsz")
    }

    mavenCentral()
}


dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.placeholderapi)

    if (BuildConfig.shadePE) {
        implementation(libs.packetevents.spigot)
    } else {
        compileOnly(libs.packetevents.spigot)
    }
    implementation(libs.cloud.paper)
    implementation(libs.adventure.platform.bukkit)

    // Service-loaded, so nothing references it at compile time; see the minimize exclusion
    // in void.shadow-conventions.
    runtimeOnly(libs.slf4j.jdk14)

    implementation(project(":common"))
    shadow(project(":common"))
}

bukkit {
    name = "Void"
    author = "Void"
    main = "ac.voidac.platform.bukkit.VoidBukkitLoaderPlugin"
    website = "https://void.ac/"
    apiVersion = "1.13"
    foliaSupported = true

    if (!BuildConfig.shadePE) {
        depend = listOf("packetevents")
    }

    softDepend = listOf(
        "ProtocolLib",
        "ProtocolSupport",
        "Essentials",
        "ViaVersion",
        "ViaBackwards",
        "ViaRewind",
        "Geyser-Spigot",
        "floodgate",
        "FastLogin",
        "PlaceholderAPI",
        // Driver holder mods — softdepend so each backend's driver class
        // resolves through the linked classloader.
        "sqlite-jdbc",
        "mysql-jdbc",
        "postgresql-jdbc",
        "mongodb-driver",
        "jedis",
    )

    permissions {
        register("void.alerts") {
            description = "Receive alerts for violations"
            default = Permission.Default.OP
        }

        register("void.alerts.enable-on-join") {
            description = "Enable alerts on join"
            default = Permission.Default.OP
        }

        register("void.performance") {
            description = "Check performance metrics"
            default = Permission.Default.OP
        }

        register("void.profile") {
            description = "Check user profile"
            default = Permission.Default.OP
        }

        register("void.brand") {
            description = "Show client brands on join"
            default = Permission.Default.OP
        }

        register("void.brand.enable-on-join") {
            description = "Enable showing client brands on join"
            default = Permission.Default.OP
        }

        register("void.sendalert") {
            description = "Send cheater alert"
            default = Permission.Default.OP
        }

        register("void.nosetback") {
            description = "Disable setback"
            default = Permission.Default.FALSE
        }

        register("void.nomodifypacket") {
            description = "Disable modifying packets"
            default = Permission.Default.FALSE
        }

        register("void.exempt") {
            description = "Exempt from all checks"
            default = Permission.Default.FALSE
        }

        register("void.storageesp") {
            description = "Enable storage decoy anti-ESP"
            default = Permission.Default.FALSE
        }

        register("void.punish") {
            description = "Manually punish (ban) a player via /void punish"
            default = Permission.Default.OP
        }

        register("void.banwave") {
            description = "Manage the ban-wave queue (list, add, remove, clear, info)"
            default = Permission.Default.OP
        }

        register("void.banwave.execute") {
            description = "Execute a ban wave (bans all queued players)"
            default = Permission.Default.OP
        }

        register("void.verbose") {
            description = "Receive verbose alerts for violations"
            default = Permission.Default.OP
        }

        register("void.verbose.enable-on-join") {
            description =
                "Enable verbose alerts on join"
            default = Permission.Default.FALSE
        }

        register("void.list") {
            description =
                "Shows lists of specific data"
            default = Permission.Default.FALSE
        }

    }
}

tasks {
    runServer {
        val javaToolchains = project.extensions.getByType<JavaToolchainService>()
        javaLauncher = javaToolchains.launcherFor {
            vendor = JvmVendorSpec.JETBRAINS
            languageVersion = JavaLanguageVersion.of(25)
        }
        minecraftVersion("26.1.2")
    }

    shadowJar {
        archiveFileName = "void-${project.version}.jar"
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
        doFirst {
            destinationDirectory.get().asFile
                .listFiles { f -> f.extension == "jar" }
                ?.forEach { it.delete() }
        }
    }
}
