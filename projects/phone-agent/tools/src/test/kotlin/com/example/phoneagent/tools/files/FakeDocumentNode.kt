package com.example.phoneagent.tools.files

/**
 * Plain-Kotlin [DocumentNode] tree for unit tests: no Android types, counts [childrenCalls] so
 * tests can assert the walker never queries nodes it should not reach. Open so scenario nodes
 * can override timing behaviour.
 */
internal open class FakeDocumentNode(
    override val name: String,
    override val isDirectory: Boolean = false,
    private val childNodes: List<FakeDocumentNode> = emptyList(),
    private val childrenError: RuntimeException? = null,
) : DocumentNode {
    var childrenCalls: Int = 0
        private set

    open override fun children(): List<FakeDocumentNode> {
        childrenCalls++
        childrenError?.let { throw it }
        return childNodes
    }
}

internal fun dir(
    name: String,
    vararg children: FakeDocumentNode,
): FakeDocumentNode = FakeDocumentNode(name, isDirectory = true, childNodes = children.toList())

internal fun file(name: String): FakeDocumentNode = FakeDocumentNode(name)
