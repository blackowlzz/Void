import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import versioning.BuildConfig

/**
 * void-bridge: the proxy-side companion plugin.
 * One jar for both Velocity and BungeeCord, carrying the wire protocol and the
 * proxy logic and none of the anticheat, so it stays small enough to drop on a
 * proxy without thinking about it.
 */
plugins {
    void.`base-conventions`
    id("com.gradleup.shadow")
}

repositories {
    if (BuildConfig.mavenLocalOverride) mavenLocal()

    exclusive("https://repo.papermc.io/repository/maven-public/", { name = "papermc" }) {
        includeGroup("com.velocitypowered")
        includeGroup("net.md-5")
    }

    // dragged in by velocity-api, published nowhere else
    exclusive("https://libraries.minecraft.net", { mavenContent { releasesOnly() } }) {
        includeModule("com.mojang", "brigadier")
    }

    mavenCentral()
}

// The wire protocol lives in :common next to the backend that speaks it, and
// gets compiled in here rather than depended on. A project dependency would
// drag PacketEvents and the whole engine onto the proxy classpath for five
// record classes. The include filter also covers this module's own sources, so
// everything here has to live under ac/voidac/bridge/proxy.
sourceSets.main {
    java {
        srcDir(rootProject.file("common/src/main/java"))
        include("ac/voidac/bridge/protocol/**", "ac/voidac/bridge/proxy/**")
    }
}

dependencies {
    compileOnly(libs.velocity.api)
    compileOnly(libs.bungeecord.api)
    compileOnly(libs.jetbrains.annotations)

    // bundled, not assumed: Bungee ships SnakeYAML, Velocity makes no promises,
    // and relocating ours means we can't collide with theirs
    implementation(libs.snakeyaml)
}

// otherwise spotless reaches through the shared source dir and reformats
// :common from here, which is both surprising and a race in a parallel build
spotless {
    java {
        target("src/main/java/**/*.java")
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName = "${rootProject.name}-bridge-${rootProject.version}.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    relocate("org.yaml.snakeyaml", "ac.voidac.bridge.shaded.snakeyaml")
    // no minimize(): SnakeYAML resolves plenty reflectively and a stripped
    // constructor only shows up as a crash on somebody's proxy months later

    // if the source-set filter above ever regresses, this keeps the anticheat
    // out of the proxy jar anyway
    exclude("ac/voidac/checks/**")
    exclude("ac/voidac/predictionengine/**")
    exclude("ac/voidac/manager/**")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

// delombok ignores the include filter above and tries to walk all of :common,
// where it dies on PacketEvents types this module deliberately doesn't have.
// Nothing here uses Lombok, and a proxy plugin has no use for javadoc or a
// sources jar full of :common, so all four come off.
listOf("delombok", "javadoc", "javadocJar", "sourcesJar").forEach { name ->
    tasks.matching { it.name == name }.configureEach { enabled = false }
}
