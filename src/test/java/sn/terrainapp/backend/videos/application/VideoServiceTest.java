package sn.terrainapp.backend.videos.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VideoServiceTest {

    @Test
    void reconnaitLeFormatALaSignature() {
        byte[] mp4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        byte[] webm = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(VideoService.extension(mp4)).isEqualTo("mp4");
        assertThat(VideoService.extension(webm)).isEqualTo("webm");
        assertThat(VideoService.extension(png)).isNull();
        assertThat(VideoService.extension(new byte[3])).isNull();
    }
}
