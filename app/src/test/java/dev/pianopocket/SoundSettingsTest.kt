package dev.pianopocket
import dev.pianopocket.settings.*
import dev.pianopocket.library.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
class SoundSettingsTest {
    private fun preset()=Preset("test",emptyList(),listOf("Mic"),listOf(Effect(7,"IR",floatArrayOf(.7f,0f,0f,0f,1f),"one.wav")),listOf(Control("Mix",0f,1f,.7f,emptyList())),emptyList(),listOf(.8f),menus=listOf(IrMenu("Room",listOf(IrChoice("one",0,"one.wav"),IrChoice("two",0,"two.wav")),0)))
    @Test fun restoresLastAndNamedSoundsAfterRecreationWithoutCrossingInstruments() {
        val directory=Files.createTempDirectory("pocket-sounds-").toFile()
        try {
            val p=preset();val schema=SoundSettings.schema(p);val s=SoundSettings.capture(p,.65f,512,listOf(.4f));val store=SoundSettingsStore(directory)
            store.write("instrument-a",schema,SoundRecord(s,listOf(FavoriteSound("Hall",s.copy(output=.8f,menus=listOf(1))))))
            val recovered=SoundSettingsStore(directory).read("instrument-a",schema)
            assertEquals(s,recovered.last);assertEquals(1,recovered.favorites.single().settings.menus.single());assertEquals(.8f,recovered.favorites.single().settings.output)
            assertNull(store.read("instrument-b",schema).last);assertTrue(recovered.last!!.valid(p));store.delete("instrument-a");assertTrue(directory.listFiles()!!.isEmpty())
        }finally{directory.deleteRecursively()}
    }
    @Test fun compatibleIrSelectionSharesSchemaButChangedControlsKeepOldProfilesSeparate() {
        val p=preset();val selected=p.copy(effects=p.effects.map {it.copy(ir="two.wav")},menus=p.menus.map {it.copy(selected=1)},controls=p.controls.map {it.copy(value=.9f)})
        assertEquals(SoundSettings.schema(p),SoundSettings.schema(selected))
        assertNotEquals(SoundSettings.schema(p),SoundSettings.schema(p.copy(controls=p.controls.map {it.copy(name="Different")})))
        assertFalse(SoundSettings.capture(p,.7f,512,listOf(1f)).copy(menus=listOf(8)).valid(p))
    }
    @Test fun boundedCodecRejectsCorruptionNanDuplicateNamesAndTruncation() {
        val s=SoundSettings.capture(preset(),.7f,512,listOf(1f));val bytes=SoundSettingsStore.encode(SoundRecord(s))
        for(b in listOf(bytes.copyOf(bytes.size-1),bytes+byteArrayOf(1),byteArrayOf(0,0,0,0)))assertTrue(runCatching {SoundSettingsStore.decode(b)}.isFailure)
        assertTrue(runCatching {SoundSettingsStore.encode(SoundRecord(s.copy(output=Float.NaN)))}.isFailure)
        assertTrue(runCatching {SoundSettingsStore.encode(SoundRecord(s,List(33){FavoriteSound("$it",s)}))}.isFailure)
        assertTrue(runCatching {SoundSettingsStore.encode(SoundRecord(s,listOf(FavoriteSound("same",s),FavoriteSound("same",s))))}.isFailure)
    }
    @Test fun hashesUntrustedIdsAndPreservesCorruptBytesForRecovery() {
        val directory=Files.createTempDirectory("pocket-sound-safe-").toFile()
        try{val store=SoundSettingsStore(directory);val schema=SoundSettings.schema(preset());store.write("../../escape",schema,SoundRecord());val file=directory.listFiles()!!.single();assertEquals(directory.canonicalPath,checkNotNull(file.parentFile).canonicalPath);file.writeBytes(byteArrayOf(1));assertTrue(runCatching {store.read("../../escape",schema)}.isFailure);store.backupCorrupt("../../escape",schema);assertNull(store.read("../../escape",schema).last);assertTrue(directory.listFiles()!!.single().name.endsWith(".broken"))}finally{directory.deleteRecursively()}
    }
}
