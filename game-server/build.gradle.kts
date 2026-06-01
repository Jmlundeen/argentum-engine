plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":rules-engine"))
    implementation(project(":mtg-sdk"))
    implementation(project(":mtg-sets"))
    implementation(project(":mtg-search"))
    implementation(project(":ai"))
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.springBootWeb)
    implementation(libs.springBootStarterDataRedis)
    implementation(libs.springdocOpenapi)
    implementation(kotlin("reflect"))

    // Engine scenario harness (ScenarioTestBase, GameTestDriver, TestCards) — the canonical
    // home for the static-board scenario builder; game-server's ScenarioTestBase is a shim over it.
    testImplementation(testFixtures(project(":rules-engine")))
    testImplementation(libs.springBootStarterTest)
    testImplementation(libs.kotestRunner)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestExtensionsSpring)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.mockk)
}
