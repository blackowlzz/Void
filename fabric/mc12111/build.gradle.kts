dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    compileOnly(project(":fabric:mc1161"))
    compileOnly(project(":fabric:mc1171"))
    compileOnly(project(":fabric:mc1194"))
    compileOnly(project(":fabric:mc1205"))

    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", "0.141.1+1.21.11"))
    modCompileOnly("me.lucko:fabric-permissions-api:0.6.1")
}


tasks.compileJava {
    options.release.set(21)
}
