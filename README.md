# Create: Factory Controller

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-factory-controller) |
[Modrinth](https://modrinth.com/mod/create-factory-controller) |
[Discord](https://discord.gg/yv77dsgeVx) |
[Manual](https://nbcss.github.io/create-factory-controller/manual/en/)

Having a large wall of factory gauges can get unwieldy. This addon introduces the "factory controller", where you can place factory gauges (and more) in a virtual dashboard, condensing the wall of gauges into a single block.

Additionally, using a factory controller allows you to:

- Quickly tune a new factory gauge to a network already tuned to by existing gauges. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/dashboard.html#network-selector)
- Select and move multiple gauges at once. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/dashboard.html#components)
- Save your setups in blueprints. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/blueprint.html)
- See the item count labels of all gauges at once.
- Disable all factory gauges in the controller with redstone. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#redstone)
- Change individual connection's arrow bending path. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/dashboard.html#connections)
- Display information with a display link. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/display-link.html)
- Import existing gauge walls into a factory controller. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/blueprint.html#import-world)

Factory gauges placed in factory controllers also receive a number of functional enhancements:

- More than 1 stack of the same ingredient.
- Up to 64 stacks of expected outputs.
- Mechanical crafting:
  - Crafting grid sizes up to 10×10. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#crafting)
  - Request ingredients in batches of up to 64. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#batch-crafting)
- Optionally ignore item NBT data. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#ignore-data)
- Custom-arrange ingredients into arbitrary patterns (for sequence assembly or vanilla crafter). [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#ingredient-arrange)
- Produce only when there is demand, minimize intermediate items in storage. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#on-demand)
- Produce according to manually placed orders from a stock keeper. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#production-orders)
- Requests multiple sets of ingredients at once if stock allows. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#request-multiplier)
- Customize request interval of individual gauges. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#request-interval)
- Limit promises created by the gauge. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#promise)
- Limit promises by shared destination address. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#promise)

You can also place these in factory controllers:

- Redstone Link [🕮](https://nbcss.github.io/create-factory-controller/manual/en/redstone-link.html)
- Electron Tube: Act as logical gates for redstone signals. [🕮](https://nbcss.github.io/create-factory-controller/manual/en/electron-tube.html)

This mod does **not**:

- Manage an external wall of factory gauges.

## In-world industrial dashboard

The controller now renders its instruments on a solid cabinet face, visible without opening a menu.
Place **Factory Dashboard Panels** edge-to-edge beside, above, or below it to enlarge the surface.
Panels inherit the neighboring cabinet's orientation; perpendicular faces and back-to-back cabinets stay separate.
One controller owns the entire assembly. Joining two controllers is rejected before an item is consumed.
The default limit is 64 blocks within an 8×8 envelope, configurable on the server.

Each block displays a 4×4 area of the logical board. Expansion never shifts existing instruments.
Removing a panel hides its instruments without deleting them or stopping their operation; replacing it reveals them again.
Irregular shapes are supported, with cables routed over existing faces rather than through gaps.
The **Display area** button changes the assembly's shared logical origin, while full-editor pan and zoom remain local.

Right-click any connected cabinet to edit in world. Click an instrument to configure it, use the inventory drawer
to attach components, Ctrl-drag to select, C to connect, Q to relocate, F to change mode, R to change bends,
and Shift-click to remove. Escape cancels a tool before closing the session. Existing configuration and blueprint
forms remain conventional screens. **Full editor** opens the original dashboard; **World editor** returns to the cabinet.
The client configuration can also make the full editor the default.

Cabinets use loaded-chunk-only discovery, persisted assembly identities, nearby read-only snapshots,
and menu/session validation for edits. Server and client must use the same dashboard protocol version.

### Dashboard rendering

Instrument bezels use a shared baked partial model and Create/Catnip's reload-aware buffer cache.
Bodies are drawn as a separate pass to avoid switching model/sprite buffers per instrument.
Widgets explicitly name `PanelSurface` layers for backplates, wires, faces, symbols, lights, items,
selection and labels; the world backend owns their physical depths and lit/emissive materials.
Placement and move previews reuse the same surfaces above the live layer stack.
New overlapping artwork must use distinct surfaces, not draw-order assumptions or local Z offsets.
The menu backend ignores world depths and keeps the existing 2D composition.

World detail is selected once per cabinet/frame from the projected size of a quarter-block instrument
at the assembly center (physical screen pixels, actual projection/FOV and grazing angle, not GUI scale).
Initial tiers are **full** at 24+ pixels, **simplified faces/status lights** at 10–24 pixels, and a
**quiet per-tile status strip** below 10 pixels. A 15% hysteresis band prevents repeated switching near
thresholds; tiers change discretely rather than blending overlapping depth-tested surfaces.
Nearby cabinets being edited retain full detail, and placement previews and the conventional editor
always remain full detail. `dashboardLod = false` in the client config disables visual simplification.
This does not change controller operation, synchronization payloads or the subscription radius.
The range gate is shared by the whole cabinet, measured from its nearest member, while chunk/frustum
culling stays tile-sized. Unsynced/unavailable boards still show their existing warning indicator.

Power-of-two instrument artwork is also stitched into the block atlas for world rendering, using
Create's mipmapped entity materials. The original GUI sprites are unchanged. Near-only 15×15 operator
icons and 16×8 strips stay on the GUI atlas so they cannot reduce the block atlas's mip level;
custom/compat artwork falls back to its existing GUI sprite. World sprites are resolved each draw
through the current atlas, not retained across resource reloads. New world atlas entries must have
dimensions divisible by 16 (covered by the atlas regression test).

### Dashboard tests

Geometry, sync, renderer-surface, projected-LoD/hysteresis and atlas regression tests: `./gradlew test`.
Dedicated-server assembly tests: `./gradlew runGameTestServer -PcleanRuntime -PdashboardGameTests`.
Game-test fixtures live in a separate source set and are not packaged in the mod JAR.

## Bundled Mods
The following mods are bundled in Factory Controller mod release.

Create: Higher Logistics Patch
- Fix number of logistics related bugs in Create.

Create: Logistics Control
- Filter Link: for dynamically set filter to blocks when unpack package.

## Mod Compatibility

This mod replicates the logic of the vanilla factory gauge system. It is unlikely to conflict with other mods that modifies vanilla gauge behaviour, but it cannot also receive their enhancements, unless explicit support was made.

Explicit support is also required for items added by other mods to be placeable in a factory controller. See [supported components](https://nbcss.github.io/create-factory-controller/manual/en/components.html). Notably, Create: Extra Gauges can coexist in a game installation, but the extra gauges may not be placed in factory controllers.

See also:

- [Fluid Transport](https://nbcss.github.io/create-factory-controller/manual/en/factory-gauge.html#fluid)
- [Production Orders](https://nbcss.github.io/create-factory-controller/manual/en/production-orders.html#mod-compat)
- [ComputerCraft (CC: Tweaked) Integration](https://nbcss.github.io/create-factory-controller/javadoc/io/github/nbcss/createfactorycontroller/content/compat/computercraft/package-summary.html)

## Dependencies

| Dependency              | Version  |
|-------------------------|----------|
| Minecraft               | 1.21.1   |
| NeoForge                | 21.1.227 |
| Create                  | 6.0.10   |
| Deployer (Recommended)  | 0.1.2    |

## Contributing

- Game translation: https://crowdin.com/project/createfactorycontroller
- Documentation translation: https://weblate.fiveyellowmice.com/engage/create-factory-controller/

## Credits

Graphics & documentation:

- [FiveYellowMice](https://github.com/FiveYellowMice)

Translations:

- Russian: Makisk
- Polish: Makisk
- Franch: totof-prod

<details>
  <summary>Sound assets</summary>
  <ul>
    <li>Winding Noise - Music Box by charcrone -- https://freesound.org/s/347035/ -- License: Creative Commons 0</li>
    <li>Pocket watch. Clicking open and closed, chain noise and picking up by JarredKarp -- https://freesound.org/s/490968/ -- License: Attribution NonCommercial 4.0</li>
  </ul>
</details>

## AI Disclosure

<details>
<summary>AI Disclosure</summary>
<p>I never expect that I need to extend this part to more than 1 line, but I have to do this now, which makes me very disappointed.</p>
<p>In short, AI has been used in this project for some parts of code implementation, documentation, debugging and solution improvement, but I have reviewed all AI's involvement, and code architecture design, complex code implementations and all graphics assets are still produced by me and my friend. However, I won't consider include AI in the modding process means I produced "AI slop" mod or low-effort mod; AI slop mod won't put AI disclosure section in their mod description.</p>
<p>As a software engineer and an experienced mod developer, I don't trust AI's work, but AI is a new tool and a collaborator for me. For small and well-defined task, AI does extremely well; they can often produce functional code without my modification (but sometimes I still need to fix things after close review). Therefore, it save my time so I can focus on design the code architecture of larger features, doing testing, etc. In this case, AI is a tool to allow me to parallel the development process, which is important since I don't have plenty of time after work.</p>
<p>For large feature, AI's solution is problematic; it can only use for prototyping but also enable me to understand the underlying technical limitation before spend large amount of time in development. Meanwhile, AI is also used in design improvement; it reviews my code &amp; design and may sometime point me a new direction of solving problems, which could be more efficient and optimized. AI's suggestion may not always be true, but it is so valuable to get another technical point of view during the development, to make the mod more optimized and allow me to identify bugs early. In fact, I found that this collaboration pattern significantly reduced the bugs produced, compare to my previous mod; I received almost no bug reports during the first month of release, which is hard to believe when put this amount of features into one system.</p>
<p>In this case, AI has improved the quality of the mod and my productivity, instead of produced "AI slop mod". In fact, I am more worry about the growing "AI hate" in the community. If people keep getting pissed by any AI involve project regardless how AI get used, fewer developers would decide to disclose the use of AI, and player will have to facing more mods without labeled how AI get used.</p>
</details>

<!-- ashley-release-information -->

## My builds

This is an unofficial maintenance fork. Original authors retain ownership and credit. See the [upstream project](https://github.com/nbcss/create-factory-controller) for the original mod. Local changes do not imply upstream endorsement.

Licensing: **MIT**. Existing license files, copyright notices, attribution and any separate asset terms remain in force. This fork does not relicense upstream work.

Provided **AS IS**, without warranty of any kind. Maintenance, updates and support are best effort; compatibility with every modpack is not guaranteed.

### Downloads and installation

Download a runtime `.jar` from [GitHub Releases](https://github.com/ashleywr/create-factory-controller/releases). Use the Minecraft version and loader named in that release and install its required dependencies. Do not install `-sources`, `-dev`, `-slim`, `-common` or test JARs as the mod.

Close Minecraft, back up your world, and place the matching runtime JAR in the instance's `mods/` directory. Replace older versions of this same mod rather than loading duplicates. For content mods, install matching versions on the server and its clients unless the release explicitly says client-only or server-only.

### Building and reporting problems

Use Java 21 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat build
```

Build outputs are under `build/libs/` or each loader subproject's `build/libs/`. Some projects require the local development dependencies described above; those dependencies are not included in the download.

For a problem report, include the exact release filename, Minecraft and loader versions, relevant mod versions, `logs/latest.log`, and a crash report if one was written. Remove private information before sharing logs. Remove the JAR to uninstall; worlds containing this mod's blocks, entities or world generation may require restoring a backup.
