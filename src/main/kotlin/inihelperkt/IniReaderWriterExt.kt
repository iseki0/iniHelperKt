package inihelperkt

import java.io.BufferedReader
import java.io.Reader
import java.io.Writer

/**
 * write ini to [stream]
 *
 * **Note:** This API will not close the [Writer]
 */
infix fun IniTree.writeTo(stream: Writer) {
    stream.write(encode())
}

/**
 * read ini from [this]
 *
 * **Note:** This API will not close the [Reader]
 */
fun Reader.readIni(): IniTree {
    val parser = IniParser()
    (if (this is BufferedReader) this else this.buffered()).let {
        it.transferTo(parser)
        parser.close()
    }
    return parser.tree
}
