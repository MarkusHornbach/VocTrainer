import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VersionNameTest {
    @Test
    fun exactlyOnAReleaseTag() {
        assertEquals("1.0.0", VersionName.fromDescribe("v1.0.0-0-gabc1234"))
        assertEquals("1.12.3", VersionName.fromDescribe("v1.12.3-0-gabc1234\n"))
    }

    @Test
    fun commitsAfterAReleaseTag() {
        assertEquals("1.0.0+3 (abc1234)", VersionName.fromDescribe("v1.0.0-3-gabc1234"))
    }

    @Test
    fun localChangesAreMarkedDirty() {
        assertEquals("1.0.0+0 (abc1234) dirty", VersionName.fromDescribe("v1.0.0-0-gabc1234-dirty"))
        assertEquals("1.0.0+3 (abc1234) dirty", VersionName.fromDescribe("v1.0.0-3-gabc1234-dirty"))
    }

    @Test
    fun releaseCandidates() {
        assertEquals("1.0.0-rc1", VersionName.fromDescribe("v1.0.0-rc1-0-gabc1234"))
        assertEquals("1.0.0-rc12", VersionName.fromDescribe("v1.0.0-rc12-0-gabc1234"))
        assertEquals("1.0.0-rc1+3 (abc1234)", VersionName.fromDescribe("v1.0.0-rc1-3-gabc1234"))
        assertEquals("1.0.0-rc1+0 (abc1234) dirty", VersionName.fromDescribe("v1.0.0-rc1-0-gabc1234-dirty"))
        assertEquals("1.0.0-rc2+5 (abc1234) dirty", VersionName.fromDescribe("v1.0.0-rc2-5-gabc1234-dirty"))
    }

    @Test
    fun otherTagsAreNoRelease() {
        for (output in listOf("", "abc1234", "v1.0-0-gabc1234", "v1.0.0-beta1-0-gabc1234", "v1.0.0-rc-0-gabc1234", "1.0.0-0-gabc1234")) {
            assertNull(output, VersionName.fromDescribe(output))
        }
    }

    @Test
    fun withoutAnyReleaseTag() {
        assertEquals("0.0.0+12 (abc1234)", VersionName.withoutTag(12, "abc1234"))
        assertEquals("0.0.0+12 (abc1234) dirty", VersionName.withoutTag(12, "abc1234-dirty"))
    }

    @Test
    fun versionCodeFromTheVersion() {
        assertEquals(1000002, VersionName.code("1.0.0-rc2"))
        assertEquals(1000099, VersionName.code("1.0.0"))
        assertEquals(1020399, VersionName.code("1.2.3"))
        assertEquals(1000001, VersionName.code("1.0.0-rc1"))
        assertEquals(12999999, VersionName.code("12.99.99"))
    }

    @Test
    fun versionCodesIncreaseInVersionOrder() {
        val order = listOf("0.14.0", "1.0.0-rc1", "1.0.0-rc2", "1.0.0", "1.0.1-rc1", "1.0.1", "1.1.0", "1.99.99", "2.0.0-rc1")
        val codes = order.map(VersionName::code)
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun versionCodeAfterATagKeepsTheTagsCode() {
        assertEquals(1000002, VersionName.codeFromDescribe("v1.0.0-rc2-0-gabc1234"))
        assertEquals(1000099, VersionName.codeFromDescribe("v1.0.0-0-gabc1234\n"))
        assertEquals(1000099, VersionName.codeFromDescribe("v1.0.0-3-gabc1234"))
        assertEquals(1000099, VersionName.codeFromDescribe("v1.0.0-3-gabc1234-dirty"))
        assertEquals(1000002, VersionName.codeFromDescribe("v1.0.0-rc2-5-gabc1234-dirty"))
    }

    @Test
    fun noVersionCodeWithoutAReleaseTag() {
        for (output in listOf("", "abc1234", "v1.0-0-gabc1234", "v1.0.0-beta1-0-gabc1234")) {
            assertNull(output, VersionName.codeFromDescribe(output))
        }
    }

    @Test
    fun versionsBeyondTheCodeRangeFailTheBuild() {
        for (version in listOf("1.100.0", "1.0.100", "1.0.0-rc99", "1.0.0-rc100", "1.0", "x")) {
            val e = assertThrows(version, IllegalArgumentException::class.java) { VersionName.code(version) }
            assert(e.message!!.startsWith("VocTrainer:")) { e.message!! }
        }
        assertEquals(1000098, VersionName.code("1.0.0-rc98"))
    }
}
