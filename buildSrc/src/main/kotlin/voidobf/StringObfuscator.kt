package voidobf

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Build-time string encryptor.
 *
 * Runs AFTER ProGuard (so it operates on the already-renamed/repackaged classes) and
 * BEFORE the integrity signing (so the signed hash covers the encrypted output).
 *
 * For every `LDC <String>` instruction inside the matched (proprietary) classes it
 * XOR-encrypts the constant and inserts a call to the runtime decryptor. The XOR is the
 * exact mirror of `ac.voidac.internal.StringDecryptor#d` so decryption is lossless.
 *
 * Shaded libraries, public API classes and the decryptor itself are never touched, so
 * reflection / event / command wiring keeps working.
 */
object StringObfuscator {

    /** Must stay identical to StringDecryptor#d (symmetric XOR). */
    private fun enc(s: String): String {
        val c = s.toCharArray()
        for (i in c.indices) {
            c[i] = (c[i].code xor ((0x5A3C7 + i * 31) and 0xFFFF)).toChar()
        }
        return String(c)
    }

    data class Result(val classesTouched: Int, val stringsEncrypted: Int, val decryptor: String)

    /**
     * Resolves the runtime (post-ProGuard) internal name of the decryptor class from the
     * ProGuard mapping file. Returns null if not found.
     */
    fun resolveDecryptor(mappingFile: File, originalFqcn: String = "ac.voidac.internal.StringDecryptor"): String? {
        if (!mappingFile.exists()) return null
        val prefix = "$originalFqcn -> "
        mappingFile.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith(prefix) && line.trimEnd().endsWith(":")) {
                    return line.substring(prefix.length).trimEnd().removeSuffix(":").replace('.', '/')
                }
            }
        }
        return null
    }

    /**
     * @param includePrefixes internal-name prefixes (e.g. "Il/lI/IlI/") whose classes get encrypted.
     * @param excludePrefixes  internal-name prefixes never touched (e.g. "ac/voidac/shaded/").
     */
    fun encryptJar(
        input: File,
        output: File,
        decryptorInternalName: String,
        decryptorMethod: String,
        includePrefixes: List<String>,
        excludePrefixes: List<String>,
    ): Result {
        var classesTouched = 0
        var stringsEncrypted = 0

        JarFile(input).use { jar ->
            JarOutputStream(output.outputStream().buffered()).use { out ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }

                    val transformed = if (
                        name.endsWith(".class") &&
                        includePrefixes.any { name.startsWith(it) } &&
                        excludePrefixes.none { name.startsWith(it) } &&
                        name != "$decryptorInternalName.class"
                    ) {
                        val (out2, n) = transformClass(bytes, decryptorInternalName, decryptorMethod)
                        if (n > 0) { classesTouched++; stringsEncrypted += n }
                        out2
                    } else {
                        bytes
                    }

                    // Re-create entry without compression metadata carry-over issues
                    out.putNextEntry(JarEntry(name))
                    out.write(transformed)
                    out.closeEntry()
                }
            }
        }
        return Result(classesTouched, stringsEncrypted, decryptorInternalName)
    }

    private fun transformClass(bytes: ByteArray, decOwner: String, decMethod: String): Pair<ByteArray, Int> {
        val cr = ClassReader(bytes)
        val node = ClassNode()
        cr.accept(node, 0)

        var count = 0
        for (method in node.methods) {
            val insns = method.instructions ?: continue
            val it = insns.iterator()
            while (it.hasNext()) {
                val insn = it.next()
                if (insn is LdcInsnNode) {
                    val cst = insn.cst
                    if (cst is String && cst.isNotEmpty()) {
                        insn.cst = enc(cst)
                        insns.insert(
                            insn,
                            MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                decOwner,
                                decMethod,
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                false,
                            ),
                        )
                        count++
                    }
                }
            }
        }

        if (count == 0) return bytes to 0

        // COMPUTE_MAXS only: stack/locals are recomputed, existing frames stay valid
        // because we never change the type on the stack (String in -> String out).
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        node.accept(cw)
        return cw.toByteArray() to count
    }
}
