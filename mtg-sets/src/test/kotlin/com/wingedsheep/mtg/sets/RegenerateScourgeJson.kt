package com.wingedsheep.mtg.sets

import com.wingedsheep.mtg.sets.definitions.scg.ScourgeSet
import com.wingedsheep.sdk.serialization.CardExporter
import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import java.nio.file.Files
import java.nio.file.Path

/**
 * Utility to regenerate Scourge JSON test resource files from Kotlin definitions.
 *
 * Run manually when card definitions or serialization format changes:
 * ```
 * ./gradlew :mtg-sets:test --tests "com.wingedsheep.mtg.sets.RegenerateScourgeJson"
 * ```
 */
@Ignored
class RegenerateScourgeJson : FunSpec({
    test("regenerate scourge json files") {
        val outputDir = Path.of("src/test/resources/cards/scourge/")
        val existing = Files.list(outputDir).use { stream ->
            stream.map { it.fileName.toString() }.toList().toSet()
        }
        fun fileNameFor(name: String) = name
            .lowercase()
            .replace("'", "")
            .replace(",", "")
            .replace(" ", "-")
            .replace("--", "-") + ".json"
        val cardsToExport = ScourgeSet.cards.filter { card ->
            fileNameFor(card.name) in existing
        }
        CardExporter.exportSet(cardsToExport, outputDir)
        println("Regenerated ${cardsToExport.size} Scourge card JSON files to $outputDir")
    }
})
