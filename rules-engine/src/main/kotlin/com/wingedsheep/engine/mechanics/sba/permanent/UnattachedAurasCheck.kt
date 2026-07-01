package com.wingedsheep.engine.mechanics.sba.permanent

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils.cleanupReverseAttachmentLink
import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.mechanics.sba.SbaOrder
import com.wingedsheep.engine.mechanics.sba.SbaZoneMovementHelper
import com.wingedsheep.engine.mechanics.sba.StateBasedActionCheck
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentHostLeftComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GrantProtection

/**
 * 704.5m - An Aura attached to an illegal object/player or not attached goes to graveyard.
 * 704.5n - An Equipment or Fortification attached to an illegal permanent becomes unattached
 *          but remains on the battlefield. This drives two Equipment cases below, both asked
 *          of the projected state (so layer-4 type-changing effects are seen):
 *            - The host stops being a creature (an Equipment can only equip a creature, CR
 *              301.5). E.g. the equipped creature is turned into a land, or an animated
 *              artifact's "until end of turn" animation wears off while still equipped.
 *            - The Equipment itself becomes a creature, so it can't legally equip another
 *              creature unless it has reconfigure (CR 301.5c). E.g. Atomic Microsizer turned
 *              into a 0/0 Robot artifact creature by Tezzeret, Cruel Captain's emblem.
 * 704.5p - A battle or creature attached to an object or player becomes unattached but
 *          remains on the battlefield.
 *
 * Protection (CR 702.16c/d): a permanent with protection from a quality can't be enchanted by
 * Auras (put into their owners' graveyards as a state-based action) or equipped by Equipment
 * (becomes unattached, stays on the battlefield) that have the stated quality. This covers
 * protection gained *after* the attachment landed — targeting-time protection is enforced by
 * `TargetValidator`. An attachment whose own printed ability grants that very protection to
 * its host is exempt ("This effect doesn't remove this Aura", the Ward cycle).
 */
class UnattachedAurasCheck(
    private val cardRegistry: CardRegistry
) : StateBasedActionCheck {
    override val name = "704.5m/n/p Unattached Auras"
    override val order = SbaOrder.UNATTACHED_AURAS

    override fun check(state: GameState): ExecutionResult {
        var newState = state
        val events = mutableListOf<com.wingedsheep.engine.core.GameEvent>()
        val projected = state.projectedState

        for (entityId in state.getBattlefield().toList()) {
            val container = state.getEntity(entityId) ?: continue
            val cardComponent = container.get<CardComponent>() ?: continue

            val isAura = cardComponent.typeLine.isAura
            val isEquipment = cardComponent.typeLine.isEquipment

            if (!isAura && !isEquipment) continue

            // CR 400.7 / 704.5m-n: the host this attachment was on left the battlefield. The host's
            // EntityId may have returned via a blink (a same-id but *new* object), so the id-based
            // checks below can't see the leave — the leave-time marker does. An Aura goes to the
            // graveyard; an Equipment unattaches and stays on the battlefield.
            val hostLeft = container.get<AttachmentHostLeftComponent>()
            if (hostLeft != null) {
                newState = newState.updateEntity(entityId) { c -> c.without<AttachmentHostLeftComponent>() }
                if (isAura) {
                    val result = SbaZoneMovementHelper.putPermanentInGraveyard(
                        newState, entityId, cardComponent,
                        lastKnownAttachedTo = hostLeft.lastKnownHostId
                    )
                    newState = result.newState
                    events.addAll(result.events)
                } else {
                    // CR 704.5n: an Equipment whose host left becomes unattached but stays on the
                    // battlefield. Only force the unattach when it's still pointing at the host that
                    // left (or is already unattached) — the marker exists for the blink case where the
                    // host returns under the same EntityId. If an effect has *re-attached* it to a
                    // different permanent in the meantime (e.g. Zack Fair attaches "an Equipment that
                    // was attached to it" to another creature as it's sacrificed), leave that new
                    // attachment in place; the legality checks below validate the new host instead.
                    val current = container.get<AttachedToComponent>()
                    if (current == null || current.targetId == hostLeft.lastKnownHostId) {
                        newState = cleanupReverseAttachmentLink(newState, entityId)
                        newState = newState.updateEntity(entityId) { c ->
                            c.without<AttachedToComponent>()
                        }
                    }
                }
                continue
            }

            val attachedTo = container.get<AttachedToComponent>()
            if (attachedTo == null) {
                if (isAura) {
                    // Aura not attached to anything - goes to graveyard
                    val result = SbaZoneMovementHelper.putPermanentInGraveyard(
                        newState, entityId, cardComponent
                    )
                    newState = result.newState
                    events.addAll(result.events)
                }
                // Equipment not attached to anything is fine - stays on battlefield
            } else if (isAura && attachedTo.targetId in state.turnOrder) {
                // 704.5m — an "enchant player" Aura (Grievous Wound) is attached to a player, not
                // a battlefield permanent. It stays as long as that player is still in the game;
                // once the player leaves, PlayerLeavesGameProcessor removes them from turnOrder and
                // the next check sends the now-unattached Aura to the graveyard.
                continue
            } else {
                // Check if attached target still exists on battlefield
                if (attachedTo.targetId !in state.getBattlefield()) {
                    if (isAura) {
                        // Aura's target gone - goes to graveyard
                        val result = SbaZoneMovementHelper.putPermanentInGraveyard(
                            newState, entityId, cardComponent,
                            lastKnownAttachedTo = attachedTo.targetId
                        )
                        newState = result.newState
                        events.addAll(result.events)
                    } else {
                        // Equipment's target gone - just detach, stays on battlefield
                        newState = cleanupReverseAttachmentLink(newState, entityId)
                        newState = newState.updateEntity(entityId) { c ->
                            c.without<AttachedToComponent>()
                        }
                    }
                } else if (
                    isEquipment && (
                        // CR 704.5n: the host is no longer a legal permanent for an Equipment.
                        // An Equipment can only be attached to a creature, so once the host
                        // stops being a creature (turned into a land, animation wore off, etc.)
                        // the attachment is illegal and the Equipment unattaches.
                        !projected.isCreature(attachedTo.targetId) ||
                        // CR 301.5c / 704.5n: the Equipment itself became a creature, so it
                        // can't equip a creature unless it has reconfigure.
                        (projected.isCreature(entityId) &&
                            !projected.hasKeyword(entityId, "RECONFIGURE"))
                    )
                ) {
                    // Illegal attachment: the Equipment unattaches but stays on the battlefield.
                    newState = cleanupReverseAttachmentLink(newState, entityId)
                    newState = newState.updateEntity(entityId) { c ->
                        c.without<AttachedToComponent>()
                    }
                } else if (
                    hostProtectedFromAttachmentColor(projected, entityId, cardComponent, attachedTo.targetId)
                ) {
                    // CR 702.16c/d: the host has protection from one of this attachment's colors
                    // (gained after the attachment landed — e.g. White Ward's pro-white sends an
                    // already-attached Holy Strength to the graveyard). Aura -> owner's graveyard
                    // (704.5m); Equipment -> unattaches, stays on the battlefield (704.5n).
                    if (isAura) {
                        val result = SbaZoneMovementHelper.putPermanentInGraveyard(
                            newState, entityId, cardComponent,
                            lastKnownAttachedTo = attachedTo.targetId
                        )
                        newState = result.newState
                        events.addAll(result.events)
                    } else {
                        newState = cleanupReverseAttachmentLink(newState, entityId)
                        newState = newState.updateEntity(entityId) { c ->
                            c.without<AttachedToComponent>()
                        }
                    }
                }
            }
        }

        return ExecutionResult.success(newState, events)
    }

    /**
     * True when the attached permanent's host has protection from one of the attachment's
     * (projected) colors, CR 702.16c/d. An attachment whose own printed [GrantProtection]
     * grants that color's protection is exempt — the Ward cycle's "This effect doesn't remove
     * this Aura". (Approximation: the exemption is per-color rather than per-effect, so two
     * same-color Wards on one host both survive where strict rules would remove each via the
     * other's effect — an untracked-provenance corner case.)
     */
    private fun hostProtectedFromAttachmentColor(
        projected: ProjectedState,
        attachmentId: EntityId,
        attachmentCard: CardComponent,
        hostId: EntityId
    ): Boolean {
        val colors = projected.getColors(attachmentId)
        if (colors.isEmpty()) return false
        val selfGrantedColors: Set<Color> = cardRegistry.getCard(attachmentCard.cardDefinitionId)
            ?.staticAbilities
            ?.filterIsInstance<GrantProtection>()
            ?.map { it.color }
            ?.toSet()
            ?: emptySet()
        return Color.entries.any { color ->
            color.name in colors &&
                color !in selfGrantedColors &&
                projected.hasKeyword(hostId, "PROTECTION_FROM_${color.name}")
        }
    }
}
