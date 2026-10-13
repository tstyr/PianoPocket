package dev.pianopocket.library

object LibraryFilePolicy {
    fun isPreset(relative: String):Boolean = (relative.endsWith(".dspreset",true) || relative.endsWith(".sfz",true)) && relative.replace('\\','/').split('/').none { it=="__MACOSX" || it.startsWith("._") || it==".DS_Store" }
}
