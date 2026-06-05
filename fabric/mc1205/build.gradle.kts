dependencies {
    minecraft("com.mojang:minecraft:1.20.5")
    mappings(loom.officialMojangMappings())
    compileOnly(project(":fabric:mc1161"))
    compileOnly(project(":fabric:mc1171"))
    compileOnly(project(":fabric:mc1194"))

    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", "0.97.8+1.20.5"))
    modCompileOnly("me.lucko:fabric-permissions-api:0.3.1")
}
