package com.mdev.mnotes.core.codec

/**
 * Adresy plików z „Otwórz w” / „Udostępnij” otwiera mNotes swoimi prawami. Inna aplikacja nie może przez nie kazać
 * zaimportować prywatnych plików mNotes: `file://` wewnątrz katalogu danych aplikacji ani `content://` jej własnych
 * dostawców (np. `…files`, eksporty w `cache/share`).
 */
object IncomingUris {
    /**
     * [canonicalPath] — dla `file:` ścieżka po rozwinięciu `..` i dowiązań (null, gdy nie dało się jej ustalić);
     * [privateRoots] — katalogi danych aplikacji (także po rozwinięciu dowiązań); [ownPackage] — nazwa pakietu.
     */
    fun allowed(scheme: String?, authority: String?, canonicalPath: String?, ownPackage: String, privateRoots: Collection<String>): Boolean =
        when (scheme?.lowercase()) {
            "content" -> authority != null && authority != ownPackage && !authority.startsWith("$ownPackage.")
            "file" -> canonicalPath != null && privateRoots.none { root -> canonicalPath == root || canonicalPath.startsWith(root.trimEnd('/') + "/") }
            else -> false
        }
}
