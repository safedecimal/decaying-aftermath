# Typed bleeding (0.4.33)

Decaying Aftermath tracks capillary, venous, and arterial **external** wound severity separately on each part. A part can have more than one active type. Internal bleeding remains a separate concealed flow, not a fourth vessel type. See [Medical News Today](https://www.medicalnewstoday.com/articles/types-of-bleeding) for the vessel-based classification and [MedlinePlus](https://medlineplus.gov/ency/article/000045.htm) for the internal/external distinction. The thresholds, rates, and treatments here are game mechanics, not a medical model.

## Casualties: Unknown reference

The local Steam build 26666273 (depot 4576491, manifest 5370764972064754355) was inspected offline using dnSpy.Console, without launching the game. `Assembly-CSharp.dll` SHA-256: `48c7f2296fc4a659faae3b1822884ca5b7ff73d6767ef159e6a6c2bc0b2d38f1`.

- `Limb.totalBleedAmount` exposes one bleed value, or zero when blocked. `Limb.Update` deducts blood based on active flow and advances clotting and the bandage buffer over time.
- `WoundView.BleedImageFromLimb` chooses four intensity sprites at 3.75, 8, and 20 points, switches to covered sprites when bandage care is above 0.5, and tints the result by severity.
- The inspected CU code does not distinguish named vessel types. The mod retains that visual convention for intensity and adds original type markers and separate game-calibrated flow and clotting rates.

## Compatibility

Version-4 player saves record all three active and tourniquet-suppressed severities. Older aggregate values migrate to the baseline venous profile, which preserves the old flow and clotting rates without guessing from severity. The owner medical packet is now `body_v9`; update both the client and the server. The separate cosmetic packet still carries only aggregate external severity.
