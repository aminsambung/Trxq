package com.trxq.finance

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trxq.finance.data.AppDatabase
import com.trxq.finance.data.Transaction
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

val PrimaryBlue = Color(0xFF4A54E1)
val DarkBlue = Color(0xFF6A46E0)
val BackgroundGray = Color(0xFFF8F9FA)
val IncomeGreen = Color(0xFF4CAF50)
val ExpenseRed = Color(0xFFF44336)
val TextGray = Color(0xFF888888)
val OrangeExpense = Color(0xFFFFA726)

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val sharedPrefs = newBase.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val language = sharedPrefs.getString("My_Lang", "in") ?: "in"
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.get(this).transactionDao()
        setContent {
            val vm = remember { FinanceViewModel(dao) }
            var refreshKey by remember { mutableStateOf(0) }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = PrimaryBlue,
                    background = BackgroundGray,
                    surface = Color.White
                )
            ) {
                key(refreshKey) {
                    FinanceApp(
                        context = this,
                        vm = vm,
                        onLanguageChange = { lang ->
                            val sharedPrefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
                            sharedPrefs.edit().putString("My_Lang", lang).apply()
                            val locale = Locale(lang)
                            Locale.setDefault(locale)
                            val config = Configuration(resources.configuration)
                            config.setLocale(locale)
                            resources.updateConfiguration(config, resources.displayMetrics)
                            refreshKey++
                        }
                    )
                }
            }
        }
    }
}

private fun rupiah(n: Long): String = "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(n)
private fun monthKey(m: YearMonth) = m.toString()

@Composable
fun FinanceApp(context: Context, vm: FinanceViewModel, onLanguageChange: (String) -> Unit) {
    val all by vm.transactions.collectAsState(initial = emptyList())
    var month by remember { mutableStateOf(YearMonth.now()) }
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }

    val current = all.filter { it.date.startsWith(monthKey(month)) }
    val income = current.filter { it.type == "Pemasukan" }.sumOf { it.amount }
    val expense = current.filter { it.type == "Pengeluaran" }.sumOf { it.amount }

    val backupOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val arr = JSONArray()
            all.forEach { arr.put(JSONObject().put("id", it.id).put("type", it.type).put("amount", it.amount).put("category", it.category).put("note", it.note).put("date", it.date)) }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(arr.toString(2)) }
        }
    }
    val backupIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "[]"
            val arr = JSONArray(text); val items = mutableListOf<Transaction>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Transaction(type = o.getString("type"), amount = o.getLong("amount"), category = o.getString("category"), note = o.optString("note"), date = o.getString("date")))
            }
            vm.replace(items)
        } catch (_: Exception) {}
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val navItems = listOf(
                    Triple(stringResource(id = R.string.nav_summary), Icons.Default.Home, 0),
                    Triple(stringResource(id = R.string.nav_transactions), Icons.Default.List, 1),
                    Triple(stringResource(id = R.string.nav_report), Icons.Default.PieChart, 2),
                    Triple(stringResource(id = R.string.settings), Icons.Default.Settings, 3)
                )
                navItems.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0 || tab == 1) {
                FloatingActionButton(onClick = { showAdd = true }, containerColor = PrimaryBlue) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { pad ->
        Column(Modifier.fillMaxSize().background(BackgroundGray).padding(pad)) {
            when (tab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                    ) {
                        item {
                            Text(stringResource(id = R.string.app_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Spacer(modifier = Modifier.height(12.dp))
                            MonthPicker(month) { month = it }
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Box(modifier = Modifier.background(Brush.linearGradient(colors = listOf(PrimaryBlue, DarkBlue))).padding(24.dp).fillMaxWidth()) {
                                    Column {
                                        Text(stringResource(id = R.string.total_balance), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                                        Text(rupiah(income - expense), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text(stringResource(id = R.string.income), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                                Text(rupiah(income), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(stringResource(id = R.string.expense), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                                Text(rupiah(expense), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            Text(stringResource(id = R.string.comparison), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            BarCompare(income, expense)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            Text(stringResource(id = R.string.chart_6_months), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            MonthlyChart(all, month)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            Text(stringResource(id = R.string.recent_transactions), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        items(current.take(5), key = { it.id }) { item ->
                            TransactionItemCard(item, onClick = { editing = it }, onDelete = { vm.delete(it) })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                1 -> {
                    Column(Modifier.padding(16.dp)) {
                        MonthPicker(month) { month = it }
                        LazyColumn {
                            items(current, key = { it.id }) { item ->
                                TransactionItemCard(item, onClick = { editing = it }, onDelete = { vm.delete(it) })
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                2 -> {
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        MonthPicker(month) { month = it }
                        SummaryCard(stringResource(id = R.string.income), income)
                        SummaryCard(stringResource(id = R.string.expense), expense)
                        SummaryCard(stringResource(id = R.string.total_balance), income - expense)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Pengeluaran per kategori", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        val cats = mutableMapOf<String, Long>()
                        current.filter { it.type == "Pengeluaran" }.forEach { cats[it.category] = (cats[it.category] ?: 0L) + it.amount }
                        cats.forEach { (name, value) ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(categoryIcon(name), fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(rupiah(value), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { if (expense > 0) value.toFloat() / expense else 0f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = PrimaryBlue,
                                    trackColor = Color.LightGray.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
                3 -> {
                    SettingsScreen(context = context, onLanguageChange = onLanguageChange)
                }
            }
        }
    }

    if (showAdd) {
        TransactionDialog(null, save = { type, amount, cat, note, date ->
            vm.add(type, amount, cat, note, date); showAdd = false
        }, dismiss = { showAdd = false })
    }
    editing?.let { item ->
        TransactionDialog(item, save = { type, amount, cat, note, date ->
            vm.update(item.copy(type = type, amount = amount, category = cat, note = note, date = date)); editing = null
        }, dismiss = { editing = null })
    }
}

@Composable
fun SettingsScreen(context: Context, onLanguageChange: (String) -> Unit) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    val sharedPrefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
    val currentLang = sharedPrefs.getString("My_Lang", "in") ?: "in"

    Column(modifier = Modifier.fillMaxSize().background(BackgroundGray).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(id = R.string.settings), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(id = R.string.general), fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Color(0xFF9C27B0), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(id = R.string.language), fontWeight = FontWeight.Medium)
                        Text(text = if (currentLang == "in") "🇮🇩 ${stringResource(id = R.string.indonesian)}" else "🇬🇧 ${stringResource(id = R.string.english)}", fontSize = 12.sp, color = TextGray)
                    }
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextGray)
                }
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(id = R.string.language), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().clickable { onLanguageChange("in"); showLanguageDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🇮🇩 ${stringResource(id = R.string.indonesian)}", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        if (currentLang == "in") Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue)
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable { onLanguageChange("en"); showLanguageDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🇬🇧 ${stringResource(id = R.string.english)}", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        if (currentLang == "en") Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(id = R.string.cancel), color = TextGray) } }
        )
    }
}

@Composable
fun MonthPicker(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null) }
        Text(month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + month.year, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        IconButton(onClick = { onChange(month.plusMonths(1)) }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) }
    }
}

@Composable
fun BarCompare(income: Long, expense: Long) {
    val max = maxOf(income, expense, 1L)
    Column {
        listOf(stringResource(id = R.string.income) to income, stringResource(id = R.string.expense) to expense).forEach { (label, v) ->
            Text("$label • ${rupiah(v)}", fontSize = 12.sp, color = TextGray)
            Spacer(modifier = Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp))) {
                Box(Modifier.fillMaxWidth((v.toFloat() / max).coerceIn(0f, 1f)).fillMaxHeight().background(if (label == stringResource(id = R.string.income)) IncomeGreen else OrangeExpense, RoundedCornerShape(6.dp)))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SummaryCard(label: String, value: Long) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = TextGray, fontSize = 14.sp)
            Text(rupiah(value), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
        }
    }
}

@Composable
fun TransactionItemCard(item: Transaction, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(BackgroundGray), contentAlignment = Alignment.Center) {
                Text(categoryIcon(item.category), fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${item.date} • ${if (item.type == "Pemasukan") stringResource(id = R.string.income) else stringResource(id = R.string.expense)}", color = TextGray, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (item.type == "Pemasukan") "+" else "-") + rupiah(item.amount), color = if (item.type == "Pemasukan") IncomeGreen else ExpenseRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                    Text(stringResource(id = R.string.delete), color = PrimaryBlue, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MonthlyChart(all: List<Transaction>, selectedMonth: YearMonth) {
    val months = (5 downTo 0).map { selectedMonth.minusMonths(it.toLong()) }
    val values = months.map { m ->
        val rows = all.filter { it.date.startsWith(m.toString()) }
        rows.filter { it.type == "Pemasukan" }.sumOf { it.amount } to rows.filter { it.type == "Pengeluaran" }.sumOf { it.amount }
    }
    val maxValue = (values.flatMap { listOf(it.first, it.second) }.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(IncomeGreen, CircleShape)); Spacer(modifier = Modifier.width(6.dp)); Text(stringResource(id = R.string.income), fontSize = 12.sp, color = TextGray) }
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(Color(0xFFE65B78), CircleShape)); Spacer(modifier = Modifier.width(6.dp)); Text(stringResource(id = R.string.expense), fontSize = 12.sp, color = TextGray) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                months.forEachIndexed { index, m ->
                    val (inc, exp) = values[index]
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.width(12.dp).fillMaxHeight((inc.toFloat() / maxValue).coerceIn(0.01f, 1f)).background(IncomeGreen, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.width(12.dp).fillMaxHeight((exp.toFloat() / maxValue).coerceIn(0.01f, 1f)).background(Color(0xFFE65B78), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(m.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = TextGray)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionDialog(item: Transaction?, save: (String, Long, String, String, String) -> Unit, dismiss: () -> Unit) {
    var type by remember { mutableStateOf(item?.type ?: "Pengeluaran") }
    var amount by remember { mutableStateOf(item?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "Makanan") }
    var note by remember { mutableStateOf(item?.note ?: "") }
    var date by remember { mutableStateOf(item?.date ?: LocalDate.now().toString()) }
    val cats = if (type == "Pemasukan") listOf("Gaji", "Bonus", "Usaha", "Lainnya") else listOf("Makanan", "Rumah tangga", "Transportasi", "Tagihan", "Belanja", "Kesehatan", "Pendidikan", "Lainnya")
    
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (item == null) stringResource(id = R.string.add_transaction) else stringResource(id = R.string.edit_transaction), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "Pengeluaran", onClick = { type = "Pengeluaran"; category = "Makanan" }, label = { Text(stringResource(id = R.string.expense)) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = type == "Pemasukan", onClick = { type = "Pemasukan"; category = "Gaji" }, label = { Text(stringResource(id = R.string.income)) }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text(stringResource(id = R.string.amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(id = R.string.category), fontSize = 12.sp, color = TextGray)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
                }
                OutlinedTextField(date, { date = it }, label = { Text(stringResource(id = R.string.date)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(id = R.string.note)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { amount.toLongOrNull()?.let { save(type, it, category, note, date) } }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(id = R.string.cancel), color = TextGray) } }
    )
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
