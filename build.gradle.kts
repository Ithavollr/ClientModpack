// ./gradlew buildPrismPack -> build/Iffy.zip (Prism instance: instance.cfg, mmc-pack.json, unsup, servers.dat)
// icon.png in this directory, if present, becomes the instance icon.

val packToml = layout.projectDirectory.file("pack.toml").asFile.readText()
fun packValue(key: String): String =
    Regex("""^\s*$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(packToml)?.groupValues?.get(1)
        ?: error("pack.toml: no '$key' entry")

val mcVersion = packValue("minecraft")
val fabricVersion = packValue("fabric")
version = packValue("version")

val instanceName: String by project
val minMemMiB: String by project
val maxMemMiB: String by project
val serverName: String by project
val serverAddress: String by project

val unsupIni = layout.projectDirectory.file("unsup.ini").asFile
val unsupComponent = layout.projectDirectory.file("com.unascribed.unsup.json").asFile
val icon = layout.projectDirectory.file("icon.png").asFile
val prismDir = layout.buildDirectory.dir("prism/$instanceName")

// Uncompressed NBT: {servers: [{name, ip, hidden}]}
// wozniak: one server, no icon (client caches it after first ping)
fun writeServersDat(target: File) {
    java.io.DataOutputStream(target.outputStream().buffered()).use { out ->
        out.writeByte(10); out.writeUTF("")                        // TAG_Compound root
        out.writeByte(9); out.writeUTF("servers")                  // TAG_List "servers"
        out.writeByte(10); out.writeInt(1)                         //   of 1 TAG_Compound
        out.writeByte(8); out.writeUTF("name"); out.writeUTF(serverName)
        out.writeByte(8); out.writeUTF("ip"); out.writeUTF(serverAddress)
        out.writeByte(1); out.writeUTF("hidden"); out.writeByte(0)
        out.writeByte(0)                                           // TAG_End server
        out.writeByte(0)                                           // TAG_End root
    }
}

val generatePrismInstance by tasks.registering {
    description = "Writes instance.cfg, mmc-pack.json and the unsup files under build/prism"
    inputs.files(unsupIni, unsupComponent)
    inputs.properties(
        "mc" to mcVersion, "fabric" to fabricVersion,
        "instanceName" to instanceName, "minMemMiB" to minMemMiB, "maxMemMiB" to maxMemMiB,
        "serverName" to serverName, "serverAddress" to serverAddress,
        "hasIcon" to icon.exists(),
    )
    outputs.dir(prismDir)
    doLast {
        val dir = prismDir.get().asFile
        dir.mkdirs()
        val iconKey = if (icon.exists()) "icon" else "default"
        dir.resolve("instance.cfg").writeText(
            """
            [General]
            ConfigVersion=1.3
            InstanceType=OneSix
            name=$instanceName
            iconKey=$iconKey
            OverrideMemory=true
            MinMemAlloc=$minMemMiB
            MaxMemAlloc=$maxMemMiB
            """.trimIndent() + "\n"
        )
        dir.resolve("mmc-pack.json").writeText(
            """
            {
              "formatVersion": 1,
              "components": [
                { "uid": "net.minecraft", "version": "$mcVersion", "important": true },
                { "uid": "net.fabricmc.intermediary", "version": "$mcVersion", "dependencyOnly": true },
                { "uid": "net.fabricmc.fabric-loader", "version": "$fabricVersion" },
                { "uid": "com.unascribed.unsup" }
              ]
            }
            """.trimIndent() + "\n"
        )
        unsupComponent.copyTo(dir.resolve("patches/com.unascribed.unsup.json"), overwrite = true)
        unsupIni.copyTo(dir.resolve(".minecraft/unsup.ini"), overwrite = true)
        writeServersDat(dir.resolve(".minecraft/servers.dat"))
        if (icon.exists()) icon.copyTo(dir.resolve("icon.png"), overwrite = true)
    }
}

tasks.register<Zip>("buildPrismPack") {
    description = "Builds the importable Prism Launcher instance zip"
    group = "build"
    dependsOn(generatePrismInstance)
    from(layout.buildDirectory.dir("prism"))
    archiveFileName.set("Iffy.zip")
    destinationDirectory.set(layout.buildDirectory)
}
