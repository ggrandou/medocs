package net.grandou.rappels.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.grandou.rappels.Settings
import net.grandou.rappels.data.AppDatabase
import net.grandou.rappels.data.BackupCrypto
import net.grandou.rappels.data.BackupData
import net.grandou.rappels.data.BackupInvalid
import net.grandou.rappels.data.BackupJson
import net.grandou.rappels.data.BackupSource
import net.grandou.rappels.data.ParsedBackup
import net.grandou.rappels.data.PhotoStore
import net.grandou.rappels.data.Repository
import net.grandou.rappels.data.WrongPassword
import java.time.LocalDate

/** What a backup contains, for display. */
data class BackupSummary(val items: Int, val schedules: Int, val journal: Int, val photos: Int) {
    constructor(d: BackupData) : this(d.items.size, d.schedules.size, d.occurrences.size, d.photos.size)
}

/** The backup screen: idle (with the last error, if any), working, or done. */
data class ExportState(val working: Boolean = false, val done: BackupSummary? = null, val error: String? = null)

/** The restore flow: pick a file, unlock it, review what it holds, then replace. */
sealed interface RestoreState {
    data object Idle : RestoreState
    /** A file is chosen and waits for its password; [error] is the reason of a failed attempt. */
    data class Picked(val fileName: String, val error: String? = null) : RestoreState
    data object Checking : RestoreState
    data class Ready(val fileName: String, val source: BackupSource, val createdAt: Long?, val summary: BackupSummary) : RestoreState
    data object Restoring : RestoreState
    data class Done(val summary: BackupSummary) : RestoreState
    data class Failed(val message: String) : RestoreState
}

/**
 * Encrypted backup: export to / restore from a file chosen by the user through the system picker.
 * Everything that is secret or big (password, decrypted content) stays in memory here, never in saved UI state.
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(AppDatabase.get(app))
    private val settings = Settings(app)

    private val _export = MutableStateFlow(ExportState())
    val export: StateFlow<ExportState> = _export

    private val _restore = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restore: StateFlow<RestoreState> = _restore

    /** What is on the phone now (null while loading). */
    private val _current = MutableStateFlow<BackupSummary?>(null)
    val current: StateFlow<BackupSummary?> = _current

    private val _lastBackup = MutableStateFlow(settings.lastBackup)
    /** Instant of the last backup made from this phone, 0 = never. */
    val lastBackup: StateFlow<Long> = _lastBackup

    private var password: CharArray? = null
    private var pickedUri: Uri? = null
    private var pickedName = ""
    private var parsed: ParsedBackup? = null

    fun refreshCurrent() {
        viewModelScope.launch(Dispatchers.IO) { _current.value = BackupSummary(repo.exportAll()) }
    }

    fun resetExport() { _export.value = ExportState(); wipePassword() }

    fun resetRestore() { _restore.value = RestoreState.Idle; pickedUri = null; parsed = null }

    // --- Export ---

    /** The password waits here while the system file picker is open. */
    fun holdPassword(p: CharArray) { wipePassword(); password = p }

    fun wipePassword() { password?.fill('\u0000'); password = null }

    fun suggestedFileName(): String = "tas-pense-a-${LocalDate.now()}.rappels"

    fun export(uri: Uri) {
        val pwd = password ?: return
        password = null
        _export.value = ExportState(working = true)
        viewModelScope.launch(Dispatchers.IO) {
            _export.value = try {
                val ctx = getApplication<Application>()
                val base = repo.exportAll()
                val data = base.copy(photos = PhotoStore.readAll(ctx, base.items.mapNotNull { it.photoPath }))
                val now = System.currentTimeMillis()
                val file = BackupCrypto.encrypt(BackupJson.toJson(data, now).toByteArray(Charsets.UTF_8), pwd)
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(file) }
                settings.lastBackup = now
                _lastBackup.value = now
                ExportState(done = BackupSummary(data))
            } catch (e: Exception) {
                ExportState(error = "La sauvegarde a échoué : ${e.message ?: e.javaClass.simpleName}")
            } finally {
                pwd.fill('\u0000')
            }
        }
    }

    // --- Restore ---

    fun pick(uri: Uri) {
        val ctx = getApplication<Application>()
        pickedName = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) { null } ?: "Fichier choisi"
        pickedUri = uri
        parsed = null
        _restore.value = RestoreState.Picked(pickedName)
    }

    /** Decrypts and reads the file, without touching the data: the user reviews the content before replacing. */
    fun unlock(pwd: CharArray) {
        val uri = pickedUri ?: return
        _restore.value = RestoreState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            _restore.value = try {
                val ctx = getApplication<Application>()
                val size = ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                if (size > MAX_FILE) throw BackupInvalid("Fichier trop volumineux pour une sauvegarde")
                val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val result = BackupJson.parse(String(BackupCrypto.decrypt(bytes, pwd), Charsets.UTF_8))
                // Photos are checked now, so that an unreadable image fails here and nothing changes
                PhotoStore.decodeAll(result.data.photos)
                parsed = result
                RestoreState.Ready(pickedName, result.source, result.createdAt, BackupSummary(result.data))
            } catch (e: WrongPassword) {
                RestoreState.Picked(pickedName, "Mot de passe incorrect, ou fichier modifié.")
            } catch (e: BackupInvalid) {
                RestoreState.Failed(e.message ?: "Fichier refusé")
            } catch (e: Exception) {
                RestoreState.Failed("Lecture impossible : ${e.message ?: e.javaClass.simpleName}")
            } finally {
                pwd.fill('\u0000')
            }
        }
    }

    /** Replaces all the data by the unlocked backup. Photos are written first: a failure leaves the database untouched. */
    fun confirmRestore() {
        val p = parsed ?: return
        _restore.value = RestoreState.Restoring
        viewModelScope.launch(Dispatchers.IO) {
            _restore.value = try {
                val ctx = getApplication<Application>()
                PhotoStore.decodeAll(p.data.photos).forEach { (name, bytes) -> PhotoStore.write(ctx, name, bytes) }
                repo.replaceAll(p.data)
                PhotoStore.clean(ctx, p.data.items.mapNotNull { it.photoPath }.toSet())
                parsed = null
                _current.value = BackupSummary(p.data)
                RestoreState.Done(BackupSummary(p.data))
            } catch (e: Exception) {
                RestoreState.Failed("La restauration a échoué, vos données n'ont pas été modifiées : ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    override fun onCleared() { wipePassword() }

    private companion object {
        const val MAX_FILE = 200L * 1024 * 1024
    }
}
