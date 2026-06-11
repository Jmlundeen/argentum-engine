package com.wingedsheep.mtg.sets

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.io.path.name
import kotlin.io.path.readText

class MtgSetCatalogTest : FunSpec({

    // Guards auto-discovery: every `*Set.kt` on disk must surface in MtgSetCatalog.all (built by
    // CardDiscovery.findSets). A set declared as something other than an `object`, or otherwise
    // missed by the classpath scan, would silently vanish from the catalog — this catches it.
    test("every <Name>Set.kt under definitions is discovered into MtgSetCatalog.all") {
        val definitionsDir = Paths.get(
            "src/main/kotlin/com/wingedsheep/mtg/sets/definitions"
        ).toAbsolutePath()

        val setFilesInTree = Files.walk(definitionsDir).use { stream ->
            stream
                .filter { it.name.endsWith("Set.kt") }
                .filter { it.readText().contains(": MtgSet") }
                .map { it.name.removeSuffix(".kt") }
                .toList()
                .sorted()
        }

        val registeredNames = MtgSetCatalog.all
            .map { it::class.simpleName!! }
            .sorted()

        setFilesInTree.shouldContainExactlyInAnyOrder(registeredNames)
    }

    test("set codes are unique") {
        val duplicates = MtgSetCatalog.all
            .groupBy { it.code }
            .filterValues { it.size > 1 }
            .keys
        duplicates.shouldBeEmpty()
    }

    // The same hollow-set failure GameBeansConfig guards at server boot, caught here in CI: a set
    // that contributes no cards, printings, or basic lands has almost always typo'd its
    // CARDS_PACKAGE. (All-reprint sets like Eighth Edition have no own cards but do carry printings.)
    test("no registered set is hollow") {
        assertSoftly {
            for (set in MtgSetCatalog.all) {
                withClue("${set.code} (${set.displayName})") {
                    val hasContent = set.cards.isNotEmpty() ||
                        set.printings.isNotEmpty() ||
                        set.basicLands.isNotEmpty()
                    hasContent shouldBe true
                }
            }
        }
    }

    test("release dates, when present, are parseable ISO YYYY-MM-DD") {
        assertSoftly {
            for (set in MtgSetCatalog.all) {
                val date = set.releaseDate ?: continue
                withClue("${set.code} releaseDate=$date") {
                    val parsed = runCatching {
                        LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                    }.isSuccess
                    parsed shouldBe true
                }
            }
        }
    }
})
