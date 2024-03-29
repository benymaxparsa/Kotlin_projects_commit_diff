package shark

// TODO Reevaluate name. Maybe not "Reader"? Something about navigating?
fun interface ReferenceReader<T : HeapObject> {

  /**
   * Returns the sequences of non null outgoing references from [source]. Outgoing refs
   * can be actual JVM references or they can be virtual references when simplifying known data
   * structures.
   *
   * Whenever possible, the returned sequence should be sorted in a way that ensures consistent
   * graph traversal across heap dumps.
   *
   * The returned sequence may contain several [Reference] with an identical
   * [Reference.valueObjectId].
   */
  fun read(source: T): Sequence<Reference>

  fun interface Factory<T : HeapObject> {
    fun createFor(heapGraph: HeapGraph): ReferenceReader<T>
  }
}
