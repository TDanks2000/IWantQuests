# IWantQuests project requirements

## Project identity

Use these identifiers consistently across source code, metadata, Gradle configuration,
resources, configuration files, documentation, the in-game quest book, commands,
and permissions:

- Display name: `IWantQuests`
- Mod ID: `iwantquests`
- Base Java package: `dev.iwantquests`
- Main entrypoint class: `IWantQuests`
- Resource namespace: `iwantquests`
- Command root: `/iwantquests`

Use only the official project name. Do not introduce legacy project names into
source code or documentation. Derive configuration filenames and permission
identifiers from `iwantquests` where the chosen platform supports them.

## Product direction

IWantQuests is a standalone questing mod for Minecraft. It is not exclusive to
any particular modpack. Other modpack developers must be able to create their
own quest chapters, objectives, rewards, and progression systems.

Aim for a polished, modern alternative to FTB Quests, with a strong focus on:

- Cooperative multiplayer progression.
- Individual rewards.
- An excellent quest-tree interface.
- Ease of use for players and modpack developers.

Keep modpack-specific content separate from the reusable questing system.
