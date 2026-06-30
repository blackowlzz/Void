import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import versioning.BuildConfig

plugins {
    id("com.gradleup.shadow")
}

tasks.named<ShadowJar>("shadowJar") {
    minimize {
        exclude(dependency("net.kyori:adventure-text-serializer-gson:.*"))
    }
    archiveFileName = "${rootProject.name}-${project.name}-${rootProject.version}.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    if (BuildConfig.relocate) {
        if (BuildConfig.shadePE) {
            relocate("io.github.retrooper.packetevents", "ac.voidac.shaded.io.github.retrooper.packetevents")
            relocate("com.github.retrooper.packetevents", "ac.voidac.shaded.com.github.retrooper.packetevents")
            relocate("net.kyori", "ac.voidac.shaded.kyori") // use PE's built-in adventure instead when not shading PE
        }
        relocate("club.minnced", "ac.voidac.shaded.discord-webhooks")
        relocate("org.slf4j", "ac.voidac.shaded.slf4j") // Required by discord-webhooks
        relocate("github.scarsz.configuralize", "ac.voidac.shaded.configuralize")
        relocate("com.github.puregero", "ac.voidac.shaded.com.github.puregero")
        relocate("com.google.code.gson", "ac.voidac.shaded.gson")
        relocate("alexh", "ac.voidac.shaded.maps")
        relocate("it.unimi.dsi.fastutil", "ac.voidac.shaded.fastutil")
        relocate("okhttp3", "ac.voidac.shaded.okhttp3")
        relocate("okio", "ac.voidac.shaded.okio")
        relocate("org.yaml.snakeyaml", "ac.voidac.shaded.snakeyaml")
        relocate("org.json", "ac.voidac.shaded.json")
        relocate("org.intellij", "ac.voidac.shaded.intellij")
        relocate("org.jetbrains", "ac.voidac.shaded.jetbrains")
        relocate("org.incendo", "ac.voidac.shaded.incendo")
        relocate("io.leangen.geantyref", "ac.voidac.shaded.geantyref") // Required by cloud
        relocate("com.zaxxer", "ac.voidac.shaded.zaxxer") // Database history
        relocate("org.bstats", "ac.voidac.shaded.bstats")
    }
    mergeServiceFiles()
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}
