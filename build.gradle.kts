import org.slf4j.event.Level

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.neoforge)
    `maven-publish`
    idea
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

object Mod {
    val id = "jamesons_better_harvest"
    val group = "io.jmsn.mc"
    val name = "Jameson's Better Harvest"
    val version = "0.1.0-mc.1.21.10"
    val license = "All Rights Reserved"
    val authors = "ImJamesB"
    val description =
        "Better Harvest is a Minecraft mod that improves the farming experience by adding new features and mechanics to make harvesting crops more efficient and enjoyable."
}

version = Mod.version
group = Mod.group

kotlin.jvmToolchain(21)

repositories {
    mavenLocal()

    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content {
            includeGroup("thedarkcolour")
        }
    }

    mavenCentral()
}

dependencies {
    implementation(libs.kotlinforforge)
}

base {
    archivesName.set(Mod.id)
}

val generateModMetadata =
    tasks.register<ProcessResources>("generateModMetadata") {
        val properties =
            mapOf(
                "modId" to Mod.id,
                "modName" to Mod.name,
                "modDescription" to Mod.description,
                "modAuthors" to Mod.authors,
                "modLicense" to Mod.license,
                "modVersion" to Mod.version,
                "modGroupId" to Mod.group,
                "minecraftVersion" to
                    libs.versions.minecraft
                        .asProvider()
                        .get(),
                "minecraftVersionRange" to
                    libs.versions.minecraft.range
                        .get(),
                "neoVersion" to
                    libs.versions.neoforge
                        .asProvider()
                        .get(),
                "neoVersionRange" to
                    libs.versions.neoforge.range
                        .get(),
                "loaderVersionRange" to
                    libs.versions.loader.range
                        .get(),
                "parchmentMinecraftVersion" to
                    libs.versions.parchment.minecraft
                        .get(),
                "parchmentMappingsVersion" to
                    libs.versions.parchment.mappings
                        .get(),
            )
        inputs.properties(properties)
        expand(properties)
        from("src/main/templates")
        into("build/generated/sources/modMetadata")
    }

sourceSets.main {
    resources {
        srcDir("src/generated/resources")
        srcDir(generateModMetadata)
    }
}

neoForge.ideSyncTask(generateModMetadata)

neoForge {
    version =
        libs.versions.neoforge
            .asProvider()
            .get()

    parchment {
        mappingsVersion =
            libs.versions.parchment.mappings
                .get()

        minecraftVersion =
            libs.versions.parchment.minecraft
                .get()
    }

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", Mod.id)
        }

        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", Mod.id)
        }

        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", Mod.id)
        }

        create("data") {
            clientData()

            programArguments.addAll(
                "--mod",
                Mod.id,
                "--all",
                "--output",
                file("src/generated/resources/").absolutePath,
                "--existing",
                file("src/main/resources/").absolutePath,
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = Level.DEBUG
        }
    }

    mods {
        create(Mod.id) {
            sourceSet(sourceSets.main.get())
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("projectRepo") {
            from(components["kotlin"])
        }

        repositories {
            maven {
                name = "ProjectRepos"
                url = uri("file://$projectDir/repo")
            }
        }
    }
}
