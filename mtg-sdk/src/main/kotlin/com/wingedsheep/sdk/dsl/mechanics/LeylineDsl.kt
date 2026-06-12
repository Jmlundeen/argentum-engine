package com.wingedsheep.sdk.dsl

/**
 * Leyline mechanic (Future Sight / M11 / M20 / Duskmourn cycles). "If this card is in
 * your opening hand, you may begin the game with it on the battlefield." The engine
 * resolves all Leyline choices after mulligans and bottoming complete, walking each
 * player in turn order starting with the active player.
 *
 * Display-only at the SDK level; the engine reads [com.wingedsheep.sdk.model.CardScript.mayStartOnBattlefield]
 * to drive the per-card yes/no prompt during the leyline phase.
 *
 * A set-mechanic [CardBuilder] extension (SDK plan §2.2): it composes onto the builder via
 * the public `mayStartOnBattlefield` flag and lives here rather than on the core builder.
 */
fun CardBuilder.leyline() {
    mayStartOnBattlefield = true
}
