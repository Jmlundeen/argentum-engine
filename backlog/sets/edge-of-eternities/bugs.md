- Rust Harvester, cannot choose from graveyard, card gets picked automatically
- Auxiliary Boosters - equipment not auto-attached
- Larval Scoutlander - Lands did not come to the battlefield, deck was not shuffled

Not bugs:
- Chrome Companion: should keep the bottom of the library visible.
- Intrepid Tenderfoot, stacks pumps, but it's sorcery.

Fixed:
- Artifact Equipment attacks, gives separate damage
  - Atomic Microsizer 3/3 attached to Chrome Companion - Tezzeret, Cruel Captain
  - Equipment that becomes a creature now unattaches (CR 301.5c / 704.5n), so it no longer
    both buffs its host AND attacks. Covered by EquipmentAsCreatureUnattachTest (combat case).
- Wedgelight Rammer did not turn to creature
- Spacecraft can attack 1 turn after being played
- Glacier Godmaw - Landfall not working
