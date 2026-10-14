package dev.demo.core.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class BoundedResponseCaptureTest {

  @Test
  void doesNotBufferFullBodyAndKeepsResponseBytesIntact() throws Exception {
    var response = new MockHttpServletResponse();
    var wrapper = new BoundedResponseCapture(response, 4);
    byte[] data = "123456789".getBytes(StandardCharsets.UTF_8);
    wrapper.getOutputStream().write(data);

    assertArrayEquals(data, response.getContentAsByteArray());
    assertArrayEquals("1234".getBytes(StandardCharsets.UTF_8), wrapper.capturedBytes());
    assertTrue(wrapper.isTruncated());
  }
}
