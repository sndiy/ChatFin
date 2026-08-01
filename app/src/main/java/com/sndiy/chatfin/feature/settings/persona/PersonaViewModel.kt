package com.sndiy.chatfin.feature.settings.persona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.persona.PersonaId
import com.sndiy.chatfin.core.persona.PersonaPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val personaPrefs: PersonaPreferences
) : ViewModel() {

    // Flow DataStore langsung, bukan one-shot read di init{} — selalu hidup,
    // tidak ada risiko "state basi" seperti yang diperbaiki di ApiKeyViewModel (M8).
    val activePersonaId: StateFlow<PersonaId> = personaPrefs.activePersonaId
        .stateIn(viewModelScope, SharingStarted.Eagerly, PersonaId.MAI)

    val customPersonaText: StateFlow<String> = personaPrefs.customPersonaText
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun selectPersona(id: PersonaId) {
        viewModelScope.launch { personaPrefs.setPersona(id) }
    }

    fun saveCustomText(text: String) {
        viewModelScope.launch { personaPrefs.setCustomText(text) }
    }
}
