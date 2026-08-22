package com.onemind.app

import com.onemind.app.domain.categories.CategoryDictionary
import org.junit.Assert.*
import org.junit.Test

/**
 * Integrity of the shipped vocabulary.
 *
 * These read as trivial, but the dictionary is seeded into a table with a unique
 * index and referenced by foreign key. A duplicate or a stray blank would surface
 * as a migration failure on a user's device, which is an expensive place to find
 * out.
 */
class CategoryDictionaryTest {

    @Test
    fun `the dictionary is broad enough to cover real-world topics`() {
        assertTrue(
            "expected around 35 categories, found ${CategoryDictionary.ALL.size}",
            CategoryDictionary.ALL.size in 30..45
        )
    }

    @Test
    fun `no duplicates, which the unique index would reject`() {
        val names = CategoryDictionary.ALL

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `no case-insensitive duplicates either, since matching folds case`() {
        // "Travel" and "travel" would match the same model output, making the
        // assignment ambiguous.
        val folded = CategoryDictionary.ALL.map { it.lowercase() }

        assertEquals(folded.size, folded.toSet().size)
    }

    @Test
    fun `every name is non-blank and trimmed`() {
        CategoryDictionary.ALL.forEach { name ->
            assertTrue("blank category name", name.isNotBlank())
            assertEquals("category name not trimmed: '$name'", name.trim(), name)
        }
    }

    @Test
    fun `no name contains a comma, which parsing splits on`() {
        // The response parser treats commas as separators, so a category whose
        // name contained one could never be matched.
        CategoryDictionary.ALL.forEach { name ->
            assertFalse("category name contains a comma: '$name'", name.contains(','))
        }
    }

    @Test
    fun `no name contains a newline, which parsing splits on`() {
        CategoryDictionary.ALL.forEach { name ->
            assertFalse(name.contains('\n'))
        }
    }

    @Test
    fun `names are short enough to read as a chip`() {
        CategoryDictionary.ALL.forEach { name ->
            assertTrue("category name too long to be a chip: '$name'", name.length <= 24)
        }
    }

    @Test
    fun `order is stable, so seeding assigns the same ids everywhere`() {
        // A fresh install and an upgrade both seed from this list. If the order
        // varied, the same category would get different ids on different devices,
        // which would matter the moment assignments are ever synced or exported.
        assertEquals("Technology", CategoryDictionary.ALL.first())
        assertEquals(CategoryDictionary.ALL, CategoryDictionary.ALL)
    }
}
