package com.kuromh.controller.core

import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipFile

/**
 * Mengunduh modul Kuro Mirror High dari Supabase Storage (bucket public "modules").
 *
 * Zip hanya dipakai jika tanda tangan ECDSA P-256 (kuro_mirror_high.zip.sig) valid terhadap
 * PUBLIC_KEY_B64 yang ditanam di APK, dan versionCode di module.prop tidak lebih rendah dari
 * MIN_VERSION_CODE (mencegah zip lama yang sah dipasang ulang). Selain itu dianggap gagal dan
 * pemanggil memakai zip bawaan di assets. Panggil dari background thread.
 */
object RemoteModule {
    private const val BASE_URL =
        "https://pwpwtnswrmacosnsxsoa.supabase.co/storage/v1/object/public/modules"
    private const val ZIP_NAME = "kuro_mirror_high.zip"
    private const val SIG_NAME = "kuro_mirror_high.zip.sig"

    /** Isi dari `./sign-module.sh init` (public key, base64). Selama belum diganti, unduhan selalu ditolak. */
    private const val PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECRTdnUMDpQGZF2Meh/23fw3XXbSkLZdzqIxzdLL7XDmsSGvJODV2o6Udn5WIPA5++SBYNypkKVt21RvWUfHmJw=="

    /** Naikkan setiap kali zip bawaan di assets diganti ke versionCode yang lebih baru. */
    private const val MIN_VERSION_CODE = 4

    private const val TIMEOUT_MS = 15_000
    private const val MAX_BYTES = 20L * 1024 * 1024

    /** True jika zip berhasil diunduh ke [dest], tanda tangannya valid, dan versinya tidak turun. */
    fun download(dest: File): Boolean {
        return try {
            val t = System.currentTimeMillis() // query unik supaya CDN tidak mengirim file lama
            val sig = Base64.decode(fetchText("$BASE_URL/$SIG_NAME?t=$t").trim(), Base64.DEFAULT)
            fetchFile("$BASE_URL/$ZIP_NAME?t=$t", dest)
            if (verify(dest, sig) && versionCode(dest) >= MIN_VERSION_CODE) {
                true
            } else {
                dest.delete()
                false
            }
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    private fun verify(file: File, sig: ByteArray): Boolean {
        val keyBytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        val s = Signature.getInstance("SHA256withECDSA")
        s.initVerify(key)
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                s.update(buf, 0, n)
            }
        }
        return s.verify(sig)
    }

    /** versionCode dari module.prop di dalam zip (isi zip sudah terjamin oleh tanda tangan). */
    private fun versionCode(zip: File): Int = ZipFile(zip).use { z ->
        val entry = z.getEntry("module.prop") ?: return@use 0
        z.getInputStream(entry).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .firstOrNull { it.startsWith("versionCode=") }
                ?.removePrefix("versionCode=")?.trim()?.toIntOrNull() ?: 0
        }
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        val code = c.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect()
            throw IllegalStateException("HTTP $code")
        }
        return c
    }

    private fun fetchText(url: String): String {
        val c = open(url)
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun fetchFile(url: String, dest: File) {
        val c = open(url)
        try {
            var total = 0L
            c.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IllegalStateException("File too large")
                        output.write(buf, 0, n)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}
