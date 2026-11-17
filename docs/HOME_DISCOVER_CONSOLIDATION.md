# Home and Discover consolidation (M3.C)

User-authorized priority on 17 November 2026, before M3.3 E or further integrations.
References: supplied iOS Home Screen/Discover Screen settings screenshots and the read-only
iOS HomeLayoutConfiguration, DiscoverLayoutConfiguration and SectionLayoutConfiguration.
Implement Android-native controls and the same product hierarchy; copy no iOS source/assets.

## Decisions and acceptance criteria

- Independent Home and Discover visibility/order; a supported section can appear on both.
  Placement never changes feature consent. Resetting layout never enables a network integration.
- Queue Builder shares its server-scoped picks/playback engine across both placements.
  Song Path gets both placements when M3.5 implements it; do not offer a nonworking section now.
- Home/Discover editors belong in Settings > Application. Keep Home carousel/grid choices.
  Both editors support show/hide, drag reorder, accessible reorder, Reset, Save and Cancel.
- Autoplay similar moves to Settings > Playback. Fresh releases consent/disclosure moves to
  Settings > Application alongside Discover configuration. No settings switches in content feeds.
- Persist old feature preferences unchanged. Preserve existing custom Home order/hidden/grid choices.
  New installs/reset use product defaults; migration must not silently reset customization.
- Browsing reads Room. Preserve downloaded-only offline semantics, pins and stable navigation.

## Inherited surface audit

| Existing surface | Decision |
|---|---|
| Pinned / Your playlists | Keep; lead Home, configurable cover shelves |
| Recently starred songs | Adapt label to Your favorite songs; still actual starred songs |
| Favorite albums | Keep; Your favorite albums |
| Recently played albums | Adapt label to Recently played |
| Most played albums | Adapt label to In heavy rotation |
| Recently/most played artists | Supplement with Artist picks; legacy variants optional and hidden by default |
| Recently added / Rediscover / Genres | Keep with iOS hierarchy and deliberate artwork/spacing |
| Decades | Retain capability as optional Home section; hidden in product default (Queue Builder also supports decades) |
| Smart Shuffle / Stations / Queue Builder | Keep Discover actions; independently configurable |
| Autoplay / Fresh releases switches | Relocate to Settings; never layout items |
| Internet radio / Lidarr | Keep existing out-of-scope decisions; no placeholders |
| Offline mirror, multiple servers, quality, downloads, sort names | Keep Android capabilities; no domain rewrite |
| Song Path / Moods / Wrapped | Add real sections in their existing milestones, using layout migration rules |

## Ordered implementation / handover checklist

- [x] C1 Layout model and migration: independent Discover config, new Home defaults, shared-section IDs;
      tests for old preferences, unknown/new sections, independence and reset/consent separation.
- [x] C2 Settings: common layout editor behavior, Discover editor, move both toggles; no lost settings.
- [x] C3 Discover: ordered/hidden real sections, no switches, empty/offline behavior and editor access.
- [x] C4 Home: shared Queue Builder placement and picks, Artist picks, adapted labels/default hierarchy;
      preserve customized layouts and downloaded offline Home. Review actual content composition.
- [x] C5 Verification/closeout: regression mutations, full build/tests/lint, normal/2× light/dark renders,
      emulator show/hide/relaunch, shared picks, consent separation; parity matrix refreshed.
      Drag/TalkBack reorder verified by unit tests only (see handoff).

## Working state

See docs/handoffs/M3.C-home-discover.md for actual progress, commands, failures, Git state and next action.
Completed 17 November 2026; the handoff records verification and remaining gaps. M3.3 E is next.
