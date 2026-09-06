package app.curio.platform

/**
 * Open a URL in the system browser.
 *
 * Deliberately not an in-app WebView. The only links Curio opens are legal
 * documents, and both stores prefer those to be visibly the real web page —
 * a policy rendered inside a WebView with no address bar is exactly what a
 * phishing screen looks like, and reviewers treat it accordingly.
 *
 * Silently does nothing if no browser can handle it. There is no useful
 * recovery, and crashing over an unopened privacy policy would be absurd.
 */
expect fun openUrl(url: String)
