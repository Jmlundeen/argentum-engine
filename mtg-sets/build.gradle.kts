plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":mtg-sdk"))
    implementation(libs.classgraph)
    implementation(libs.kotlinxSerialization)

    testImplementation(libs.kotestRunner)
    testImplementation(libs.kotestAssertions)
}

tasks.withType<Test> {
    systemProperty("verifyImageUris", System.getProperty("verifyImageUris") ?: "false")
    // Forward the snapshot re-bless switch into the forked test JVM (see CardDefinitionSnapshotTest).
    System.getProperty("updateSnapshots")?.let { systemProperty("updateSnapshots", it) }
}

// One-shot Scryfall sync — populates legalities.json from the live Scryfall API.
// Run with: ./gradlew :mtg-sets:syncLegality
tasks.register<JavaExec>("syncLegality") {
    description = "Fetch deck-format legality for every registered card from Scryfall."
    group = "build"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wingedsheep.mtg.sets.legality.SyncLegalitiesKt")
    workingDir = rootProject.projectDir
}

// Offline sync from a Scryfall bulk-data dump. Pass the dump path via --args.
// Run with: ./gradlew :mtg-sets:syncLegalityFromDump --args="/path/to/all-cards.json"
tasks.register<JavaExec>("syncLegalityFromDump") {
    description = "Populate legalities.json from a local Scryfall bulk-data dump."
    group = "build"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wingedsheep.mtg.sets.legality.SyncLegalitiesFromDumpKt")
    workingDir = rootProject.projectDir
}

// Offline sync of color identities. Walks every card definition .kt file under
// mtg-sets/.../definitions/<set>/cards/ and adds or updates `colorIdentity = "..."` from a
// Scryfall bulk-data dump.
// Run with: ./gradlew :mtg-sets:syncColorIdentityFromDump --args="/path/to/all-cards.json"
tasks.register<JavaExec>("syncColorIdentityFromDump") {
    description = "Patch every card .kt file with its Scryfall color identity."
    group = "build"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wingedsheep.mtg.sets.colors.SyncColorIdentityFromDumpKt")
    workingDir = rootProject.projectDir
}
