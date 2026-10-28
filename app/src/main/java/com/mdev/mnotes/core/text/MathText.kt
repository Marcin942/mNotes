package com.mdev.mnotes.core.text

/**
 * Grecki i indeksy przy pisaniu (L2, pomysł z pracy licencjackiej, napisane od zera): `\alpha` → α, `x^2` → x²,
 * `a_1` → a₁, `x^{-1}` → x⁻¹, `a_{ij}` → aᵢⱼ. Zamiana następuje dopiero po zakończeniu znacznika (spacja, znak
 * przestankowy), więc `\alpha` nie zamieni się w połowie pisania `\alphabet`, a `nazwa_pliku` zostaje bez zmian
 * (po `_` litera bez nawiasu klamrowego tylko przed znakiem niebędącym literą).
 */
object MathText {
    private val GREEK = linkedMapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ",
        "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
        "xi" to "ξ", "omicron" to "ο", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ",
        "upsilon" to "υ", "phi" to "φ", "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ",
        "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        // Najczęstsze symbole z zajęć (też w duchu „pisz jak w LaTeX-u”).
        "infty" to "∞", "pm" to "±", "cdot" to "·", "times" to "×", "leq" to "≤", "geq" to "≥", "neq" to "≠", "approx" to "≈",
        "to" to "→", "in" to "∈", "sum" to "∑", "int" to "∫", "partial" to "∂", "nabla" to "∇", "sqrt" to "√", "deg" to "°",
    )
    private const val SUP_SRC = "0123456789+-=()niax"
    private const val SUP_DST = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱᵃˣ"
    private const val SUB_SRC = "0123456789+-=()aeoxhklmnpstij"
    private const val SUB_DST = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₒₓₕₖₗₘₙₚₛₜᵢⱼ"

    private fun sup(c: Char): Char? = SUP_SRC.indexOf(c).let { if (it < 0) null else SUP_DST[it] }
    private fun sub(c: Char): Char? = SUB_SRC.indexOf(c).let { if (it < 0) null else SUB_DST[it] }

    // FB13: odwrotność tabel do eksportu TikZ — co aplikacja zamieniła przy pisaniu, wraca do LaTeX-a.
    private val LATEX: Map<Char, String> by lazy {
        val m = HashMap<Char, String>()
        for ((name, s) in GREEK) if (s.length == 1) m.putIfAbsent(s[0], "\\$name")
        // `\sqrt` i `\deg` w LaTeX-u znaczą co innego niż sam znak (pierwiastek z argumentem, operator „deg”).
        m['√'] = "\\surd"; m['°'] = "^\\circ"
        m
    }

    /** Polecenie LaTeX (tryb matematyczny) dla znaku z tabeli greckiej i symboli, np. `α` → `\alpha`; null — zwykły znak. */
    fun latexOf(c: Char): String? = LATEX[c]

    /** Znak przed zamianą na indeks górny (`²` → `2`) albo null. */
    fun unSup(c: Char): Char? = SUP_DST.indexOf(c).let { if (it < 0) null else SUP_SRC[it] }

    /** Znak przed zamianą na indeks dolny (`₂` → `2`) albo null. */
    fun unSub(c: Char): Char? = SUB_DST.indexOf(c).let { if (it < 0) null else SUB_SRC[it] }

    /**
     * Zamienia wszystkie zakończone znaczniki w [s]. [atEnd] = tekst zatwierdzony („Gotowe”): koniec tekstu też kończy
     * `\alpha` i ciąg cyfr, ale nie pojedynczą literę (`plik_a` zostaje).
     */
    fun convert(s: String, atEnd: Boolean = false): String {
        if ('\\' !in s && '^' !in s && '_' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> {
                    var j = i + 1
                    while (j < s.length && s[j].isLetter()) j++
                    val name = s.substring(i + 1, j)
                    val g = GREEK[name]
                    // Znacznik zakończony dowolnym nieliterowym znakiem (zostaje); na końcu tekstu czekamy na dalsze litery.
                    if (g != null && (j < s.length || atEnd)) {
                        out.append(g)
                        i = j
                        continue
                    }
                    out.append(c); i++
                }
                (c == '^' || c == '_') && i > 0 -> {
                    val map: (Char) -> Char? = if (c == '^') ::sup else ::sub
                    val n = s.getOrNull(i + 1)
                    if (n == '{') {
                        val end = s.indexOf('}', i + 2)
                        val body = if (end > 0) s.substring(i + 2, end) else null
                        val conv = body?.map { map(it) }
                        if (conv != null && body.isNotEmpty() && conv.all { it != null }) { conv.forEach { out.append(it) }; i = end + 1; continue }
                    } else if (n != null && (n in '0'..'9' || n == '+' || n == '-')) {
                        // Znak i cyfry czekają na koniec ciągu, inaczej „x^10” dałoby „x¹0”. Po „^” ciąg kończy też litera
                        // („x^2y” to wzór), po „_” tylko nielitera, bo w nazwach plików („plik_2a”) podkreślenie jest częste.
                        var j = i + 1
                        if (n !in '0'..'9') j++
                        while (j < s.length && s[j] in '0'..'9') j++
                        val stop = s.getOrNull(j)
                        val closed = if (stop == null) atEnd else c == '^' || !stop.isLetter()
                        if (closed) { for (k in i + 1 until j) out.append(map(s[k]) ?: s[k]); i = j; continue }
                    } else if (n != null) {
                        val m = map(n)
                        val after = s.getOrNull(i + 2)
                        // Pozostałe znaki od razu; litera tylko przed nieliterą (żeby „nazwa_pliku” zostało).
                        if (m != null && (!n.isLetter() || (after != null && !after.isLetter()))) { out.append(m); i += 2; continue }
                    }
                    out.append(c); i++
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
