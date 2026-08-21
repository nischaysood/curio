# Fonts

Drop two .ttf files here, then update `DisplayFace` / `TextFace` in `ui/theme/Theme.kt`.

| Role | Candidates | Why |
|---|---|---|
| Display | Fraunces, Instrument Serif | Warm, slightly odd, reads as "collected object" not "ed-tech" |
| Text | Inter Tight, Figtree | Neutral, tight, good at 13-20sp on small screens |

All are SIL Open Font License — free to ship commercially. Get them from
fonts.google.com, take the variable .ttf, rename to lowercase with underscores
(Compose Resources requires that): `fraunces_variable.ttf`, `inter_tight.ttf`.

Then in Theme.kt:

    import curio.composeapp.generated.resources.Res
    import curio.composeapp.generated.resources.fraunces_variable
    import org.jetbrains.compose.resources.Font

    private val DisplayFace @Composable get() = FontFamily(Font(Res.font.fraunces_variable))

Note the @Composable getter — Compose Resources fonts load asynchronously, so
they cannot be top-level vals. CurioTypography will need to become a @Composable
function rather than a val at the same time.
