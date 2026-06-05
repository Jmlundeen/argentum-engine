#!/usr/bin/env python3
"""
Auto-generation gap detector + draft generator, on top of the mtgish bridge.

Modes:
  --gaps SET     Of a set's UNIMPLEMENTED cards, predict which the emitter could auto-author
                 today: AUTOGEN (covered + the emitter renders the whole card), SCAFFOLD
                 (covered but structure not fully recovered), BLOCKED (capability gap), with a
                 leaderboard of what blocks the rest.
  --write SET    Emit a draft `.kt` for every AUTOGEN missing card into a STAGING dir
                 (default spike/mtgish-coverage/generated/<set>/) for human review.
  --emit-all SET Emit a `.kt` for every card of the set (implemented included) the emitter can
                 render WHOLE, into a generated package, for the Kotlin compile-verification gate
                 (`./gradlew :mtg-sets:verifyGeneratedCards`). This is how AUTO is proven: the
                 output must compile and serialize to the same capabilities as the golden tree.

WHY DRAFTS STAY IN STAGING. These are predictions from approximate mtgish IR; the emitter
guarantees a card that compiles with the right capabilities, NOT behavioural correctness (a filter
or count can be subtly wrong). Ground truth stays a human-authored card whose scenario test passes.
So drafts must (1) compile, (2) get a scenario test, (3) be reviewed — then move into the set's
`cards/` package (auto-registered via classpath scan). Deliberately NOT a card loader.
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

sys.dont_write_bytecode = True
SPIKE_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SPIKE_DIR))
import probe      # noqa: E402
import emitter    # noqa: E402


def gen_package(set_code: str) -> str:
    return f"com.wingedsheep.mtg.sets.generated.{set_code.lower()}.cards"


def draft_package(set_code: str) -> str:
    return f"com.wingedsheep.mtg.sets.definitions.{set_code.lower()}.cards"


def render(card, set_code, effects, keywords, mapping, package):
    scryfall = probe.scryfall_card(set_code, card["Name"])
    return emitter.render_card(card, scryfall, effects, keywords, mapping, package=package)


def classify(card, set_code, effects, keywords, mapping):
    coverable, _reqs, _bl = probe.analyze(card, effects, keywords, mapping)
    if not coverable:
        return "BLOCKED"
    return "AUTOGEN" if render(card, set_code, effects, keywords, mapping, gen_package(set_code)).complete \
        else "SCAFFOLD"


def missing_with_mtgish(set_code):
    draft, extra = probe.canonical_names(set_code)
    if draft is None:
        sys.exit(f"no Scryfall data for {set_code.upper()} — run: just card-status --set {set_code.upper()}")
    impl = probe.implemented_names(set_code)
    missing = sorted((draft | extra) - impl)
    return missing, probe.load_mtgish_index(set(missing))


def all_with_mtgish(set_code):
    draft, extra = probe.canonical_names(set_code)
    if draft is None:
        sys.exit(f"no Scryfall data for {set_code.upper()} — run: just card-status --set {set_code.upper()}")
    names = sorted(draft | extra)
    return names, probe.load_mtgish_index(set(names))


def classify_missing(set_code, effects, keywords, mapping):
    """Bucket a set's unimplemented cards into AUTOGEN/SCAFFOLD/BLOCKED/UNMATCHED."""
    missing, idx = missing_with_mtgish(set_code)
    cats = {"AUTOGEN": [], "SCAFFOLD": [], "BLOCKED": [], "UNMATCHED": []}
    block_tax = Counter()
    for name in missing:
        card = idx.get(name)
        if card is None:
            cats["UNMATCHED"].append(name)
            continue
        cat = classify(card, set_code, effects, keywords, mapping)
        cats[cat].append(name)
        if cat == "BLOCKED":
            for _d, v, _verdict in probe.analyze(card, effects, keywords, mapping)[2]:
                block_tax[v] += 1
    return missing, cats, block_tax


def mode_gaps(set_code, effects, keywords, mapping, list_cat):
    missing, cats, block_tax = classify_missing(set_code, effects, keywords, mapping)

    n = len(missing)
    print(f"== {set_code.upper()} auto-generation gap — {n} unimplemented cards ==\n")
    print(f"  AUTOGEN   {len(cats['AUTOGEN']):>4}   emitter renders a whole compiling card now")
    print(f"  SCAFFOLD  {len(cats['SCAFFOLD']):>4}   covered, but structure needs hand-wiring")
    print(f"  BLOCKED   {len(cats['BLOCKED']):>4}   capability gap (needs mapping or engine work)")
    if cats["UNMATCHED"]:
        print(f"  (unmatched in mtgish: {len(cats['UNMATCHED'])} — name join / Un-set / too new)")
    print(f"\n  -> `just coverage-generate --set {set_code.upper()}` drafts the {len(cats['AUTOGEN'])} "
          f"AUTOGEN cards into a staging dir.")
    if block_tax:
        print("\nBLOCKED leaderboard — capability ranked by # cards it would unlock:")
        for cap, c in block_tax.most_common(15):
            print(f"  x{c:<4} {cap}")
    if list_cat:
        names = cats.get(list_cat.upper(), [])
        print(f"\n{list_cat.upper()} ({len(names)}):")
        for nm in names:
            print(f"  - {nm}")
    return 0


def mode_write(set_code, effects, keywords, mapping, outdir):
    missing, idx = missing_with_mtgish(set_code)
    out = Path(outdir) if outdir else SPIKE_DIR / "generated" / set_code.lower()
    out.mkdir(parents=True, exist_ok=True)
    written = 0
    for name in missing:
        card = idx.get(name)
        if card is None or not probe.analyze(card, effects, keywords, mapping)[0]:
            continue
        res = render(card, set_code, effects, keywords, mapping, draft_package(set_code))
        if not res.complete:
            continue
        (out / (re.sub(r"[^A-Za-z0-9]", "", name) + ".kt")).write_text(res.text)
        written += 1
    print(f"wrote {written} draft card(s) to {out}")
    print("These are DRAFTS — compile, add a scenario test, and review before moving into the set.")
    return 0


def mode_emit_all(set_code, effects, keywords, mapping, outdir):
    """Emit every card the emitter can render WHOLE into a generated source dir, for the gate."""
    names, idx = all_with_mtgish(set_code)
    out = Path(outdir) if outdir else SPIKE_DIR / "generated" / set_code.lower()
    if out.exists():  # fresh dir so stale drafts never linger on the gate's classpath
        for f in out.glob("*.kt"):
            f.unlink()
    out.mkdir(parents=True, exist_ok=True)
    written = 0
    for name in names:
        card = idx.get(name)
        if card is None:
            continue
        res = render(card, set_code, effects, keywords, mapping, gen_package(set_code))
        if not res.complete:
            continue
        (out / (re.sub(r"[^A-Za-z0-9]", "", name) + ".kt")).write_text(res.text)
        written += 1
    print(f"emit-all: wrote {written}/{len(names)} whole-card drafts to {out} "
          f"(package {gen_package(set_code)})")
    return 0


def mode_gaps_all(effects, keywords, mapping, list_cat, unique):
    """Run the auto-gen gap over every booster set Scryfall knows, summing the AUTOGEN total."""
    codes = probe.all_set_codes()
    print(f"== auto-generation gap across {len(codes)} Scryfall booster sets ==", file=sys.stderr)
    print("   (corpus is name-keyed oracle IR — every set is reasoned over, not just a sample)\n",
          file=sys.stderr)
    rows, totals = [], Counter()
    autogen_union: set[str] = set()  # front-faced AUTOGEN names, deduped across sets
    for i, code in enumerate(codes, 1):
        print(f"  [{i:>3}/{len(codes)}] {code} ...", end="\r", file=sys.stderr, flush=True)
        if probe.canonical_names(code)[0] is None:
            continue  # no Scryfall cache and fetch failed — skip rather than abort the sweep
        missing, cats, _ = classify_missing(code, effects, keywords, mapping)
        if not missing:
            continue
        autogen_union.update(probe.front(n) for n in cats["AUTOGEN"])
        row = (code, len(missing), len(cats["AUTOGEN"]), len(cats["SCAFFOLD"]),
               len(cats["BLOCKED"]), len(cats["UNMATCHED"]))
        rows.append(row)
        for k in ("AUTOGEN", "SCAFFOLD", "BLOCKED", "UNMATCHED"):
            totals[k] += len(cats[k])
        totals["MISSING"] += len(missing)
        if list_cat:
            names = cats.get(list_cat.upper(), [])
            if names:
                print(f"\n{code} {list_cat.upper()} ({len(names)}):", file=sys.stderr)
                for nm in names:
                    print(f"  - {nm}", file=sys.stderr)
    print(" " * 40, end="\r", file=sys.stderr)  # clear the progress line

    rows.sort(key=lambda r: (-r[2], -r[1]))  # most AUTOGEN first, then biggest backlog
    print(f"  {'SET':<5} {'missing':>7} {'AUTOGEN':>7} {'SCAFFOLD':>8} {'BLOCKED':>7} {'UNMATCHED':>9}")
    print("  " + "-" * 54)
    for code, miss, auto, scaf, block, unm in rows:
        print(f"  {code:<5} {miss:>7} {auto:>7} {scaf:>8} {block:>7} {unm:>9}")
    print("  " + "-" * 54)
    print(f"  {'TOTAL':<5} {totals['MISSING']:>7} {totals['AUTOGEN']:>7} "
          f"{totals['SCAFFOLD']:>8} {totals['BLOCKED']:>7} {totals['UNMATCHED']:>9}")
    print(f"\n  {totals['AUTOGEN']} unimplemented cards across {len(rows)} sets would auto-author "
          f"a whole compiling card today.")
    print("  (per-set count — a reprint counts once per set, and once per already-authored printing)")

    if unique:
        distinct = len(autogen_union)
        net_new = sorted(autogen_union - probe.all_implemented_names())
        print("\n  NET-NEW (deduped across sets, minus everything already implemented anywhere):")
        print(f"    {distinct:>6}  distinct AUTOGEN card names (cross-set duplicates collapsed)")
        print(f"    {len(net_new):>6}  genuinely unimplemented — the real auto-authorable backlog")
        print(f"\n  NET-NEW cards ({len(net_new)}):")
        for nm in net_new:
            print(f"    - {nm}")
    else:
        print("  (--unique collapses cross-set reprints + already-implemented cards into a "
              "net-new count)")
    print("\n  Per set: `just coverage-generate --set <CODE>` drafts its AUTOGEN cards into staging.")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    target = ap.add_mutually_exclusive_group(required=True)
    target.add_argument("--set", metavar="CODE", help="one set code")
    target.add_argument("--all", action="store_true",
                        help="with --gaps: sweep every Scryfall booster set, sum the AUTOGEN total")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--gaps", action="store_true", help="report the auto-gen gap (default)")
    g.add_argument("--write", action="store_true", help="write draft .kt files for AUTOGEN cards")
    g.add_argument("--emit-all", action="store_true", dest="emit_all",
                   help="emit every whole-renderable card (impl included) for the Kotlin gate")
    ap.add_argument("--list", metavar="CAT", help="with --gaps: list AUTOGEN/SCAFFOLD/BLOCKED")
    ap.add_argument("--unique", action="store_true",
                    help="with --all: also report net-new cards (deduped, minus all implementations)")
    ap.add_argument("--out", metavar="DIR", help="output dir (--write / --emit-all)")
    args = ap.parse_args()
    effects, keywords, mapping = (probe.load_effect_serialnames(), probe.load_keywords(),
                                  probe.load_mapping())
    if args.unique and not args.all:
        ap.error("--unique only applies to --all")
    if args.all:
        if args.write or args.emit_all:
            ap.error("--all is only supported with --gaps")
        return mode_gaps_all(effects, keywords, mapping, args.list, args.unique)
    if args.write:
        return mode_write(args.set, effects, keywords, mapping, args.out)
    if args.emit_all:
        return mode_emit_all(args.set, effects, keywords, mapping, args.out)
    return mode_gaps(args.set, effects, keywords, mapping, args.list)


if __name__ == "__main__":
    sys.exit(main())
