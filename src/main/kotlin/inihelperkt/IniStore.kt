package inihelperkt

import java.io.File

interface IniStore {
    fun loadIniTree(): IniTree
    fun storeIniTree(tree: IniTree)
}

/**
 * A ini storage provider for [File]
 */
class FileSystemIniStore(val file: File) : IniStore {
    override fun loadIniTree() = file.bufferedReader().use { it.readIni() }

    override fun storeIniTree(tree: IniTree) {
        file.bufferedWriter().use { tree writeTo it }
    }
}



