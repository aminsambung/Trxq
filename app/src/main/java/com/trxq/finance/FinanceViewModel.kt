package com.trxq.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trxq.finance.data.Transaction
import com.trxq.finance.data.TransactionDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FinanceViewModel(
    private val dao: TransactionDao
) : ViewModel() {

    val transactions = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun insert(item: Transaction) {
        viewModelScope.launch {
            dao.insert(item)
        }
    }

    fun update(item: Transaction) {
        viewModelScope.launch {
            dao.update(item)
        }
    }

    fun delete(item: Transaction) {
        viewModelScope.launch {
            dao.delete(item)
        }
    }

    fun replace(items: List<Transaction>) {
        viewModelScope.launch {
            dao.clear()
            dao.insertAll(items)
        }
    }
}
