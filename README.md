# Abjection

Abjection (formerly *Decaying Aftermath*) is a standalone **Minecraft 26.3 / Fabric** mod written for **Java 25**. It adds a deep medical, physiological, and psychological survival layer: localized body-part injuries, typed bleeding, interactive treatment procedures, vitals, defibrillators, depression/mood dynamics, and a post-mortem aftermath.

[Русская версия](README_RU.md)

## Features

- **Localized body model**: separate head, torso, arms, and legs with tissue integrity, fractures, burns, cold injuries, embedded foreign objects, and typed external/internal bleeding (capillary, venous, arterial).
- **Interactive treatment procedures**: bandaging, pressure dressings, splints, tourniquets, antiseptic sprays, local anesthetics, tweezers extraction, chest drains, blood/saline bags, syringes, and AED / manual defibrillators.
- **Vitals & physiology**: blood volume, oxygenation, red cells/plasma, pain, consciousness, energy, cardiac rhythm/arrest, hydration, nutrition, body temperature, encumbrance, and infection progression.
- **Mental health & aftermath**: mood tracking, multiple depression types and treatments, and a locked camera / sensory aftermath sequence upon death.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API for Minecraft 26.3
- Java 25

The client and server should use the same mod version.

## Build

On Windows:

```powershell
.\gradlew.bat build
```

On Linux/macOS:

```sh
./gradlew build
```

The built JAR is written to `build/libs/`. Run the test suite with:

```sh
./gradlew test
```

Use `gradlew.bat test` on Windows.

## Contributing

1. Fork the repository and create a focused branch.
2. Make the smallest change that solves the issue.
3. Run `build` and the relevant tests locally.
4. Open a pull request with a short description of the change and how it was tested.

Please do not commit generated files, build output, or private assets.

## License and assets

The source code is licensed under the [MIT License](LICENSE).

Some textures, icons, sounds, and other materials come from **Casualties Unknown** and remain the property of **Orsoniks**. Those assets are not covered by the MIT License.

## Push and releases

Push changes to the main branch with:

```sh
git add README.md README_RU.md VERSION
git commit -m "Refresh project README"
git push origin main
```

The release workflow runs when `VERSION` changes. It builds the mod and publishes the JAR to GitHub Releases. Use a new unused semantic version in `VERSION` for each release.

## CU defibrillators (0.4.22)

The old symbol puzzle and three rhythm locks are removed. Original CU electrode dragging, dial, charge/shock buttons, art, numeric font and audio are ported. LMB interacts with the inertial hand; RMB/Esc exits. Update both client and server. See [port notes](docs/CU_DEFIBRILLATORS_0.4.22.md) for verified source bindings and explicit adaptations.
