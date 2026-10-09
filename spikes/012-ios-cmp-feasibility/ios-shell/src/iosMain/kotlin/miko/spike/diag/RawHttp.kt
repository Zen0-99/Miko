package miko.spike.diag

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.close
import platform.posix.connect
import platform.posix.freeaddrinfo
import platform.posix.AI_NUMERICHOST
import platform.posix.getaddrinfo
import platform.posix.memset
import platform.posix.recv
import platform.posix.send
import platform.posix.socket

/**
 * Plain-HTTP GET over a raw BSD socket — no NSURLSession, no bundle context.
 * Isolation control: if this works while ktor-darwin fails, the failure is
 * in NSURLSession-under-test-harness, not in the simulator's network stack.
 */
@OptIn(ExperimentalForeignApi::class)
fun rawHttpGet(host: String, path: String): String = memScoped {
    val res = alloc<kotlinx.cinterop.CPointerVar<addrinfo>>()
    // null hints — simplest possible resolution
    if (getaddrinfo(host, "80", null, res.ptr) != 0) {
        return@memScoped "FAILED: getaddrinfo(null-hints)"
    }
    val addr = res.value ?: return@memScoped "FAILED: no addrinfo"

    val sock = socket(addr.pointed.ai_family, addr.pointed.ai_socktype, addr.pointed.ai_protocol)
    if (sock < 0) return@memScoped "FAILED: socket"
    if (connect(sock, addr.pointed.ai_addr, addr.pointed.ai_addrlen) != 0) {
        close(sock)
        freeaddrinfo(addr)
        return@memScoped "FAILED: connect"
    }
    freeaddrinfo(addr)

    val request = "GET $path HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n"
    request.encodeToByteArray().usePinned { pinned ->
        send(sock, pinned.addressOf(0), request.length.toULong(), 0)
    }

    val buf = allocArray<ByteVar>(4096)
    val n = recv(sock, buf, 4095u, 0)
    close(sock)
    if (n <= 0) return@memScoped "FAILED: recv=$n"
    buf[n.toInt()] = 0
    buf.toKString()
}

/** getaddrinfo only — does DNS resolution work at all? */
@OptIn(ExperimentalForeignApi::class)
fun dnsProbe(host: String): String = memScoped {
    val hints = alloc<addrinfo>()
    memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
    hints.ai_family = AF_INET
    hints.ai_socktype = SOCK_STREAM
    val res = alloc<kotlinx.cinterop.CPointerVar<addrinfo>>()
    val rc = getaddrinfo(host, "80", hints.ptr, res.ptr)
    if (rc == 0) {
        freeaddrinfo(res.value)
        "ok"
    } else {
        "getaddrinfo rc=$rc"
    }
}

/** TCP connect to an IP literal — skips DNS entirely via AI_NUMERICHOST. */
@OptIn(ExperimentalForeignApi::class)
fun tcpProbe(ip: String, port: String): String = memScoped {
    val hints = alloc<addrinfo>()
    memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
    hints.ai_family = AF_INET
    hints.ai_socktype = SOCK_STREAM
    hints.ai_flags = AI_NUMERICHOST
    val res = alloc<kotlinx.cinterop.CPointerVar<addrinfo>>()
    if (getaddrinfo(ip, port, hints.ptr, res.ptr) != 0) {
        return@memScoped "FAILED: getaddrinfo(numeric)"
    }
    val addr = res.value ?: return@memScoped "FAILED: no addrinfo"
    val sock = socket(addr.pointed.ai_family, addr.pointed.ai_socktype, addr.pointed.ai_protocol)
    if (sock < 0) return@memScoped "FAILED: socket"
    val rc = connect(sock, addr.pointed.ai_addr, addr.pointed.ai_addrlen)
    close(sock)
    freeaddrinfo(addr)
    if (rc == 0) "connected" else "connect rc=$rc"
}
