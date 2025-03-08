/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.hoststubgen

import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.dumper.ApiDumper
import com.android.hoststubgen.filters.FilterPolicy
import com.android.hoststubgen.filters.printAsTextPolicy
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Actual main class.
 */
class HostStubGen(val options: HostStubGenOptions) {
    fun run() {
        val errors = HostStubGenErrors()
        val stats = HostStubGenStats()

        // Load all classes.
        val allClasses = ClassNodes.loadClassStructures(options.inJar.get)

        // Dump the classes, if specified.
        options.inputJarDumpFile.ifSet {
            log.iTime("Dump file created at $it") {
                PrintWriter(it).use { pw -> allClasses.dump(pw) }
            }
        }

        options.inputJarAsKeepAllFile.ifSet {
            log.iTime("Dump file created at $it") {
                PrintWriter(it).use { pw ->
                    allClasses.forEach { classNode ->
                        printAsTextPolicy(pw, classNode)
                    }
                }
            }
        }

        // Build the class processor
        val processor = HostStubGenClassProcessor(options, allClasses, errors, stats)

        // Transform the jar.
        convert(
            options.inJar.get,
            options.outJar.get,
            processor,
            options.enableClassChecker.get,
            options.numShards.get,
            options.shard.get,
        )

        // Dump statistics, if specified.
        options.statsFile.ifSet {
            log.iTime("Dump file created at $it") {
                PrintWriter(it).use { pw -> stats.dumpOverview(pw) }
            }
        }
        options.apiListFile.ifSet {
            log.iTime("API list file created at $it") {
                PrintWriter(it).use { pw ->
                    // TODO, when dumping a jar that's not framework-minus-apex.jar, we need to feed
                    // framework-minus-apex.jar so that we can dump inherited methods from it.
                    ApiDumper(pw, allClasses, null, processor.filter).dump()
                }
            }
        }
    }

    /**
     * Convert a JAR file into "stub" and "impl" JAR files.
     */
    private fun convert(
        inJar: String,
        outJar: String?,
        processor: HostStubGenClassProcessor,
        enableChecker: Boolean,
        numShards: Int,
        shard: Int
    ) {
        log.i("Converting %s into %s ...", inJar, outJar)
        log.i("ASM CheckClassAdapter is %s", if (enableChecker) "enabled" else "disabled")

        log.iTime("Transforming jar") {
            var itemIndex = 0
            var numItemsProcessed = 0
            var numItems = -1 // == Unknown

            log.withIndent {
                // Open the input jar file and process each entry.
                ZipFile(inJar).use { inZip ->

                    numItems = inZip.size()
                    val shardStart = numItems * shard / numShards
                    val shardNextStart = numItems * (shard + 1) / numShards

                    maybeWithZipOutputStream(outJar) { outStream ->
                        val inEntries = inZip.entries()
                        while (inEntries.hasMoreElements()) {
                            val entry = inEntries.nextElement()
                            val inShard = (shardStart <= itemIndex)
                                    && (itemIndex < shardNextStart)
                            itemIndex++
                            if (!inShard) {
                                continue
                            }
                            convertSingleEntry(inZip, entry, outStream, processor)
                            numItemsProcessed++
                        }
                        log.i("Converted all entries.")
                    }
                    outJar?.let { log.i("Created: $it") }
                }
            }
            log.i("%d / %d item(s) processed.", numItemsProcessed, numItems)
        }
    }

    private fun <T> maybeWithZipOutputStream(filename: String?, block: (ZipOutputStream?) -> T): T {
        if (filename == null) {
            return block(null)
        }
        return ZipOutputStream(BufferedOutputStream(FileOutputStream(filename))).use(block)
    }

    /**
     * Convert a single ZIP entry, which may or may not be a class file.
     */
    private fun convertSingleEntry(
        inZip: ZipFile,
        entry: ZipEntry,
        outStream: ZipOutputStream?,
        processor: HostStubGenClassProcessor
    ) {
        log.d("Entry: %s", entry.name)
        log.withIndent {
            val name = entry.name

            // Just ignore all the directories. (TODO: make sure it's okay)
            if (name.endsWith("/")) {
                return
            }

            // If it's a class, convert it.
            if (name.endsWith(".class")) {
                processSingleClass(inZip, entry, outStream, processor)
                return
            }

            // Handle other file types...

            // - *.uau seems to contain hidden API information.
            // -  *_compat_config.xml is also about compat-framework.
            if (name.endsWith(".uau") || name.endsWith("_compat_config.xml")) {
                log.d("Not needed: %s", entry.name)
                return
            }

            // Unknown type, we just copy it to both output zip files.
            log.v("Copying: %s", entry.name)
            outStream?.let { copyZipEntry(inZip, entry, it) }
        }
    }

    /**
     * Copy a single ZIP entry to the output.
     */
    private fun copyZipEntry(
        inZip: ZipFile,
        entry: ZipEntry,
        out: ZipOutputStream,
    ) {
        // TODO: It seems like copying entries this way is _very_ slow,
        // even with out.setLevel(0). Look for other ways to do it.

        inZip.getInputStream(entry).use { ins ->
            // Copy unknown entries as is to the impl out. (but not to the stub out.)
            val outEntry = ZipEntry(entry.name)
            out.putNextEntry(outEntry)
            ins.transferTo(out)
            out.closeEntry()
        }
    }

    /**
     * Convert a single class.
     */
    private fun processSingleClass(
        inZip: ZipFile,
        entry: ZipEntry,
        outStream: ZipOutputStream?,
        processor: HostStubGenClassProcessor
    ) {
        val classInternalName = entry.name.replaceFirst("\\.class$".toRegex(), "")
        val classPolicy = processor.filter.getPolicyForClass(classInternalName)
        if (classPolicy.policy == FilterPolicy.Remove) {
            log.d("Removing class: %s %s", classInternalName, classPolicy)
            return
        }
        // If we're applying a remapper, we need to rename the file too.
        var newName = entry.name
        processor.remapper.mapType(classInternalName)?.let { remappedName ->
            if (remappedName != classInternalName) {
                log.d("Renaming class file: %s -> %s", classInternalName, remappedName)
                newName = "$remappedName.class"
            }
        }

        if (outStream != null) {
            log.v("Creating class: %s Policy: %s", classInternalName, classPolicy)
            log.withIndent {
                BufferedInputStream(inZip.getInputStream(entry)).use { bis ->
                    val newEntry = ZipEntry(newName)
                    outStream.putNextEntry(newEntry)
                    val classBytecode = bis.readAllBytes()
                    outStream.write(processor.processClassBytecode(classBytecode))
                    outStream.closeEntry()
                }
            }
        }
    }
}
