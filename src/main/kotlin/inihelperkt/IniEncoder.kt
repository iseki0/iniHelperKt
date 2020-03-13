package inihelperkt


fun IniTree.encode(): String {
    val sb = StringBuilder()
    list.forEach { sb.appendln(it) }
    return sb.toString()
}

