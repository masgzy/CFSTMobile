package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.GithubClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** GitHub 上传纯函数回归网:路径清洗、raw 直链、与 CFData 对齐的默认值 */
class GithubClientTest {

    @Test
    fun `path trims slashes and keeps hierarchy`() {
        assertEquals("results/cfst.txt", GithubClient.normalizePath("/results/cfst.txt/"))
        assertEquals("cfst-results.txt", GithubClient.normalizePath("cfst-results.txt"))
    }

    @Test
    fun `path escapes segments but not separators`() {
        assertEquals("my%20dir/cfst.txt", GithubClient.normalizePath("my dir/cfst.txt"))
    }

    @Test
    fun `empty path stays empty`() {
        assertEquals("", GithubClient.normalizePath("  /  "))
    }

    @Test
    fun `raw url follows cfdata structure`() {
        assertEquals(
            "https://raw.githubusercontent.com/masgzy/results/refs/heads/main/cfst-results.txt",
            GithubClient.rawUrl("masgzy", "results", "main", "cfst-results.txt"),
        )
    }

    @Test
    fun `defaults match cfdata vocabulary`() {
        assertEquals("main", GithubClient.DEFAULT_BRANCH)
        assertEquals("update cfst results", GithubClient.DEFAULT_MESSAGE)
        assertEquals("cfst-results.txt", GithubClient.DEFAULT_PATH)
    }
}
