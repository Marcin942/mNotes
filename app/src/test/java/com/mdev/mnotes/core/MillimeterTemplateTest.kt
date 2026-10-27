package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import com.mdev.mnotes.core.model.TemplateGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pomysł Fable nr 9: papier milimetrowy jako szablon. */
class MillimeterTemplateTest {
    @Test fun heavyEveryCentimetreThinEveryMillimetre() {
        val spec = PageSpec.of(PageFormat.A4, template = Template.MILLIMETER)
        val g = TemplateGeometry.build(spec)
        val thin = g.lines.size / 4; val heavy = g.heavy.size / 4
        // A4 ≈ 21 × 29,7 cm: ok. 20 + 29 grubych linii i dziewięć razy tyle cienkich.
        assertTrue("grube: $heavy", heavy in 45..55)
        assertEquals(9.0, thin.toDouble() / heavy, 1.0)
    }

    @Test fun codeRoundTripsAndOldReaderFallsBack() {
        val spec = PageSpec.of(PageFormat.A5, template = Template.MILLIMETER)
        assertEquals(Template.MILLIMETER, PageSpec.fromJson(spec.toJson()).template)
        assertEquals(Template.BLANK, Template.fromCode(99))
    }
}
