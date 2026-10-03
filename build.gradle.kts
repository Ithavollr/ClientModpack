// Builds an importable Prism Launcher instance zip from pack.toml + the unsup files.
// Nothing here runs Minecraft or Prism: the zip is five small files, and unsup fetches
// the whole pack on first launch. No logs, no mods, nothing to clean.
//
//   ./gradlew buildPrismPack  ->  build/Iffy.zip
//
// Optional: an icon.png in this directory becomes the instance icon.

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

val unsupIni = layout.projectDirectory.file("unsup.ini").asFile
val unsupComponent = layout.projectDirectory.file("com.unascribed.unsup.json").asFile
val icon = layout.projectDirectory.file("icon.png").asFile
val prismDir = layout.buildDirectory.dir("prism/$instanceName")

val generatePrismInstance by tasks.registering {
    description = "Writes instance.cfg, mmc-pack.json and the unsup files under build/prism"
    inputs.files(unsupIni, unsupComponent)
    inputs.properties(
        "mc" to mcVersion, "fabric" to fabricVersion,
        "instanceName" to instanceName, "minMemMiB" to minMemMiB, "maxMemMiB" to maxMemMiB,
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
