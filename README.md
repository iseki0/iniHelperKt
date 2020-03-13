# iniHelperKt
use *.ini file in Kotlin/Jvm keep comments. 

Example:
```kotlin

import java.io.File

const PATH = "file.ini"

@Section("SectionName") // optional
class Test{
    val mapper = IniMapper(FileSystemIniStore(File(PATH)))

    val readOnlyKey by mapper.bind("readOnlyKey"){ "Default value" }

    var readWriteKey by mapper.bind("AnotherSection:readWriteKey"){ "default" }
}
```