@file:Suppress("NOTHING_TO_INLINE", "unused")

package net.mamoe.mirai.utils

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

fun <E> LockFreeLinkedList<E>.joinToString(
    separator: CharSequence = ", ",
    prefix: CharSequence = "[",
    postfix: CharSequence = "]",
    transform: ((E) -> CharSequence)? = null
): String = prefix.toString() + buildString {
    this@joinToString.forEach {
        if (transform != null) {
            append(transform(it))
        } else append(it)
        append(separator)
    }
}.dropLast(2) + postfix

/**
 * Returns a [List] containing all the elements in [this] in the same order
 */
fun <E> LockFreeLinkedList<E>.toList(): List<E> = toMutableList()

/**
 * Returns a [MutableList] containing all the elements in [this] in the same order
 */
fun <E> LockFreeLinkedList<E>.toMutableList(): MutableList<E> {
    val list = mutableListOf<E>()
    this.forEach { list.add(it) }
    return list
}

/**
 * Implementation of lock-free LinkedList.
 *
 * Modifying can be performed concurrently.
 * Iterating concurrency is guaranteed.
 */
open class LockFreeLinkedList<E> {
    @PublishedApi
    internal val tail: Tail<E> = Tail()

    @PublishedApi
    internal val head: Head<E> = Head(tail)

    fun removeFirst(): E {
        while (true) {
            val currentFirst = head.nextNode
            if (!currentFirst.isValidElementNode()) {
                throw NoSuchElementException()
            }
            if (head.compareAndSetNextNodeRef(currentFirst, currentFirst.nextNode)) {
                return currentFirst.nodeValue
            }
        }
    }

    fun peekFirst(): E = head.nextNode.letValueIfValid { return it } ?: throw NoSuchElementException()

    fun peekLast(): E = head.iterateBeforeFirst { it === tail }.letValueIfValid { return it } ?: throw NoSuchElementException()

    fun removeLast(): E {
        while (true) {
            val beforeLast = head.iterateBeforeFirst { it.nextNode === tail }
            if (!beforeLast.isValidElementNode()) {
                throw NoSuchElementException()
            }
            val last = beforeLast.nextNode
            if (beforeLast.nextNodeRef.compareAndSet(last, last.nextNode)) {
                return last.nodeValue
            }
        }
    }

    fun addLast(element: E) {
        val node = element.asNode(tail)

        while (true) {
            val tail = head.iterateBeforeFirst { it === tail } // find the last node.
            if (tail.nextNodeRef.compareAndSet(this.tail, node)) { // ensure the last node is the last node
                return
            }
        }
    }

    operator fun plusAssign(element: E) = this.addLast(element)

    inline fun filteringGetOrAdd(filter: (E) -> Boolean, noinline supplier: () -> E): E {
        val node = LazyNode(tail, supplier)

        while (true) {
            var current: Node<E> = head

            findLastNode@ while (true) {
                if (current.isValidElementNode() && filter(current.nodeValue))
                    return current.nodeValue

                if (current.nextNode === tail) {
                    if (current.compareAndSetNextNodeRef(tail, node)) { // ensure only one attempt can put the lazyNode in
                        return node.nodeValue
                    }
                }

                current = current.nextNode
            }
        }
    }

    @PublishedApi // limitation by atomicfu
    internal fun <E> Node<E>.compareAndSetNextNodeRef(expect: Node<E>, update: Node<E>) = this.nextNodeRef.compareAndSet(expect, update)

    override fun toString(): String = joinToString()

    @Suppress("unused")
    internal fun getLinkStructure(): String = buildString {
        head.childIterateReturnsLastSatisfying<Node<*>>({
            append(it.toString())
            append(" <- ")
            it.nextNode
        }, { it !is Tail })
    }.dropLast(4)

    fun remove(element: E): Boolean {
        while (true) {
            val before = head.iterateBeforeNodeValue(element)
            val toRemove = before.nextNode
            if (toRemove === tail) {
                return false
            }
            if (toRemove.isRemoved()) {
                continue
            }
            toRemove.removed.value = true // logically remove: all the operations will recognize this node invalid


            // physically remove: try to fix the link
            var next: Node<E> = toRemove.nextNode
            while (next !== tail && next.isRemoved()) {
                next = next.nextNode
            }
            if (before.nextNodeRef.compareAndSet(toRemove, next)) {
                return true
            }
        }
    }

    val size: Int get() = head.countChildIterate<Node<E>>({ it.nextNode }, { it !is Tail }) - 1 // empty head is always included

    operator fun contains(element: E): Boolean {
        forEach { if (it == element) return true }
        return false
    }

    @Suppress("unused")
    fun containsAll(elements: Collection<E>): Boolean = elements.all { contains(it) }

    fun isEmpty(): Boolean = head.allMatching { it.isValidElementNode().not() }

    inline fun forEach(block: (E) -> Unit) {
        var node: Node<E> = head
        while (true) {
            node.letValueIfValid(block)
            node = node.nextNode
            if (node === tail) return
        }
    }

    fun addAll(elements: Collection<E>) = elements.forEach { addLast(it) }

    @Suppress("unused")
    fun clear() {
        val first = head.nextNode
        head.nextNode = tail
        first.childIterateReturnFirstUnsatisfying({
            val n = it.nextNode
            it.nextNode = tail
            it.removed.value = true
            n
        }, { it !== tail }) // clear the link structure, help GC.
    }

    @Suppress("unused")
    fun removeAll(elements: Collection<E>): Boolean = elements.all { remove(it) }

    /*


    private fun removeNode(node: Node<E>): Boolean {
        if (node == tail) {
            return false
        }
        while (true) {
            val before = head.iterateBeforeFirst { it === node }
            val toRemove = before.nextNode
            val next = toRemove.nextNode
            if (toRemove == tail) { // This
                return true
            }
            toRemove.nodeValue = null // logically remove first, then all the operations will recognize this node invalid

            if (before.nextNodeRef.compareAndSet(toRemove, next)) { // physically remove: try to fix the link
                return true
            }
        }
    }

    fun removeAt(index: Int): E {
        require(index >= 0) { "index must be >= 0" }
        val nodeBeforeIndex = head.iterateValidNodeNTimes(index)
        val value = nodeBeforeIndex.nodeValue
        if (value === null) noSuchElement()
        removeNode(nodeBeforeIndex)
        return value
    }

    operator fun set(index: Int, element: E): E {
        while (true) {
            val nodeAtIndex = head.iterateValidNodeNTimes(index + 1)
            val originalValue = nodeAtIndex.nodeValue
            if (originalValue === null) noSuchElement() // this node has been removed.
            if (!nodeAtIndex.nodeValueRef.compareAndSet(null, element)) { // with concurrent compatibility
                continue
            }
            return originalValue
        }
    }

    /**
     * Find the last index of the element in the list that is [equals] to [element], with concurrent compatibility.
     *
     * For a typical list, say `head <- Node#1(1) <- Node#2(2) <- Node#3(3) <- Node#4(4) <- Node#5(2) <- tail`,
     * the procedures of `lastIndexOf(2)` is:
     *
     * 1. Iterate each element, until 2 is found, accumulate the index found, which is 1
     * 2. Search again from the first matching element, which is Node#2
     * 3. Accumulate the index found.
     * 4. Repeat 2,3 until the `tail` is reached.
     *
     * Concurrent changes may influence the result.
     */
    fun lastIndexOf(element: E): Int {
        var lastMatching: Node<E> = head
        var searchStartingFrom: Node<E> = lastMatching
        var index = 0 // accumulated index from each search

        findTheLastMatchingElement@ while (true) { // iterate to find the last matching element.
            var timesOnThisTurn = if (searchStartingFrom === head) -1 else 0 // ignore the head
            val got = searchStartingFrom.nextNode.iterateBeforeFirst { timesOnThisTurn++; it.nodeValue == element }
            // find the first match starting from `searchStartingFrom`

            if (got.isTail()) break@findTheLastMatchingElement // no further elements
            check(timesOnThisTurn >= 0) { "Internal check failed: too many times ran: $timesOnThisTurn" }

            searchStartingFrom = got.nextNode
            index += timesOnThisTurn

            if (!got.isRemoved()) lastMatching = got
        }

        if (!lastMatching.isValidElementNode()) {
            // found is invalid means not found
            return -1
        }

        return index
    }
     */

    /*
    override fun listIterator(): MutableListIterator<E> = listIterator0(0)
    override fun listIterator(index: Int): MutableListIterator<E> = listIterator0(index)

    @Suppress("NOTHING_TO_INLINE")
    private inline fun listIterator0(index: Int): MutableListIterator<E> {
        var first: Node<E> = head
        repeat(index) {
            first = first.nextNode
            if (first === tail) noSuchElement()
        }
        return object : MutableListIterator<E> {
            var currentNode: Node<E>
                get() = currentNodeRef.value
                set(value) {
                    currentNodeRef.value = value
                }

            private val currentNodeRef: AtomicRef<Node<E>> = atomic(first) // concurrent compatibility

            override fun nextIndex(): Int = indexOfNode(currentNode)


            // region previous

            var previousNode: Node<E>
                get() = previousNodeRef.value
                set(value) {
                    previousNodeRef.value = value
                }
            private val previousNodeRef: AtomicRef<Node<E>> = atomic(head) // concurrent compatibility
            private val previousNodeIndexRef: AtomicInt = atomic(-1) // concurrent compatibility
            private val currentNodeAtTheMomentWhenPreviousNodeIsUpdated: AtomicRef<Node<E>> = atomic(currentNode)

            override fun hasPrevious(): Boolean = previousIndex() == -1

            private fun updatePrevious(): Boolean {
                while (true) {
                    val localNodeAtTheMomentWhenPreviousNodeIsUpdated = currentNode
                    var i = -1 // head
                    var lastSatisfying: Node<E>? = null
                    val foundNode = currentNode.childIterateReturnsLastSatisfying({ it.nextNode }, {
                        i++
                        if (it.isValidElementNode()) {
                            lastSatisfying = it
                        }
                        it != currentNode
                    })

                    if (localNodeAtTheMomentWhenPreviousNodeIsUpdated !== currentNode) {
                        continue // current is concurrently changed, must retry
                    }

                    if (!foundNode.isValidElementNode()) {
                        // Current node is not found in the list, meaning it had been removed concurrently
                        previousNode = head
                        previousNodeIndexRef.value = -1
                        return false
                    }

                    if (lastSatisfying === null) {
                        // All the previous nodes are logically removed.
                        previousNode = head
                        previousNodeIndexRef.value = -1
                        return false
                    }

                    currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value = localNodeAtTheMomentWhenPreviousNodeIsUpdated
                    previousNode = lastSatisfying!! // false positive nullable warning
                    previousNodeIndexRef.value = i
                    return true
                }
            }

            override fun previous(): E {
                if (currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value == head) {
                    // node list have been changed.
                    if (!updatePrevious()) noSuchElement()
                }
                while (true) {
                    val value = previousNode.nodeValue
                    if (value != null) {
                        currentNode = previousNode
                        currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value == head
                        return value
                    } else if (!updatePrevious()) noSuchElement()
                }
            }

            override fun previousIndex(): Int {
                if (currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value == head) {
                    // node list have been changed.
                    if (!updatePrevious()) noSuchElement()
                }
                while (true) {
                    val value = previousNodeIndexRef.value
                    if (value != -1) return value
                    else if (!updatePrevious()) noSuchElement()
                }
            }
            // endregion

            override fun add(element: E) {
                val toAdd = element.asNode(tail)
                while (true) {
                    val next = currentNode.nextNode
                    toAdd.nextNode = next
                    if (currentNode.nextNodeRef.compareAndSet(next, toAdd)) { // ensure the link is not changed
                        currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value = head
                        return
                    }
                }
            }

            override fun hasNext(): Boolean {
                if (currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value == head) {
                    // node list have been changed.
                    if (!updatePrevious()) noSuchElement()
                }
                return currentNode.nextNode !== tail
            }

            override fun next(): E {
                while (true) {
                    if (currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value == head) {
                        // node list have been changed.
                        if (!updatePrevious()) noSuchElement()
                    }
                    val nextNodeValue = currentNode.nextNode.nodeValue
                    if (nextNodeValue !== null) {
                        currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value = head
                        return nextNodeValue
                    }
                }
            }

            override fun remove() {
                if (!removeNode(currentNode)) { // search from head onto the node, concurrent compatibility
                    noSuchElement()
                }
                currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value = head
            }

            override fun set(element: E) {
                if (currentNodeAtTheMomentWhenPreviousNodeIsUpdated.value == head) {
                    // node list have been changed.
                    if (!updatePrevious()) noSuchElement()
                }

            }

        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {

    }
    */

    /*
    operator fun get(index: Int): E {
        require(index >= 0) { "Index must be >= 0" }
        var i = index + 1 // 1 for head
        return head.iterateStopOnFirst { i-- == 0 }.nodeValue ?: noSuchElement()
    }

    fun indexOf(element: E): Int {
        var i = -1 // head
        if (!head.iterateStopOnFirst {
                i++
                it.nodeValue == element
            }.isValidElementNode()) {
            return -1
        }
        return i - 1 // iteration is stopped at the next node
    }

    private fun indexOfNode(node: Node<E>): Int {
        var i = -1 // head
        if (!head.iterateStopOnFirst {
                i++
                it == node
            }.isValidElementNode()) {
            return -1
        }
        return i - 1 // iteration is stopped at the next node
    }

    operator fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        var currentNode: Node<E>
            get() = currentNodeRef.value
            set(value) {
                currentNodeRef.value = value
            }

        private val currentNodeRef: AtomicRef<Node<E>> = atomic(head) // concurrent compatibility

        /**
         * Check if
         *
         * **Notice That:**
         * if `hasNext` returned `true`, then the last remaining element is removed concurrently,
         * [next] will produce a [NoSuchElementException]
         */
        override fun hasNext(): Boolean = !currentNode.iterateStopOnFirst { it.isValidElementNode() }.isTail()

        /**
         * Iterate until the next node is not
         */
        override fun next(): E {
            while (true) {
                val next = currentNode.nextNode
                if (next.isTail()) noSuchElement()

                currentNode = next

                val nodeValue = next.nodeValue
                if (nodeValue != null) { // next node is not removed, that's what we want
                    return nodeValue
                } // or try again
            }
        }

        override fun remove() {
            if (!removeNode(currentNode)) { // search from head onto the node, concurrent compatibility
                noSuchElement()
            }
        }
    }
    */
}


// region internal

@Suppress("NOTHING_TO_INLINE")
private inline fun <E> E.asNode(nextNode: Node<E>): Node<E> = Node(nextNode, this)

/**
 * Self-iterate using the [iterator], until [mustBeTrue] returns `false`.
 * Returns the element at the last time when the [mustBeTrue] returns `true`
 */
@PublishedApi
internal inline fun <N : Node<*>> N.childIterateReturnsLastSatisfying(iterator: (N) -> N, mustBeTrue: (N) -> Boolean): N {
    if (!mustBeTrue(this)) return this
    var value: N = this

    while (true) {
        val newValue = iterator(value)
        if (mustBeTrue(newValue)) {
            value = newValue
        } else {
            return value
        }

        if (newValue is Tail<*>) return newValue
    }
}

/**
 * Self-iterate using the [iterator], until [mustBeTrue] returns `false`.
 * Returns the element at the first time when the [mustBeTrue] returns `false`
 */
private inline fun <E> E.childIterateReturnFirstUnsatisfying(iterator: (E) -> E, mustBeTrue: (E) -> Boolean): E {
    if (!mustBeTrue(this)) return this
    var value: E = this

    while (true) {
        val newValue = iterator(value)
        if (mustBeTrue(newValue)) {
            value = newValue
        } else {
            return newValue
        }

        if (newValue is Tail<*>) return newValue
    }
}

/**
 * Self-iterate using the [iterator], until [mustBeTrue] returns `false`.
 * Returns the count of elements being iterated.
 */
private inline fun <E> E.countChildIterate(iterator: (E) -> E, mustBeTrue: (E) -> Boolean): Int {
    var count = 0
    var value: E = this
    if (!mustBeTrue(value)) return count

    while (true) {
        count++
        val newValue = iterator(value)
        if (mustBeTrue(newValue)) {
            value = newValue
        } else {
            return count
        }
    }
}

@PublishedApi
internal class LazyNode<E> @PublishedApi internal constructor(
    nextNode: Node<E>,
    private val valueComputer: () -> E
) : Node<E>(nextNode, null) {
    private val initialized = atomic(false)

    private val value: AtomicRef<E?> = atomic(null)

    override val nodeValue: E
        get() {
            @Suppress("BooleanLiteralArgument") // false positive warning
            if (initialized.compareAndSet(false, true)) { // ensure only one lucky attempt can go into the if
                val value = valueComputer()
                this.value.value = value
                return value // fast path
            }
            value.loop {
                if (it != null) {
                    return it
                }
            }
        }
}

@PublishedApi
internal class Head<E>(nextNode: Node<E>) : Node<E>(nextNode, null) {
    override fun toString(): String = "Head"
    override val nodeValue: Nothing get() = error("Internal error: trying to get the value of a Head")
}

@PublishedApi
internal open class Tail<E> : Node<E>(null, null) {
    override fun toString(): String = "Tail"
    override val nodeValue: Nothing get() = error("Internal error: trying to get the value of a Tail")
}

@PublishedApi
internal open class Node<E>(
    nextNode: Node<E>?,
    private var initialNodeValue: E?
) {
    /*
    internal val id: Int = nextId()
    companion object {
        private val idCount = atomic(0)
        internal fun nextId() = idCount.getAndIncrement()
    }*/

    override fun toString(): String = "$nodeValue"

    open val nodeValue: E get() = initialNodeValue ?: error("Internal error: nodeValue is not initialized")

    val removed = atomic(false)

    @Suppress("LeakingThis")
    val nextNodeRef: AtomicRef<Node<E>> = atomic(nextNode ?: this)

    inline fun <R> letValueIfValid(block: (E) -> R): R? {
        if (!this.isValidElementNode()) {
            return null
        }
        val value = this.nodeValue
        return if (value !== null) block(value) else null
    }

    /**
     * Short cut for accessing [nextNodeRef]
     */
    var nextNode: Node<E>
        get() = nextNodeRef.value
        set(value) {
            nextNodeRef.value = value
        }

    /**
     * Returns the former node of the last node whence [filter] returns true
     */
    inline fun iterateBeforeFirst(filter: (Node<E>) -> Boolean): Node<E> =
        this.childIterateReturnsLastSatisfying({ it.nextNode }, { !filter(it) })

    /**
     * Check if all the node which is not [Tail] matches the [condition]
     *
     * Head, which is this, is also being tested.
     * [Tail], is not being tested.
     */
    inline fun allMatching(condition: (Node<E>) -> Boolean): Boolean = this.childIterateReturnsLastSatisfying({ it.nextNode }, condition) !is Tail

    /**
     * Stop on and returns the former element of the element that is [equals] to the [element]
     *
     * E.g.: for `head <- 1 <- 2 <- 3 <- tail`, `iterateStopOnNodeValue(2)` returns the node whose value is 1
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun iterateBeforeNodeValue(element: E): Node<E> = this.iterateBeforeFirst { it.isValidElementNode() && it.nodeValue == element }

}

@PublishedApi
internal fun <E> Node<E>.isRemoved() = this.removed.value

@PublishedApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun Node<*>.isValidElementNode(): Boolean = !isHead() && !isTail() && !isRemoved()

@PublishedApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun Node<*>.isHead(): Boolean = this is Head

@PublishedApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun Node<*>.isTail(): Boolean = this is Tail

// en dregion