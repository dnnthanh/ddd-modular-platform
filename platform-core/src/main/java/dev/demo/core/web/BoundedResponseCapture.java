package dev.demo.core.web;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Objects;

/** Writes directly to the real response and retains only a bounded preview. */
final class BoundedResponseCapture extends HttpServletResponseWrapper {

  private final ByteArrayOutputStream preview;
  private final int limit;
  private ServletOutputStream outputStream;
  private PrintWriter writer;
  private boolean outputStreamAccessed;
  private boolean writerAccessed;
  private boolean truncated;

  BoundedResponseCapture(HttpServletResponse response, int limit) {
    super(response);
    this.limit = Math.max(1, limit);
    this.preview = new ByteArrayOutputStream(Math.min(this.limit, 1024));
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if (writerAccessed) {
      throw new IllegalStateException("getWriter() has already been called");
    }
    outputStreamAccessed = true;
    if (Objects.isNull(outputStream)) {
      var delegate = super.getOutputStream();
      outputStream =
          new ServletOutputStream() {
            @Override
            public void write(int value) throws IOException {
              delegate.write(value);
              capture(value);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
              delegate.write(bytes, offset, length);
              capture(bytes, offset, length);
            }

            @Override
            public boolean isReady() {
              return delegate.isReady();
            }

            @Override
            public void setWriteListener(WriteListener listener) {
              delegate.setWriteListener(listener);
            }

            @Override
            public void flush() throws IOException {
              delegate.flush();
            }
          };
    }
    return outputStream;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (outputStreamAccessed && !writerAccessed) {
      throw new IllegalStateException("getOutputStream() has already been called");
    }
    if (Objects.isNull(writer)) {
      // Do not call getOutputStream() here because the caller uses the Writer API.
      var delegate = super.getOutputStream();
      var tee =
          new ServletOutputStream() {
            @Override
            public void write(int value) throws IOException {
              delegate.write(value);
              capture(value);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
              delegate.write(bytes, offset, length);
              capture(bytes, offset, length);
            }

            @Override
            public boolean isReady() {
              return delegate.isReady();
            }

            @Override
            public void setWriteListener(WriteListener listener) {
              delegate.setWriteListener(listener);
            }
          };
      writer =
          new PrintWriter(
              new OutputStreamWriter(tee, Charset.forName(getCharacterEncoding())), false);
    }
    writerAccessed = true;
    return writer;
  }

  @Override
  public void flushBuffer() throws IOException {
    flushWriter();
    super.flushBuffer();
  }

  void flushWriter() {
    if (Objects.nonNull(writer)) {
      writer.flush();
    }
  }

  byte[] capturedBytes() {
    return preview.toByteArray();
  }

  boolean isTruncated() {
    return truncated;
  }

  private void capture(int value) {
    if (preview.size() < limit) {
      preview.write(value);
    } else {
      truncated = true;
    }
  }

  private void capture(byte[] bytes, int offset, int length) {
    int remaining = limit - preview.size();
    if (remaining > 0) {
      preview.write(bytes, offset, Math.min(remaining, length));
    }
    if (length > remaining) {
      truncated = true;
    }
  }
}
