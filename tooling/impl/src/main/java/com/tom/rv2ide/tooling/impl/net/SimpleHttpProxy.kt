/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.tooling.impl.net

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/*
 * Minimal HTTP/HTTPS CONNECT proxy for AndroidCodeStudio Gradle traffic.
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class SimpleHttpProxy(private val listenPort: Int = 0) {
  @Volatile private var serverSocket: ServerSocket? = null
  private val executor = Executors.newCachedThreadPool()

  val port: Int
    get() = serverSocket?.localPort ?: -1

  @Synchronized
  fun start() {
    if (serverSocket != null) return
    serverSocket =
        ServerSocket().apply {
          reuseAddress = true
          bind(InetSocketAddress("127.0.0.1", listenPort))
        }
    executor.execute {
      val socket = serverSocket // Capture reference to avoid race condition
      while (socket != null && !socket.isClosed) {
        try {
          val client = socket.accept()
          executor.execute { handle(client) }
        } catch (_: Throwable) {
          // break // Exit on any exception (including when socket is closed)
        }
      }
    }
  }

  @Synchronized
  fun stop() {
    try {
      serverSocket?.close()
    } catch (_: Throwable) {}
    serverSocket = null
  }

  private fun handle(socket: Socket) {
    socket.soTimeout = 1800000
    socket.tcpNoDelay = true
    BufferedInputStream(socket.getInputStream()).use { input ->
      BufferedOutputStream(socket.getOutputStream()).use { output ->
        val firstLine = readLine(input) ?: return
        val tokens = firstLine.split(" ")
        if (tokens.size < 2) return
        val method = tokens[0]
        val target = tokens[1]
        if (method.equals("CONNECT", ignoreCase = true)) {
          // HTTPS tunnel
          val hostPort = target.split(":")
          val host = hostPort[0]
          val port = if (hostPort.size > 1) hostPort[1].toInt() else 443
          // Establish upstream tunnel
          val upstream = Socket(host, port)
          output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
          output.flush()
          relay(socket, upstream)
        } else {
          // Only HTTPS tunneling supported
          output.write("HTTP/1.1 501 Not Implemented\r\nContent-Length: 0\r\n\r\n".toByteArray())
          output.flush()
        }
      }
    }
  }

  private fun relay(a: Socket, b: Socket) {
    val t1 = Thread { pump(a.getInputStream(), b.getOutputStream()) }
    val t2 = Thread { pump(b.getInputStream(), a.getOutputStream()) }
    t1.start()
    t2.start()
    try {
      t1.join()
    } catch (_: InterruptedException) {}
    try {
      t2.join()
    } catch (_: InterruptedException) {}
    try {
      a.close()
    } catch (_: Throwable) {}
    try {
      b.close()
    } catch (_: Throwable) {}
  }

  private fun pump(inp: InputStream, out: OutputStream) {
    val buf = ByteArray(16 * 1024)
    try {
      var n = inp.read(buf)
      while (n > 0) {
        out.write(buf, 0, n)
        out.flush()
        n = inp.read(buf)
      }
    } catch (_: Throwable) {}
  }

  private fun readLine(input: BufferedInputStream): String? {
    val sb = StringBuilder()
    while (true) {
      val c = input.read()
      if (c == -1) return if (sb.isEmpty()) null else sb.toString()
      if (c == '\r'.code) {
        val next = input.read() // consume \n
        return sb.toString()
      }
      sb.append(c.toChar())
    }
  }
}
