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

    // Menambahkan transaksi baru
    fun add(
        type: String,
        amount: Long,
        category: String,
        note: String,
        date: String
    ) {
        viewModelScope.launch {
            dao.insert(
                Transaction(
                    type = type,
                    amount = amount,
                    category = category,
                    note = note,
                    date = date
                )
            )
        }
    }

    // Menyimpan transaksi yang sudah dibuat
    fun insert(item: Transaction) {
        viewModelScope.launch {
            dao.insert(item)
        }
    }

    // Memperbarui transaksi
    fun update(item: Transaction) {
        viewModelScope.launch {
            dao.update(item)
        }
    }

    // Menghapus transaksi
    fun delete(item: Transaction) {
        viewModelScope.launch {
            dao.delete(item)
        }
    }

    // Mengganti seluruh data transaksi
    fun replace(items: List<Transaction>) {
        viewModelScope.launch {
            dao.clear()
            dao.insertAll(items)
        }
    }
}
