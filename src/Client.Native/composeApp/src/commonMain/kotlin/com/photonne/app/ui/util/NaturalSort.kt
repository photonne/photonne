package com.photonne.app.ui.util

/**
 * Case-insensitive, numeric-aware ("natural") string comparator.
 *
 * Splits each string into maximal runs of digits and non-digits and compares
 * run by run: numeric runs by value (so "9" < "10" and "2001" < "2023"),
 * textual runs case-insensitively. This gives the ordering users expect for
 * names like "Viaje 2" < "Viaje 10" or years "2001" < "2006", where a plain
 * lexicographic `String.compareTo` would misorder unequal-width numbers.
 *
 * Lives in commonMain, so it deliberately avoids `String.CASE_INSENSITIVE_ORDER`
 * (JVM-only) and lowercases per character instead.
 */
val naturalStringComparator: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

/** Sort by a [String] key using [naturalStringComparator]. */
inline fun <T> Iterable<T>.sortedByNatural(crossinline selector: (T) -> String): List<T> =
    sortedWith(compareBy(naturalStringComparator, selector))

private fun compareNatural(a: String, b: String): Int {
    var ia = 0
    var ib = 0
    val la = a.length
    val lb = b.length
    while (ia < la && ib < lb) {
        if (a[ia].isDigit() && b[ib].isDigit()) {
            val startA = ia
            val startB = ib
            while (ia < la && a[ia].isDigit()) ia++
            while (ib < lb && b[ib].isDigit()) ib++
            val cmp = compareDigitRun(a, startA, ia, b, startB, ib)
            if (cmp != 0) return cmp
        } else {
            val ca = a[ia].lowercaseChar()
            val cb = b[ib].lowercaseChar()
            if (ca != cb) return ca.compareTo(cb)
            ia++
            ib++
        }
    }
    // Prefix-of comparison: the shorter string sorts first.
    val remA = la - ia
    val remB = lb - ib
    if (remA != remB) return remA.compareTo(remB)
    // Equal ignoring case: fall back to case-sensitive order for a stable tie-break.
    return a.compareTo(b)
}

/**
 * Compare two digit runs `a[startA until endA]` and `b[startB until endB]` by
 * numeric value without parsing (overflow-safe for arbitrarily long numbers).
 */
private fun compareDigitRun(
    a: String,
    startA: Int,
    endA: Int,
    b: String,
    startB: Int,
    endB: Int
): Int {
    // Skip leading zeros so magnitude is compared by significant-digit count.
    var sa = startA
    var sb = startB
    while (sa < endA - 1 && a[sa] == '0') sa++
    while (sb < endB - 1 && b[sb] == '0') sb++
    val lenA = endA - sa
    val lenB = endB - sb
    if (lenA != lenB) return lenA.compareTo(lenB)
    var i = 0
    while (i < lenA) {
        val c = a[sa + i].compareTo(b[sb + i])
        if (c != 0) return c
        i++
    }
    // Same magnitude; fewer leading zeros sorts first for determinism.
    return (endA - startA).compareTo(endB - startB)
}
