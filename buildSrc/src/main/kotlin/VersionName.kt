/**
 * The app's version name and version code from `git describe --tags --match "v[0-9]*" --long --dirty` (see
 * docs/ARCHITECTURE.md, "Versions"). Release tags are `vX.Y.Z`, release candidates `vX.Y.Z-rcN`.
 */
object VersionName {
    private val describe = Regex("""^v(\d+\.\d+\.\d+(?:-rc\d+)?)-(\d+)-g([0-9a-f]+)(-dirty)?$""")

    /**
     * Exactly on a tag its version (`1.0.0`, `1.0.0-rc1`); later commits `1.0.0+3 (abc1234)`;
     * local changes add ` dirty`. Null if [output] is not such a description (no release tag yet).
     */
    fun fromDescribe(output: String): String? {
        val (version, since, hash, dirty) = describe.find(output.trim())?.destructured ?: return null
        return when {
            since == "0" && dirty.isEmpty() -> version
            else -> "$version+$since ($hash)" + if (dirty.isEmpty()) "" else " dirty"
        }
    }

    /**
     * Version code of the last release tag in [output]: `MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + S`
     * with `S = N` for `vX.Y.Z-rcN` and `S = 99` for `vX.Y.Z`. Commits after the tag and local changes keep
     * the tag's code. Null if [output] is not such a description (no release tag yet).
     */
    fun codeFromDescribe(output: String): Int? {
        val version = describe.find(output.trim())?.groupValues?.get(1) ?: return null
        return code(version)
    }

    /** Version code of [version] (`1.0.0-rc2` → 1000002, `1.0.0` → 1000099, `1.2.3` → 1020399). */
    fun code(version: String): Int {
        val (major, minor, patch, rc) = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-rc(\d+))?$""").find(version)
            ?.destructured ?: throw IllegalArgumentException("VocTrainer: \"$version\" is no version X.Y.Z or X.Y.Z-rcN")
        require(minor.toInt() < 100 && patch.toInt() < 100) {
            "VocTrainer: version $version cannot get a version code, MINOR and PATCH must be below 100"
        }
        require(rc.isEmpty() || rc.toInt() < 99) {
            "VocTrainer: version $version cannot get a version code, the rc number must be below 99"
        }
        val suffix = if (rc.isEmpty()) 99 else rc.toInt()
        return major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + suffix
    }

    /** Without a release tag: `0.0.0+<commits> (<hash>)`, from [count] and `git describe --always --dirty`. */
    fun withoutTag(count: Int, hashAndDirty: String): String {
        val dirty = hashAndDirty.endsWith("-dirty")
        return "0.0.0+$count (${hashAndDirty.removeSuffix("-dirty")})" + if (dirty) " dirty" else ""
    }
}
