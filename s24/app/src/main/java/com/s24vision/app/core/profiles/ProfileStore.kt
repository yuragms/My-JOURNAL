package com.s24vision.app.core.profiles

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Хранит профили в `<root>/profiles/<type.dir>/<name>.bin`.
 * Формат файла (float32 big-endian через DataOutputStream):
 * `[count:int][dim:int][values: count*dim float]`.
 */
class ProfileStore(root: File) {

    private val base = File(root, "profiles").apply { mkdirs() }

    private fun file(type: ProfileType, name: String) =
        File(File(base, type.dir).apply { mkdirs() }, "$name.bin")

    fun exists(type: ProfileType, name: String): Boolean = file(type, name).exists()

    fun load(type: ProfileType, name: String): Profile? {
        val f = file(type, name)
        if (!f.exists()) return null
        DataInputStream(f.inputStream().buffered()).use { ins ->
            val count = ins.readInt()
            val dim = ins.readInt()
            val list = ArrayList<FloatArray>(count)
            repeat(count) {
                val a = FloatArray(dim) { ins.readFloat() }
                list.add(a)
            }
            return Profile(type, name, list)
        }
    }

    private fun save(p: Profile) {
        val f = file(p.type, p.name)
        val dim = p.embeddings.firstOrNull()?.size ?: 0
        DataOutputStream(f.outputStream().buffered()).use { out ->
            out.writeInt(p.embeddings.size)
            out.writeInt(dim)
            p.embeddings.forEach { v -> v.forEach(out::writeFloat) }
        }
    }

    /** Создаёт новый профиль. Не перезаписывает существующий. */
    fun createEmbeddings(type: ProfileType, name: String, embs: List<FloatArray>): EnrollResult {
        if (embs.isEmpty()) return EnrollResult.Empty("нет эмбеддингов")
        if (exists(type, name)) return EnrollResult.AlreadyExists(name)
        save(Profile(type, name, embs.toMutableList()))
        return EnrollResult.Created(embs.size)
    }

    /** Добавляет эмбеддинги в уже существующий профиль. */
    fun improveEmbeddings(type: ProfileType, name: String, embs: List<FloatArray>): EnrollResult {
        if (embs.isEmpty()) return EnrollResult.Empty("нет эмбеддингов")
        val existing = load(type, name) ?: return EnrollResult.NotFound(name)
        val before = existing.embeddings.size
        existing.embeddings.addAll(embs)
        save(existing)
        return EnrollResult.Improved(before, existing.embeddings.size)
    }

    /** @deprecated Авто-режим: создать или дополнить. Используйте create/improve явно. */
    fun addEmbeddings(
        type: ProfileType,
        name: String,
        embs: List<FloatArray>,
    ): EnrollResult {
        if (embs.isEmpty()) return EnrollResult.Empty("нет эмбеддингов")
        return if (exists(type, name)) improveEmbeddings(type, name, embs)
        else createEmbeddings(type, name, embs)
    }

    fun list(type: ProfileType): List<String> =
        File(base, type.dir).listFiles()
            ?.filter { it.extension == "bin" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    fun delete(type: ProfileType, name: String): Boolean = file(type, name).delete()

    fun fileSizeBytes(type: ProfileType, name: String): Long =
        file(type, name).takeIf { it.exists() }?.length() ?: 0L

    fun sampleCount(type: ProfileType, name: String): Int =
        load(type, name)?.embeddings?.size ?: 0
}
