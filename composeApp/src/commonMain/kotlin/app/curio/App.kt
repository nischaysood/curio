package app.curio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import app.curio.auth.CurioApi
import app.curio.data.CurioConfig
import app.curio.domain.Course
import app.curio.domain.Lesson
import app.curio.billing.Entitlements
import app.curio.billing.Tier
import app.curio.billing.Usage
import app.curio.billing.rememberBilling
import app.curio.domain.LessonState
import app.curio.platform.PlatformBackHandler
import app.curio.platform.openUrl
import app.curio.ui.screens.AuthScreen
import app.curio.ui.screens.GenerateScreen
import app.curio.ui.screens.LessonScreen
import app.curio.ui.screens.PathScreen
import app.curio.ui.screens.PaywallScreen
import app.curio.ui.screens.ProfileScreen
import app.curio.ui.theme.CurioTheme
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Three screens, hand-rolled navigation.
 *
 * No nav library on purpose: a sealed class and one `when` is less code than the
 * dependency's setup, it survives configuration changes the same way, and there
 * is no deep linking to support. Revisit if a fourth screen with real back-stack
 * semantics appears.
 */
private sealed interface Screen {
    /** Shown once on first launch, then never again unless the user logs out. */
    data object Auth : Screen
    data object Generate : Screen
    data object Profile : Screen
    data class Path(val course: Course, val animate: Boolean) : Screen
    data class InLesson(val course: Course, val lesson: Lesson) : Screen

    /** Remembers where to return to, so dismissing never dead-ends the user. */
    data class Paywall(val returnTo: Screen) : Screen
}

@Composable
fun App() {
    CurioTheme {
        val api = remember { CurioApi() }

        // Straight past the login screen if a token is already stored. Asking
        // someone to log in again every launch is how an account stops feeling
        // like a benefit and starts feeling like a toll gate.
        var screen by remember {
            mutableStateOf<Screen>(if (api.isSignedIn) Screen.Generate else Screen.Auth)
        }

        // Tier and today's usage. In memory only until persistence lands, which
        // means limits reset when the app dies — generous rather than strict,
        // and the right way round to be wrong while this is untested.
        val scope = rememberCoroutineScope()
        val billing = rememberBilling()
        var tier by remember { mutableStateOf(Tier.FREE) }
        var usage by remember { mutableStateOf(Usage(day = today())) }

        // Refreshed once at launch. Subscriptions change rarely and a stale FREE
        // is recoverable via Restore; blocking startup on a network call is not.
        LaunchedEffect(Unit) { tier = billing.tier() }

        val entitlements = Entitlements(tier, usage)

        // Read theme values HERE, in composable context. `transitionSpec` is not
        // a @Composable lambda, so CurioTheme.* cannot be touched inside it.
        val turnMillis = CurioTheme.motion.pageTurnMillis
        val turnEasing = CurioTheme.motion.pageTurnEasing

        // System back. Enabled everywhere except the two root screens — on a root
        // screen back should exit the app, which is what users expect and what
        // Play's reviewers check.
        PlatformBackHandler(enabled = screen !is Screen.Generate && screen !is Screen.Auth) {
            screen = when (val s = screen) {
                is Screen.InLesson -> Screen.Path(s.course, animate = false)
                is Screen.Path -> Screen.Generate
                is Screen.Profile -> Screen.Generate
                // Back must dismiss the paywall. A paywall you can't back out of
                // is a trap, and both stores treat it as one.
                is Screen.Paywall -> s.returnTo
                is Screen.Generate, is Screen.Auth -> s
            }
        }

        // MainActivity calls enableEdgeToEdge(), so without this the app draws
        // UNDER the status bar and the gesture nav bar, and the keyboard covers
        // whatever field you're typing into. safeDrawing handles system bars,
        // display cutouts and the IME in one place — applied at the root so no
        // future screen can forget it.
        Box(
            Modifier
                .fillMaxSize()
                .background(CurioTheme.colors.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val spec = tween<IntOffset>(turnMillis, easing = turnEasing)
                    val forward = initialState is Screen.Auth ||
                        initialState is Screen.Generate ||
                        (initialState is Screen.Path && targetState is Screen.InLesson)

                    if (forward) {
                        slideInHorizontally(spec) { it } + fadeIn() togetherWith
                            slideOutHorizontally(spec) { -it / 3 } + fadeOut()
                    } else {
                        slideInHorizontally(spec) { -it / 3 } + fadeIn() togetherWith
                            slideOutHorizontally(spec) { it } + fadeOut()
                    }
                },
                label = "screen",
            ) { current ->
                when (current) {
                    is Screen.Auth -> AuthScreen(
                        api = api,
                        onAuthenticated = {
                            // Bind purchases to the account. Until this runs, a
                            // subscription belongs to the install — it survives a
                            // restart but not a new phone, and the user has no way
                            // to prove it was theirs.
                            //
                            // It also merges any anonymous purchase made before
                            // signing up, which is the common order: people buy
                            // first and create an account later.
                            scope.launch {
                                api.userId?.let { tier = billing.logIn(it) }
                            }
                            screen = Screen.Generate
                        },
                        onSkip = { screen = Screen.Generate },
                    )

                    is Screen.Generate -> GenerateScreen(
                        onCourseReady = { screen = Screen.Path(it, animate = true) },
                        onOpenProfile = { screen = Screen.Profile },
                    )

                    is Screen.Profile -> ProfileScreen(
                        api = api,
                        billing = billing,
                        tier = tier,
                        usage = usage,
                        onUpgrade = { screen = Screen.Paywall(returnTo = Screen.Profile) },
                        onSignIn = { screen = Screen.Auth },
                        onSignedOut = {
                            // Return RevenueCat to an anonymous id too. Otherwise
                            // the next person to open the app on this device
                            // inherits the previous user's subscription — which on
                            // a shared or handed-down phone is giving it away.
                            scope.launch { billing.logOut() }
                            tier = Tier.FREE
                            screen = Screen.Auth
                        },
                        onBack = { screen = Screen.Generate },
                        onOpenPrivacy = { openUrl(CurioConfig.PRIVACY_POLICY_URL) },
                    )

                    is Screen.Path -> PathScreen(
                        course = current.course,
                        animateAssembly = current.animate,
                        onLessonSelected = { lesson ->
                            // The gate. Free tier gets three lessons a day; the
                            // fourth tap shows the paywall instead of the lesson.
                            screen = if (entitlements.canStartLesson().isAllowed) {
                                Screen.InLesson(current.course, lesson)
                            } else {
                                Screen.Paywall(returnTo = current)
                            }
                        },
                        onBack = { screen = Screen.Generate },
                    )

                    is Screen.Paywall -> PaywallScreen(
                        billing = billing,
                        onDismiss = { screen = current.returnTo },
                        onPurchased = {
                            tier = Tier.PREMIUM
                            screen = current.returnTo
                        },
                    )

                    is Screen.InLesson -> LessonScreen(
                        lesson = current.lesson,
                        onExit = { screen = Screen.Path(current.course, animate = false) },
                        onComplete = { correct ->
                            // Count it BEFORE navigating, or the daily limit never
                            // advances and the paywall never fires.
                            usage = Entitlements(tier, usage).afterLessonCompleted()

                            // Mirror it to the server when signed in. Not awaited:
                            // the path screen shouldn't wait on a round trip to
                            // show a lesson the user just finished. A dropped call
                            // costs one lesson of history, not correctness.
                            scope.launch {
                                api.recordProgress(
                                    topicHash = current.course.topicHash,
                                    lessonId = current.lesson.id,
                                    correct = correct,
                                    total = current.lesson.exercises.size,
                                )
                            }

                            // Progress lives in memory only until persistence lands.
                            // Returning to a path that forgot what you just did is
                            // the single most demoralising thing a learning app can
                            // do, so this is the next thing to make durable.
                            screen = Screen.Path(
                                course = current.course.completing(current.lesson, correct),
                                animate = false,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Mark a lesson complete and unlock the next one.
 *
 * A lesson counts as complete however it went — getting things wrong is how
 * learning works, and locking someone out of lesson two for missing a question
 * would punish exactly the behaviour we want. Score decides whether it's flagged
 * for review, not whether you may continue.
 */
private fun Course.completing(lesson: Lesson, correctCount: Int): Course {
    val threshold = lesson.exercises.size * 0.6f
    val newState = if (correctCount >= threshold) LessonState.COMPLETE else LessonState.NEEDS_REVIEW

    return copy(
        lessons = lessons.map {
            when {
                it.id == lesson.id -> it.copy(state = newState)
                it.position == lesson.position + 1 && it.state == LessonState.LOCKED ->
                    it.copy(state = LessonState.AVAILABLE)
                else -> it
            }
        },
    )
}

/** Today in the device's timezone. One definition, so the daily reset is consistent. */
private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
