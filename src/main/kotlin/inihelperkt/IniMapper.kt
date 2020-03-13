package inihelperkt

import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class IniMapper(val store: IniStore) {
    val codecs: MutableMap<KClass<*>, Codecs<*>> =
        listOf(StringCodecs, IntCodecs, BooleanCodecs).map { it.type to it }.toMap().toMutableMap()
    private val map: MutableMap<Pair<String, String>, IniElement.Item> = mutableMapOf()
    private val bindingList = mutableListOf<IniBinding<*>>()

    var tree: IniTree? = null

    init {
        load()
    }

    /**
     * get kotlin property delegation.
     *
     * @param[key] ini item's key and section name
     *
     * **Example:**
     *
     *  "Section:itemName.awsl" ->
     *
     *  `[Section]`
     *
     *  `itemName.awsl = "value"`
     *
     * @see Section
     *
     * @param[default] provide default value when the key is not exists.
     *
     * @throws [IllegalStateException] when something goes wrong. Such as item not exists and without default.
     */
    inline fun <reified T : Any> bind(key: String, noinline default: (() -> T)? = null): IniBinding<T> {
        return IniBinding(key, T::class, default)
    }

    /**
     * load file. It will be called during init.
     * If ini file has been modified, call it to get update.
     */
    fun load() {
        val tree = store.loadIniTree()
        this.tree = tree
        updateCache()
    }

    /**
     * If the AST tree has been modified, must call it immediately.
     *
     * Mapper has cache for IniItem internal. This method will be auto called in [load]
     */
    fun updateCache() {
        val tree = checkNotNull(tree) { "tree is null" }
        map.clear()
        tree.list.asSequence().filter { it is IniElement.Section }.forEach {
            (it as IniElement.Section).let { section ->
                it.list.asSequence().filter { it is IniElement.Item }.forEach {
                    (it as IniElement.Item).let {
                        map[section.name to it.key] = it
                    }
                }
            }
        }
    }

    /**
     * store ini file.
     */
    fun store() {
        check(checkValid()) { "tree is invalid. updateCache if modified!" }
        val tree = checkNotNull(tree)
        store.storeIniTree(tree)
    }

    /**
     * If the AST tree has been change, better call it
     *
     * @return the AST is available, in other words every binding is available
     */
    fun checkValid(): Boolean {
        bindingList.forEach {
            if (!map.containsKey(it.id))
                return false
        }
        return true
    }

    private fun insertDefault(id: Pair<String, String>) = IniElement.Item(id.second, "").apply {
        map[id] = this
        val tree = checkNotNull(tree)
        (tree.list.find { it is IniElement.Section && it.name == id.first }?.let { it as? IniElement.Section }
            ?: IniElement.Section(id.first).also { tree.list.add(it) }).let { it.list.add(this) }
    }

    inner class IniBinding<T : Any>(key: String, val type: KClass<T>, val default: (() -> T)?) {
        private var thisRef: Any?=null

        val id by lazy {
            key.indexOf(':').let {
                if(it > 0)
                    key.slice(0 until it).trim() to key.slice(it + 1..key.lastIndex).trim()
                else
                    (thisRef!!::class.annotations.find{ it is Section } as Section).name to key
            }
        }

        val codec by lazy(LazyThreadSafetyMode.NONE) { (codecs[type] as? Codecs<T> ?: error("no codecs")) }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            this.thisRef=thisRef
            return map[id]?.let { codec.decode(it.value) }
                ?: default?.invoke()?.also { setValue(null, property, it) }
                ?: error("not found and no default")
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.thisRef=thisRef
            (map[id] ?: insertDefault(id)).let { it.value = codec.encode(value) }
        }
    }
}

/**
 * Identify ini section name
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Section(val name:String)

/**
 * encode and decode ini value
 */
interface Codecs<T : Any> {
    /**
     * supported by the codecs.
     */
    val type: KClass<T>
    fun encode(o: T): String
    fun decode(s: String): T
}

object StringCodecs : Codecs<String> {
    override val type: KClass<String> = String::class
    override fun encode(o: String): String = o
    override fun decode(s: String): String = s
}

object IntCodecs : Codecs<Int> {
    override val type: KClass<Int> = Int::class
    override fun encode(o: Int): String = o.toString()
    override fun decode(s: String): Int = s.toInt()
}

object BooleanCodecs : Codecs<Boolean> {
    override val type: KClass<Boolean> = Boolean::class
    override fun encode(o: Boolean): String = o.toString()
    override fun decode(s: String): Boolean = s.toBoolean()
}
