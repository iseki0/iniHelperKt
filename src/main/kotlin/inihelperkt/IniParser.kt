package inihelperkt

import java.io.Writer
import java.util.*

/**
 * IniParser, the better use a wrapper instead.
 *
 * @param[features] enabled features, default is empty
 *
 * @see FileSystemIniStore
 */
class IniParser(features: Set<Feature> = setOf()) : Writer() {
    enum class Feature {
        /**
         * Strict mode: raise an [Exception] and stop parse on invalid line.
         *
         * Default is disabled
         */
        STRICT,

        /**
         * Wrapped value: value which is wrapped by quotation marks(Also allow escape by backslash)
         *
         * Default is disabled.
         */
        WRAPPED_VALUE,
    }

    private enum class State {
        Init, Idle, SectionIdentify, Key, BeforeValue, CommonValue, WrappedValue, Escape, UnicodeEscape, Comment, EOF
    }

    var warnHandler: ((msg: String, pos: Int) -> Unit)? = null

    val featureWrappedValue = Feature.WRAPPED_VALUE in features
    val featureStrict = Feature.STRICT in features

    private val textBuffer = StringBuilder()
    private var uncBuf = 0
    private var uncCounter = 0

    private val list = LinkedList<IniElement>()

    /**
     * ini AST tree, only readable when parser is EOF
     */
    val tree = IniTree(list)
        get() = field.also { check(state == State.EOF) { "parser not stop" } }

    private fun parse(buf: String) {
        loop(buf)
    }

    private fun end(buf: String = "") {
        loop(buf)
        loop("\n")
        check(state == State.Idle) { "parser not stop" }
        state = State.EOF
    }

    private var state = State.Init

    private fun loop(buf: String) {
        buf.forEachIndexed { index, ch ->
            var repeat = true
            while (repeat) {
                repeat = false
                when (state) {
                    State.Init -> {
                        when (ch) {
                            '\uFFFE', '\uFEFF' -> Unit
                            else -> {
                                repeat = true
                                state = State.Idle
                            }
                        }
                    }
                    State.Idle -> {
                        when (ch) {
                            '\r', '\n', ' ' -> Unit
                            '[' -> state = State.SectionIdentify
                            ';' -> state = State.Comment
                            else -> {
                                repeat = true
                                state = State.Key
                            }
                        }
                    }
                    State.Comment -> {
                        when (ch) {
                            '\r', '\n' -> {
                                endComment()
                                state = State.Idle
                            }
                            else -> textBuffer += ch
                        }
                    }
                    State.WrappedValue -> {
                        when (ch) {
                            '\"' -> {
                                endValue()
                                state = State.Idle
                            }
                            '\\' -> state = State.Escape
                            else -> textBuffer += ch
                        }
                    }
                    State.UnicodeEscape -> {
                        if (uncCounter > 3) {
                            textBuffer += uncBuf.toChar()
                            state = State.WrappedValue
                            uncCounter = 0
                            uncBuf = 0
                        } else {
                            uncCounter++
                            uncBuf = (uncBuf shl 4) + hexCharToInt(ch)
                        }
                    }
                    State.Escape -> {
                        escapeMap[ch]?.let {
                            state = State.WrappedValue
                            textBuffer += ch
                        } ?: if (ch == 'u') state = State.UnicodeEscape else error("unknown escape: $ch")
                    }
                    State.CommonValue -> {
                        when (ch) {
                            '\r', '\n' -> {
                                endValue()
                                state = State.Idle
                            }
                            else -> textBuffer += ch
                        }
                    }
                    State.BeforeValue -> {
                        when (ch) {
                            '\r', '\n' -> {
                                endValue()
                                state = State.Idle
                            }
                            ' ' -> Unit
                            else -> {
                                if (ch == '\"' && featureWrappedValue) {
                                    state = State.WrappedValue
                                } else {
                                    repeat = true
                                    state = State.CommonValue
                                }
                            }
                        }
                    }
                    State.Key -> {
                        when (ch) {
                            '=' -> {
                                endKey()
                                state = State.BeforeValue
                            }
                            '\r', '\n' -> {
                                warn("invalid line", index)
                            }
                            else -> textBuffer += ch
                        }
                    }
                    State.SectionIdentify -> {
                        when (ch) {
                            '\r', '\n' -> error("invalid section")
                            ']' -> {
                                endSectionTitle()
                                state = State.Idle
                            }
                            else -> textBuffer += ch
                        }
                    }
                    State.EOF -> error("parser is EOF")
                }
            }
        }

    }

    private fun warn(msg: String, pos: Int) {
        check(!featureStrict) { msg }
        warnHandler?.invoke(msg, pos)
    }

    private var sectionList: MutableList<IniElement>? = null
    private fun endSectionTitle() {
        sectionList = mutableListOf()
        val name = textBuffer.toString().trim()
        textBuffer.clear()
        check(name.isNotBlank()) { "blank section name" }
        list += IniElement.Section(name = name, list = sectionList!!)
    }

    private var curKey = ""
    private fun endKey() {
        curKey = textBuffer.toString().trim()
        textBuffer.clear()
        check(curKey.isNotBlank()) { "blank key" }
    }

    private fun endValue() {
        val value = textBuffer.toString().trim()
        textBuffer.clear()
        sectionList?.add(IniElement.Item(key = curKey, value = value)) ?: error("no section before")
    }

    private fun endComment() {
        val text = textBuffer.toString().trim()
        textBuffer.clear()
        (sectionList ?: list).add(IniElement.Comment(text))
    }


    companion object {
        private val escapeMap = mapOf(
            'r' to '\r',
            'n' to '\n',
            't' to '\t',
            'a' to '\u0007',
            'b' to '\b',
            '0' to '\u0000',
            '\\' to '\\',
            '\'' to '\'',
            '\"' to '\"',
            ';' to ';',
            '#' to '#',
            ':' to ':'
        )
        private val reversedEscape = escapeMap.asSequence().map { (k, v) -> v to k }.toMap()
        private fun hexCharToInt(ch: Char) =
            when (ch) {
                in '0'..'9' -> ch - '0'
                in 'a'..'f' -> ch - 'a'
                in 'A'..'F' -> ch - 'A'
                else -> error("Not a hex")
            }
    }

    private operator fun StringBuilder.plusAssign(ch: Char) {
        this.append(ch)
    }

    /**
     * write ini char sequence
     */
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        parse(String(cbuf, off, len))
    }

    /**
     * Use less in this scene.
     */
    override fun flush() {}

    /**
     * must be called if no more char sequence will be written
     */
    override fun close() {
        end()
    }
}


sealed class IniElement {
    class Section(val name: String, val list: MutableList<IniElement> = mutableListOf()) : IniElement() {
        override fun toString() = buildString {
            this.appendln("[${name.trim()}]")
            list.forEach {
                this.appendln(it)
            }
        }
    }

    class Item(val key: String, var value: String) : IniElement() {
        override fun toString() = "${key.trim()} = ${value.trim()}"
    }

    class Comment(var text: String) : IniElement() {
        override fun toString() = ";$text"
    }

}

/**
 * INI file AST tree.
 */
class IniTree(val list: MutableList<IniElement>)