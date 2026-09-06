# Fonts

| File | Family | Role |
|---|---|---|
| `fraunces_variable.ttf` | [Fraunces](https://fonts.google.com/specimen/Fraunces) | Display — headings, lesson titles |
| `inter_tight.ttf` | [Inter Tight](https://fonts.google.com/specimen/Inter+Tight) | Text — prompts, body, tiles, labels |

Both are variable fonts: one file covers every weight, so the pair costs roughly
900KB instead of the ~2MB an equivalent set of static weights would.

Both are licensed under the **SIL Open Font License 1.1**, which permits
commercial distribution inside an app. The OFL text ships in each font's
download — keep a copy if an attributions screen is ever added.

## Renaming matters

Compose Resources generates Kotlin accessors from filenames, so files must be
lowercase with underscores. The originals ship as
`Fraunces-VariableFont_SOFT,WONK,opsz,wght.ttf` and
`InterTight-VariableFont_wght.ttf` — capitals, hyphens, brackets and commas all
break the generated `Res.font.*` names.

## Wiring

Read by `ui/theme/Theme.kt` via `Res.font.fraunces_variable` and
`Res.font.inter_tight`. Nothing else references them: every screen goes through
`CurioTheme.type`, so changing a face is a one-file edit.
