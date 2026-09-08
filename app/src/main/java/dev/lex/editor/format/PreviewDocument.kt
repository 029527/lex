package dev.lex.editor.format

import dev.lex.editor.model.FileType

/**
 * Builds the standalone HTML documents fed to the preview WebView.
 *
 * Nothing here escapes its input: the Markdown branch gets HTML that commonmark
 * has already escaped, and the HTML branch is meant to render the user's own
 * markup as markup -- escaping it would show them their source instead of their
 * page.
 */
object PreviewDocument {

    /** Full standalone HTML document for a WebView, or null when [type] has no preview. */
    fun build(type: FileType, text: String, darkTheme: Boolean): String? = when (type) {
        FileType.MARKDOWN -> wrap(MarkdownRenderer.toHtml(text), darkTheme)
        // Already a whole page? Hand it over untouched -- the author's own <head>,
        // styles and scripts win over ours.
        FileType.HTML -> if (text.contains("<html", ignoreCase = true)) text else wrap(text, darkTheme)
        FileType.JSON, FileType.PLAIN -> null
    }

    private fun wrap(bodyHtml: String, darkTheme: Boolean): String = buildString(bodyHtml.length + STYLE.length + 256) {
        append("<!DOCTYPE html>\n")
        append("<html>\n<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<style>\n")
        append(if (darkTheme) DARK_PALETTE else LIGHT_PALETTE)
        append(STYLE)
        append("</style>\n</head>\n<body>\n")
        append(bodyHtml)
        append("\n</body>\n</html>")
    }

    // The two palettes define the same custom properties, so exactly one set of
    // rules below has to exist for both themes.
    private const val LIGHT_PALETTE = """
:root {
  color-scheme: light;
  --bg: #ffffff;
  --fg: #1f2328;
  --muted: #59636e;
  --link: #0969da;
  --border: #d1d9e0;
  --rule: #d1d9e0;
  --code-bg: rgba(129, 139, 152, 0.12);
  --pre-bg: #f6f8fa;
  --quote-border: #d1d9e0;
  --table-header-bg: #f6f8fa;
  --table-stripe: #f6f8fa;
  --mark-bg: #fff8c5;
}
"""

    private const val DARK_PALETTE = """
:root {
  color-scheme: dark;
  --bg: #0d1117;
  --fg: #e6edf3;
  --muted: #9198a1;
  --link: #4493f8;
  --border: #3d444d;
  --rule: #3d444d;
  --code-bg: rgba(110, 118, 129, 0.35);
  --pre-bg: #161b22;
  --quote-border: #3d444d;
  --table-header-bg: #161b22;
  --table-stripe: #161b22;
  --mark-bg: #4a3d00;
}
"""

    private const val STYLE = """
* { box-sizing: border-box; }

html { -webkit-text-size-adjust: 100%; }

body {
  margin: 0 auto;
  padding: 16px 16px 48px;
  max-width: 44rem;
  background: var(--bg);
  color: var(--fg);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue",
               Arial, "Noto Sans", sans-serif, "Apple Color Emoji", "Segoe UI Emoji";
  font-size: 16px;
  line-height: 1.65;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

body > *:first-child { margin-top: 0; }
body > *:last-child { margin-bottom: 0; }

p, ul, ol, dl, blockquote, table, pre { margin: 0 0 1em; }

h1, h2, h3, h4, h5, h6 {
  margin: 1.6em 0 0.6em;
  line-height: 1.3;
  font-weight: 600;
}
h1 { font-size: 1.9em; padding-bottom: 0.3em; border-bottom: 1px solid var(--rule); }
h2 { font-size: 1.45em; padding-bottom: 0.3em; border-bottom: 1px solid var(--rule); }
h3 { font-size: 1.2em; }
h4 { font-size: 1em; }
h5 { font-size: 0.9em; }
h6 { font-size: 0.85em; color: var(--muted); }

a { color: var(--link); text-decoration: none; }
a:hover { text-decoration: underline; }

strong { font-weight: 600; }
mark { background: var(--mark-bg); color: inherit; }
small { color: var(--muted); }

hr {
  height: 1px;
  margin: 2em 0;
  border: 0;
  background: var(--rule);
}

ul, ol { padding-left: 1.6em; }
li { margin: 0.25em 0; }
li > ul, li > ol { margin: 0.25em 0; }

blockquote {
  padding: 0 1em;
  color: var(--muted);
  border-left: 0.25em solid var(--quote-border);
}
blockquote > *:first-child { margin-top: 0; }
blockquote > *:last-child { margin-bottom: 0; }

code, kbd, samp, pre {
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas,
               "Liberation Mono", monospace;
}

code {
  padding: 0.2em 0.4em;
  font-size: 0.88em;
  background: var(--code-bg);
  border-radius: 6px;
}

pre {
  padding: 12px;
  font-size: 0.85em;
  line-height: 1.5;
  background: var(--pre-bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

pre code {
  padding: 0;
  font-size: inherit;
  background: none;
  border-radius: 0;
  white-space: pre;
}

/* Markdown tables arrive without a wrapper element, so the table itself has to be
   the scroll container on narrow screens. */
table {
  display: block;
  width: max-content;
  max-width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-collapse: collapse;
  font-size: 0.95em;
}
th, td {
  padding: 6px 13px;
  border: 1px solid var(--border);
  text-align: left;
}
th { background: var(--table-header-bg); font-weight: 600; }
tbody tr:nth-child(2n) { background: var(--table-stripe); }

img, video, svg {
  max-width: 100%;
  height: auto;
}

input[type="checkbox"] { margin-right: 0.4em; }
"""
}
