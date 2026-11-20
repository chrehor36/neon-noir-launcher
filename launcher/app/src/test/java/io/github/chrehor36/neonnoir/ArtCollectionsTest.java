package io.github.chrehor36.neonnoir;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for Art Mode's collections: unique ids, All first, and well-formed search clauses. */
public class ArtCollectionsTest {

    @Test
    public void allPaintingsComesFirstAndIsTheFallback() {
        assertEquals(ArtCollections.ALL_ID, ArtCollections.ALL.get(0).id);
        assertTrue(ArtCollections.ALL.get(0).isAll());
        assertEquals(ArtCollections.ALL_ID, ArtCollections.byId("no-such-collection").id);
    }

    @Test
    public void idsAreUniqueAndUsableInFileNames() {
        Set<String> ids = new HashSet<>();
        for (ArtCollections.Collection c : ArtCollections.ALL) {
            assertTrue("duplicate id " + c.id, ids.add(c.id));
            assertTrue("id " + c.id, c.id.matches("[a-z_]+"));
        }
    }

    @Test
    public void everyCollectionButAllNarrowsTheSearch() {
        for (ArtCollections.Collection c : ArtCollections.ALL) {
            if (c.isAll()) continue;
            assertFalse(c.id + " narrows nothing", c.must.isEmpty() && c.should.isEmpty());
            assertFalse(c.id + " has no group", c.group.isEmpty());
        }
    }

    @Test
    public void clausesAreBalancedJson() {
        for (ArtCollections.Collection c : ArtCollections.ALL) {
            assertBalanced(c.id, c.must);
            assertBalanced(c.id, c.should);
        }
    }

    @Test
    public void titlesNameTheGroup() {
        assertEquals("All paintings", ArtCollections.byId(ArtCollections.ALL_ID).title());
        assertEquals("Mood · Jazz bar", ArtCollections.byId("jazz_bar").title());
    }

    private static void assertBalanced(String id, String json) {
        int braces = 0, brackets = 0;
        boolean quoted = false;
        for (char ch : json.toCharArray()) {
            if (ch == '"') quoted = !quoted;
            if (quoted) continue;
            if (ch == '{') braces++;
            if (ch == '}') braces--;
            if (ch == '[') brackets++;
            if (ch == ']') brackets--;
            assertTrue(id + " closes too early", braces >= 0 && brackets >= 0);
        }
        assertFalse(id + " has an open quote", quoted);
        assertEquals(id + " braces", 0, braces);
        assertEquals(id + " brackets", 0, brackets);
    }
}
