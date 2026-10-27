# Third-party notices

IWantQuests gameplay and UI source and the empty GameTest structure are original.
No third-party questing source or artwork is copied or bundled.

The development build derives from the official NeoForge-generated 26.2 MDK:
https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle

The Gradle wrapper comes from that MDK. Gradle is licensed under Apache-2.0:
https://github.com/gradle/gradle/blob/master/LICENSE

NeoForge is a runtime dependency, licensed under LGPL-2.1-only:
https://github.com/neoforged/NeoForge
It is not bundled in the mod JAR. Minecraft and its built-in item icons are supplied
by the user's game installation and are subject to Mojang/Microsoft's terms.

Gson is supplied by Minecraft at runtime (Apache-2.0). JUnit is test-only
(EPL-2.0). Neither is shaded or bundled into the release mod JAR.

FTB Quests' quest-tree interactions and team/reward model were inspected as a
functional reference only. Its repository does not grant a licence to copy code
or assets through a root licence file on the inspected main branch. No FTB
implementation, library, texture, or other artwork is included in this project.
