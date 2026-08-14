package com.fowoco.server.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAgentDeviceSummarizerTest {

    @Test
    void recognizesChromeOnMac() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
        assertThat(UserAgentDeviceSummarizer.summarize(ua)).isEqualTo("Chrome · macOS");
    }

    @Test
    void recognizesSafariOnIphone() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
        assertThat(UserAgentDeviceSummarizer.summarize(ua)).isEqualTo("Safari · iOS");
    }

    @Test
    void recognizesEdgeOnWindowsAndDoesNotMisreadItAsChrome() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0";
        assertThat(UserAgentDeviceSummarizer.summarize(ua)).isEqualTo("Edge · Windows");
    }

    @Test
    void recognizesFirefoxOnLinux() {
        String ua = "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0";
        assertThat(UserAgentDeviceSummarizer.summarize(ua)).isEqualTo("Firefox · Linux");
    }

    @Test
    void fallsBackToUnknownWhenBlank() {
        assertThat(UserAgentDeviceSummarizer.summarize(null)).isEqualTo("알 수 없는 기기");
        assertThat(UserAgentDeviceSummarizer.summarize("  ")).isEqualTo("알 수 없는 기기");
    }

    @Test
    void fallsBackToUnknownWhenUnrecognized() {
        assertThat(UserAgentDeviceSummarizer.summarize("curl/8.4.0")).isEqualTo("알 수 없는 기기");
    }
}
