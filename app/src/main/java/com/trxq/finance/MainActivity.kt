package com.trxq.finance

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trxq.finance.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class FinanceViewModel(private val dao: TransactionDao) : ViewModel() {
    val transactions = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(type:String, amount:Long, category:String, note:String, date:String) = viewModelScope.launch {
        if (amount > 0) dao.insert(Transaction(type=type, amount=amount, category=category, note=note, date=date))
    }
    fun delete(item:Transaction) = viewModelScope.launch { dao.delete(item) }
    fun update(item:Transaction) = viewModelScope.launch { dao.update(item) }
    fun replace(items:List<Transaction>) = viewModelScope.launch { dao.clear(); dao.insertAll(items) }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.get(this).transactionDao()
        setContent {
            val vm = remember { FinanceViewModel(dao) }
            MaterialTheme(colorScheme = lightColorScheme(primary=Color(0xFF087F5B), secondary=Color(0xFF14B889), background=Color(0xFFF4F7F6))) {
                FinanceApp(this, vm)
            }
        }
    }
}
private fun rupiah(n:Long):String = "Rp " + NumberFormat.getNumberInstance(Locale("id","ID")).format(n)
private fun monthKey(m:YearMonth)=m.toString()

@Composable
fun FinanceApp(context:Context, vm:FinanceViewModel) {
    val all by vm.transactions.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    val current=all.filter { it.date.startsWith(monthKey(month)) }
    val income=current.filter{it.type=="Pemasukan"}.sumOf{it.amount}
    val expense=current.filter{it.type=="Pengeluaran"}.sumOf{it.amount}
    val backupOut= rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) {
            val arr=JSONArray()
            all.forEach { arr.put(JSONObject().put("id",it.id).put("type",it.type).put("amount",it.amount).put("category",it.category).put("note",it.note).put("date",it.date)) }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(arr.toString(2)) }
        }
    }
    val backupIn=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try {
            val text=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()} ?: "[]"
            val arr=JSONArray(text); val items= mutableListOf<Transaction>()
            for(i in 0 until arr.length()) { val o=arr.getJSONObject(i); items.add(Transaction(type=o.getString("type"),amount=o.getLong("amount"),category=o.getString("category"),note=o.optString("note"),date=o.getString("date"))) }
            vm.replace(items)
        } catch (_:Exception) { }
    }
    Scaffold(bottomBar={ NavigationBar { listOf("Ringkasan","Transaksi","Laporan","Cadangan").forEachIndexed { i,s -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","＋","▥","⇧")[i])},label={Text(s)}) } } },
        floatingActionButton={ if(tab==0 || tab==1) FloatingActionButton(onClick={showAdd=true},containerColor=Color(0xFF087F5B)){Text("+",color=Color.White)} }) { pad ->
        Column(Modifier.fillMaxSize().background(Color(0xFFF4F7F6)).padding(pad).padding(16.dp)) {
            Text("Catatan Keuangan",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Color(0xFF123B30))
            Spacer(Modifier.height(12.dp))
            when(tab) {
                0 -> {
                    MonthPicker(month){month=it}
                    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF087F5B)),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Sisa penghasilan",color=Color.White.copy(alpha=.8f))
                            Text(rupiah(income-expense),color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Column { Text("Pemasukan",color=Color.White.copy(alpha=.8f)); Text(rupiah(income),color=Color.White,fontWeight=FontWeight.SemiBold) }
                                Column { Text("Pengeluaran",color=Color.White.copy(alpha=.8f)); Text(rupiah(expense),color=Color.White,fontWeight=FontWeight.SemiBold) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Perbandingan bulan ini",fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    BarCompare(income,expense)
                    Spacer(Modifier.height(16.dp))
                    Text("Grafik 6 bulan",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    MonthlyChart(all, month)
                    Spacer(Modifier.height(14.dp))
                    Text("Transaksi terbaru",fontWeight=FontWeight.Bold)
                    TransactionList(current.take(5),{editing=it},{vm.delete(it)})
                }
                1 -> { MonthPicker(month){month=it}; TransactionList(current,{editing=it},{vm.delete(it)}) }
                2 -> {
                    MonthPicker(month){month=it}
                    SummaryCard("Total pemasukan",income)
                    SummaryCard("Total pengeluaran",expense)
                    SummaryCard("Sisa penghasilan",income-expense)
                    Spacer(Modifier.height(12.dp)); Text("Pengeluaran per kategori",fontWeight=FontWeight.Bold)
                    val cats=current.filter{it.type=="Pengeluaran"}.groupBy{it.category}.mapValues{e->e.value.sumOf{it.amount}}.toList().sortedByDescending{it.second}
                    cats.forEach { (name,value)-> Column(Modifier.padding(vertical=6.dp)) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(categoryIcon(name), style=MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("Kategori")
                    }
                    Text(rupiah(value))
                }; LinearProgressIndicator(progress={if(expense>0)value.toFloat()/expense else 0f},modifier=Modifier.fillMaxWidth()) } }
                }
                3 -> {
                    Text("Cadangkan data transaksi ke berkas JSON. Simpan berkas di tempat aman.",color=Color.DarkGray)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick={backupOut.launch("catatan-keuangan-${LocalDate.now()}.json")},modifier=Modifier.fillMaxWidth()){Text("Ekspor / Cadangkan")}
                    OutlinedButton(onClick={backupIn.launch(arrayOf("application/json","text/*"))},modifier=Modifier.fillMaxWidth()){Text("Pulihkan dari cadangan")}
                    Text("Catatan: pemulihan akan mengganti seluruh data yang ada.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if(showAdd) TransactionDialog(null,{type,amount,cat,note,date->vm.add(type,amount,cat,note,date);showAdd=false},{showAdd=false})
    editing?.let { item -> TransactionDialog(item,{type,amount,cat,note,date->vm.update(item.copy(type=type,amount=amount,category=cat,note=note,date=date));editing=null},{editing=null}) }
}
@Composable fun MonthPicker(month:YearMonth,onChange:(YearMonth)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={onChange(month.minusMonths(1))}){Text("‹")}
        Text(month.month.name.lowercase().replaceFirstChar{it.uppercase()}+" "+month.year,fontWeight=FontWeight.Bold)
        TextButton(onClick={onChange(month.plusMonths(1))}){Text("›")}
    }
}
@Composable fun SummaryCard(label:String,value:Long) { Card(Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(14.dp)){Text(label,color=Color.Gray);Text(rupiah(value),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)}} }
@Composable fun BarCompare(income:Long,expense:Long) {
    val max=maxOf(income,expense,1L)
    Column {
        listOf("Pemasukan" to income,"Pengeluaran" to expense).forEach { (label,v)->
            Text("$label • ${rupiah(v)}",style=MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().height(18.dp).background(Color(0xFFE0E8E4),RoundedCornerShape(8.dp))) {
                Box(Modifier.fillMaxWidth((v.toFloat()/max).coerceIn(0f,1f)).fillMaxHeight().background(if(label=="Pemasukan")Color(0xFF14B889) else Color(0xFFFF9F43),RoundedCornerShape(8.dp)))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
@Composable fun TransactionList(items:List<Transaction>,edit:(Transaction)->Unit,delete:(Transaction)->Unit) {
    LazyColumn { items(items,key={it.id}) { item ->
        Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable{edit(item)},shape=RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(Color(0xFFE7F3EE), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(categoryIcon(item.category), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.note.ifBlank { item.date },fontWeight=FontWeight.SemiBold)
                    Text("${item.date} • ${if(item.type=="Pemasukan") "Pemasukan" else "Pengeluaran"}",style=MaterialTheme.typography.bodySmall,color=Color.Gray)
                }
                Column(horizontalAlignment=Alignment.End) { Text((if(item.type=="Pemasukan")"+" else "−")+rupiah(item.amount),fontWeight=FontWeight.Bold,color=if(item.type=="Pemasukan")Color(0xFF087F5B) else Color(0xFFC45B36)); TextButton(onClick={delete(item)}){Text("Hapus")} }
            }
        }
    } }
}

private fun categoryIcon(category: String): String = when (category.lowercase()) {
    "makanan", "makan", "makanan & minuman" -> "🍔"
    "rumah tangga", "rumah" -> "🏠"
    "transportasi" -> "🚗"
    "tagihan", "tagihan listrik" -> "🧾"
    "belanja" -> "🛍️"
    "kesehatan" -> "💊"
    "pendidikan" -> "📚"
    "gaji", "gaji bulanan" -> "💼"
    "bonus" -> "🎁"
    "usaha" -> "🏪"
    else -> "💰"
}

@Composable
fun MonthlyChart(all: List<Transaction>, selectedMonth: YearMonth) {
    val months = (5 downTo 0).map { selectedMonth.minusMonths(it.toLong()) }
    val values = months.map { m ->
        val rows = all.filter { it.date.startsWith(m.toString()) }
        rows.filter { it.type == "Pemasukan" }.sumOf { it.amount } to
            rows.filter { it.type == "Pengeluaran" }.sumOf { it.amount }
    }
    val maxValue = (values.flatMap { listOf(it.first, it.second) }.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(Color(0xFF14B889), RoundedCornerShape(50)))
                    Spacer(Modifier.width(5.dp))
                    Text("Pemasukan", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(Color(0xFFE65B78), RoundedCornerShape(50)))
                    Spacer(Modifier.width(5.dp))
                    Text("Pengeluaran", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(130.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                months.forEachIndexed { index, m ->
                    val (inc, exp) = values[index]
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(
                                Modifier.width(10.dp).fillMaxHeight((inc.toFloat() / maxValue).coerceIn(0f, 1f))
                                    .background(Color(0xFF14B889), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier.width(10.dp).fillMaxHeight((exp.toFloat() / maxValue).coerceIn(0f, 1f))
                                    .background(Color(0xFFE65B78), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(m.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable fun TransactionDialog(item:Transaction?,save:(String,Long,String,String,String)->Unit,dismiss:()->Unit) {
    var type by remember { mutableStateOf(item?.type ?: "Pengeluaran") }
    var amount by remember { mutableStateOf(item?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "Makanan") }
    var note by remember { mutableStateOf(item?.note ?: "") }
    var date by remember { mutableStateOf(item?.date ?: LocalDate.now().toString()) }
    val cats=if(type=="Pemasukan") listOf("Gaji","Bonus","Usaha","Lainnya") else listOf("Makanan","Rumah tangga","Transportasi","Tagihan","Belanja","Kesehatan","Pendidikan","Lainnya")
    AlertDialog(onDismissRequest=dismiss,title={Text(if(item==null)"Tambah transaksi" else "Edit transaksi")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row { FilterChip(selected=type=="Pengeluaran",onClick={type="Pengeluaran";category="Makanan"},label={Text("Pengeluaran")}); Spacer(Modifier.width(6.dp));FilterChip(selected=type=="Pemasukan",onClick={type="Pemasukan";category="Gaji"},label={Text("Pemasukan")}) }
            OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("Nominal (Rp)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
            Text("Kategori")
            Row { cats.take(4).forEach { c->FilterChip(selected=category==c,onClick={category=c},label={Text(c)}) } }
            if(cats.size>4) Row { cats.drop(4).forEach { c->FilterChip(selected=category==c,onClick={category=c},label={Text(c)}) } }
            OutlinedTextField(date,{date=it},label={Text("Tanggal (YYYY-MM-DD)")},singleLine=true)
            OutlinedTextField(note,{note=it},label={Text("Catatan")},singleLine=true)
        }
    },confirmButton={TextButton(onClick={amount.toLongOrNull()?.let{save(type,it,category,note,date)}}){Text("Simpan")}},dismissButton={TextButton(onClick=dismiss){Text("Batal")}})
}
