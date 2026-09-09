package com.tendril.app.sync

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * §9.4 — carry forward the fields a newer build wrote and this one has no member for.
 *
 * **The failure this exists to stop.** `SnapshotSyncOrchestrator`'s `Json` is
 * `ignoreUnknownKeys = true` and every `@Serializable` record here is a plain data class with no
 * catch-all, so a record from a newer build decodes with its extra field silently discarded, is
 * adopted into Room, and is then re-encoded **from Room rows** on the next write pass — going back
 * to the folder without the field, for every device to adopt in turn. The asymmetry is what makes
 * it worth this much code: a record this build *cannot* read is the safe case, because quarantine
 * holds it as a raw `JsonElement` and republishes it byte-faithfully. It is precisely the records
 * understood *well enough to adopt* that lose data, so the failure gets quieter as two builds grow
 * closer together, and no unreadable value exists anywhere for a guard to trip on.
 *
 * **Why the rule is "not declared" and not "not present".** kotlinx.serialization omits a field
 * equal to its default, so a `deletedAt` this build has legitimately set back to null is simply
 * absent from [current]. Copying back every key [previous] has that [current] lacks would
 * resurrect it — turning a preservation fix into a corruption bug. Only keys the schema does not
 * *declare* are carried over: a declared-but-absent key means this build had an opinion and its
 * opinion was "no value", while an undeclared key means it never had one.
 *
 * **List elements are matched by `uid`, or not at all.** Every collection whose members carry
 * cross-device identity — blocks, properties, views, canvas nodes — is matched on it. The three
 * that do not ([FormattingSpanSnapshot], [ViewFilterSnapshot], [CanvasEdgeSnapshotRecord]) are
 * left untouched rather than matched by position: a list that gained, lost or reordered an element
 * between the two writes would then graft a field onto the wrong object, which is a worse outcome
 * than the field being dropped. They are small positional value types, and the trade is stated
 * here rather than discovered later.
 *
 * Pure and total: unknown shapes, type changes and a null [previous] all fall through to
 * [current] unchanged. It can add keys to an object; it can never remove or alter one.
 */
@OptIn(ExperimentalSerializationApi::class)
fun mergeUnknownFields(
    current: JsonElement,
    previous: JsonElement?,
    descriptor: SerialDescriptor,
): JsonElement {
    if (previous == null) return current
    return when {
        current is JsonObject && previous is JsonObject && descriptor.kind == StructureKind.CLASS ->
            mergeObject(current, previous, descriptor)
        current is JsonArray && previous is JsonArray && descriptor.kind == StructureKind.LIST ->
            mergeList(current, previous, descriptor)
        // A primitive, or a shape that changed between the two writes. Nothing to carry, and
        // guessing across a type change is how a "safe" merge corrupts a record.
        else -> current
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun mergeObject(
    current: JsonObject,
    previous: JsonObject,
    descriptor: SerialDescriptor,
): JsonObject {
    val declared = buildMap {
        for (i in 0 until descriptor.elementsCount) put(descriptor.getElementName(i), i)
    }
    val merged = current.toMutableMap()
    for ((key, value) in previous) {
        val index = declared[key]
        if (index == null) {
            // Undeclared: this build has no member for it. Carry it, unless this build somehow
            // wrote the key anyway -- in which case its value is the current one by definition.
            if (key !in merged) merged[key] = value
        } else {
            // Declared: recurse, so a field nested inside a known object is reached too. The
            // value itself is never replaced -- only its unknown descendants are added to.
            val mine = current[key] ?: continue
            merged[key] = mergeUnknownFields(mine, value, descriptor.getElementDescriptor(index))
        }
    }
    return JsonObject(merged)
}

@OptIn(ExperimentalSerializationApi::class)
private fun mergeList(
    current: JsonArray,
    previous: JsonArray,
    descriptor: SerialDescriptor,
): JsonArray {
    val element = descriptor.getElementDescriptor(0)
    if (element.kind != StructureKind.CLASS || !element.hasUid()) return current
    val byUid = previous.mapNotNull { item ->
        (item as? JsonObject)?.uid()?.let { it to item }
    }.toMap()
    if (byUid.isEmpty()) return current
    return JsonArray(
        current.map { item ->
            val uid = (item as? JsonObject)?.uid()
            val match = uid?.let(byUid::get)
            if (item is JsonObject && match != null) mergeObject(item, match, element) else item
        }
    )
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.hasUid(): Boolean =
    (0 until elementsCount).any { getElementName(it) == UID_KEY }

private fun JsonObject.uid(): String? = (this[UID_KEY] as? JsonPrimitive)?.contentOrNull

private const val UID_KEY = "uid"
