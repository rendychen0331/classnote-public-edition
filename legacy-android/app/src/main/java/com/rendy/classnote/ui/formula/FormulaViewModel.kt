package com.rendy.classnote.ui.formula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rendy.classnote.data.local.entity.FormulaEntity
import com.rendy.classnote.data.repository.FormulaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FormulaViewModel(private val repository: FormulaRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val formulas: StateFlow<List<FormulaEntity>> = searchQuery
        .debounce(200)
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getAllFormulas()
            else repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(query: String) {
        searchQuery.value = query
    }

    fun insert(formula: FormulaEntity) = viewModelScope.launch {
        repository.insert(formula)
    }

    fun update(formula: FormulaEntity) = viewModelScope.launch {
        repository.update(formula)
    }

    fun delete(formula: FormulaEntity) = viewModelScope.launch {
        repository.delete(formula)
    }

    suspend fun getById(id: Long): FormulaEntity? = repository.getById(id)

    class Factory(private val repository: FormulaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FormulaViewModel(repository) as T
    }
}
