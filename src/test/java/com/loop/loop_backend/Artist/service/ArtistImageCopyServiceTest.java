package com.loop.loop_backend.Artist.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistImageCopyServiceTest {

    @Test
    void 내부망_루프백_메타데이터_주소는_차단한다() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.0.1",
                "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1"}) {
            assertThat(ArtistImageCopyService.isInternal(InetAddress.getByName(ip))).as(ip).isTrue();
        }
        assertThat(ArtistImageCopyService.isInternal(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThatThrownBy(() -> ArtistImageCopyService.checkPublicHost(URI.create("http://169.254.169.254/latest/meta-data")))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ArtistImageCopyService.checkPublicHost(URI.create("file:///etc/passwd")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void 이미지_형식은_헤더가_아닌_파일_앞부분으로_판별하고_jpg_png_webp만_받는다() throws Exception {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};
        assertThat(ArtistImageCopyService.detectImage(jpg).extension()).isEqualTo("jpg");
        assertThat(ArtistImageCopyService.detectImage(png).extension()).isEqualTo("png");
        assertThat(ArtistImageCopyService.detectImage(webp).extension()).isEqualTo("webp");
        assertThatThrownBy(() -> ArtistImageCopyService.detectImage("<svg onload=alert(1)>".getBytes()))
                .isInstanceOf(IOException.class);
    }
}
