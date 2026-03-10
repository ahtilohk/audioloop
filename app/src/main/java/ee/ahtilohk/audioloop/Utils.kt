package ee.ahtilohk.audioloop

/**
 * Sanitizes a file name by removing characters that are
 * invalid on common file systems.
 */
fun sanitizeName(name: String): String {
    val sb = StringBuilder()
    for (c in name) {
        if (c == '/' || c == '\\' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') continue
        if (c == ':') sb.append('_') else sb.append(c)
    }
    return sb.toString().trim()
}
