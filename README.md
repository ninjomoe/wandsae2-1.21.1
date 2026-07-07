# Building Wands AE2 Integration

Addon mod for [Building Wands](https://github.com/nicguzzo/wands) and Applied Energistics 2 on NeoForge 1.21.1.

This mod lets Building Wands behave more like AE2 wireless tools: link a wand to your ME system through an AE2 Wireless Access Point, then use the wand with blocks stored in that ME system.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.230 or newer
- Applied Energistics 2 19.2.17 or newer
- Building Wands 3.0.4 or newer

## Core Features

- Building Wands can be linked through an AE2 Wireless Access Point.
- Linked wands count matching blocks stored in the ME system when placing.
- Placed blocks are extracted from ME storage instead of requiring them in the player inventory.
- Missing blocks can trigger AE2 autocrafting if the ME system has a crafting pattern for them.
- AE2 crafting uses the linked ME network, including available stored ingredients and crafting CPUs.

## AE2 Wand Menu

When a wand is linked to AE2, the wand menu gets an `AE2` tab.

### Missing Blocks

Controls what happens when the wand needs blocks that are not currently stored, but are craftable in AE2.

- `Ask`: opens the AE2 autocrafting popup so the player can review and confirm the craft.
- `Auto`: submits the AE2 craft automatically without opening the popup.

### Craft Excess

Controls whether the wand should craft extra blocks beyond the amount currently missing.

- `OFF`: only craft the missing amount.
- `ON`: craft the missing amount plus the configured extra amount.

### Amount

The number of extra blocks to craft when `Craft Excess` is `ON`.

- Minimum: `1`
- Maximum: `1000`
- Saved per wand