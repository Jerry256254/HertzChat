package cz.kuclab.hertzchat.ui.file

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.kuclab.hertzchat.data.db.MessageDao
import cz.kuclab.hertzchat.data.db.MessageEntity
import cz.kuclab.hertzchat.media.MediaStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FileViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val mediaStorage: MediaStorage,
) : ViewModel() {

    private val messageId: String = checkNotNull(savedStateHandle["messageId"])

    private val _message = MutableStateFlow<MessageEntity?>(null)
    val message: StateFlow<MessageEntity?> = _message

    private val _notice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val notice: SharedFlow<String> = _notice

    init {
        viewModelScope.launch { _message.value = messageDao.find(messageId) }
    }

    fun download() {
        val message = _message.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val path = message.mediaPath
            val file = path?.let { File(it) }
            if (file == null || !file.exists()) {
                _notice.emit("Soubor už není k dispozici")
                return@launch
            }
            mediaStorage.saveToPublic(file, message.mediaMimeType, message.mediaFileName ?: file.name)
                .onSuccess { location -> _notice.emit("Uloženo do $location") }
                .onFailure { _notice.emit("Uložení selhalo") }
        }
    }
}
