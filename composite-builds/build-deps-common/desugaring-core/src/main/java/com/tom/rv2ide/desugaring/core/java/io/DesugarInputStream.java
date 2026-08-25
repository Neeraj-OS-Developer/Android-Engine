/*
 * This file is part of AndroidIDE.
 * Modified by Neeraj-OS-Developer.
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.desugaring.core.java.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings("unused")
public final class DesugarInputStream {

    private static final int DEFAULT_BUFFER_SIZE = 16384;
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

    private DesugarInputStream() {
        throw new UnsupportedOperationException();
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        return readNBytes(in, Integer.MAX_VALUE);
    }

    /**
     * Optimized version: single dynamically growing byte array.
     * Replaces List<byte[]> + double copying with amortized O(n) resizing.
     */
    public static byte[] readNBytes(InputStream in, int len) throws IOException {
        Objects.requireNonNull(in, "in");
        if (len < 0) throw new IllegalArgumentException("len < 0");
        if (len == 0) return new byte[0];

        int initialCapacity = Math.min(len, DEFAULT_BUFFER_SIZE);
        byte[] buf = new byte[initialCapacity];
        int total = 0;

        while (total < len) {
            if (total == buf.length) {
                // Grow buffer, safely handling overflow
                int newCapacity = buf.length << 1;
                if (newCapacity < 0 || newCapacity > MAX_BUFFER_SIZE) {
                    newCapacity = MAX_BUFFER_SIZE;
                }
                if (newCapacity > len) {
                    newCapacity = len;
                }
                buf = Arrays.copyOf(buf, newCapacity);
            }
            int remaining = len - total;
            int toRead = Math.min(buf.length - total, remaining);
            int n = in.read(buf, total, toRead);
            if (n < 0) break;
            total += n;
        }

        return (total == buf.length) ? buf : Arrays.copyOf(buf, total);
    }

    public static int readNBytes(InputStream input, byte[] b, int off, int len) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.checkFromIndexSize(off, len, b.length);
        int n = 0;
        while (n < len) {
            int count = input.read(b, off + n, len - n);
            if (count < 0) break;
            n += count;
        }
        return n;
    }

    public static long transferTo(InputStream in, OutputStream out) throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        long transferred = 0;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
            transferred += read;
        }
        return transferred;
    }
}
