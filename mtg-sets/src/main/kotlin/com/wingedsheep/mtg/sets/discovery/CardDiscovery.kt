package com.wingedsheep.mtg.sets.discovery

import com.wingedsheep.sdk.model.CardDefinition
import io.github.classgraph.ClassGraph
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Auto-discovers `CardDefinition` values declared as top-level Kotlin `val`s in a given package,
 * so a set's `cards` and `basicLands` lists do not have to be maintained by hand.
 *
 * Each card file in `definitions/{set}/cards/` declares one `val Foo = card("Foo") { ... }`
 * (or `val FooPlains1 = basicLand("Plains") { ... }` for basics). Those compile to static
 * fields on a synthetic `*Kt` class per file; we scan the package, read those fields
 * reflectively, and split them into:
 *
 *   - [findIn] — non-basic-land cards, suitable for `MtgSet.cards`.
 *   - [findBasicLandsIn] — basic-land variants, suitable for `MtgSet.basicLands`.
 *
 * The split is by `typeLine.isBasicLand`. The aggregate `val FooBasicLands = listOf(...)`
 * is skipped automatically because its field type is `List<CardDefinition>`, not
 * `CardDefinition`.
 *
 * `setCode` is **not** stamped here. Today, `cards` are stamped centrally at registry-load
 * time (`GameBeansConfig.stamp(...)`), and `basicLands` are stamped by each set via
 * `.copy(setCode = code)`. Discovery preserves both behaviors — call sites stamp as they
 * do today.
 */
object CardDiscovery {

    private data class ScannedPackage(
        val cards: List<CardDefinition>,
        val basicLands: List<CardDefinition>,
    )

    private val cache = ConcurrentHashMap<String, ScannedPackage>()

    fun findIn(packageName: String): List<CardDefinition> =
        cache.getOrPut(packageName) { scan(packageName) }.cards

    fun findBasicLandsIn(packageName: String): List<CardDefinition> =
        cache.getOrPut(packageName) { scan(packageName) }.basicLands

    private fun scan(packageName: String): ScannedPackage {
        val cards = mutableListOf<CardDefinition>()
        val basicLands = mutableListOf<CardDefinition>()
        ClassGraph()
            .acceptPackages(packageName)
            .enableClassInfo()
            .scan()
            .use { scanResult ->
                for (classInfo in scanResult.allClasses) {
                    if (!classInfo.name.endsWith("Kt")) continue
                    val cls = runCatching { classInfo.loadClass() }.getOrNull() ?: continue
                    val publicGetterFieldNames = cls.declaredMethods
                        .asSequence()
                        .filter { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) }
                        .filter { it.parameterCount == 0 && it.returnType == CardDefinition::class.java }
                        .filter { it.name.startsWith("get") && it.name.length > 3 }
                        .map { it.name.substring(3).replaceFirstChar { c -> c.lowercase() } }
                        .toSet()
                    for (field in cls.declaredFields) {
                        if (!Modifier.isStatic(field.modifiers)) continue
                        if (field.type != CardDefinition::class.java) continue
                        if (field.name.replaceFirstChar { it.lowercase() } !in publicGetterFieldNames) continue
                        field.isAccessible = true
                        val value = field.get(null) as? CardDefinition ?: continue
                        if (value.typeLine.isBasicLand) basicLands += value else cards += value
                    }
                }
            }
        return ScannedPackage(
            cards = cards.sortedBy { it.name },
            basicLands = basicLands.sortedBy { it.name },
        )
    }
}
