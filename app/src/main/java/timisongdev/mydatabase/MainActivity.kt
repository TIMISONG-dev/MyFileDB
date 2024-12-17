package timisongdev.mydatabase

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.poi.ss.usermodel.WorkbookFactory
import timisongdev.mydatabase.ui.theme.MyDataBaseTheme

class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyDataBaseTheme {
                Scaffold {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onFileSelected: (Uri, Context, Boolean) -> Unit) {
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onFileSelected(it, context, false) }
    }

    val jsonPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onFileSelected(it, context, true) }
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Выберите файл", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row {
            Button(onClick = { filePickerLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) {
                Text("Открыть Excel")
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = { jsonPickerLauncher.launch(arrayOf("application/json")) }) {
                Text("Открыть JSON")
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    val titles = listOf(
        "Главная",
        "Объекты",
        "Работы",
        "Справочники",
        "Сотрудники",
        "Ошибка"
    )

    val icons = listOf(
        R.drawable.ic_home,
        R.drawable.ic_item,
        R.drawable.ic_work,
        R.drawable.ic_table,
        R.drawable.ic_people,
        R.drawable.ic_not_filtered
    )

    var databaseLoaded by remember { mutableStateOf(false) }
    var excelData by remember { mutableStateOf<Map<String, List<Map<String, String>>>>(emptyMap()) }
    val context = LocalContext.current

    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            saveJson(excelData, it, context)
        }
    }

    if (!databaseLoaded) {
        HomeScreen { uri, context, isJson ->
            if (isJson) {
                excelData = loadJsonFile(context, uri)
            } else {
                excelData = processExcelFile(context, uri)
                saveFileLauncher.launch("${getFileName(context, uri)}.json")
            }
            databaseLoaded = true
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        titles.forEachIndexed { index, title ->
                            NavigationBarItem(
                                icon = {
                                    Icon(painterResource(icons[index]), contentDescription = null)
                                },
                                label = {
                                    Text(title, maxLines = 1)
                                },
                                selected = selectedTab == index,
                                onClick = {
                                    selectedTab = index
                                }
                            )
                        }
                    }
                }
            }
        ) {
            when (selectedTab) {
                0 -> HomeScreen { uri, context, isJson ->
                    if (isJson) {
                        excelData = loadJsonFile(context, uri)
                    } else {
                        excelData = processExcelFile(context, uri)
                        saveFileLauncher.launch("${getFileName(context, uri)}.json")
                    }
                    databaseLoaded = true
                }
                1 -> DataScreen(excelData["Объекты"] ?: emptyList(), "Объекты")
                2 -> DataScreen(excelData["Работы"] ?: emptyList(), "Работы")
                3 -> DataScreen(excelData["Справочники"] ?: emptyList(), "Справочники")
                4 -> DataScreen(excelData["Сотрудники"] ?: emptyList(), "Сотрудники")
                5 -> DataScreen(excelData["Не отсортированное"] ?: emptyList(), "Не отсортированное")
            }
        }
    }
}

@Composable
fun DataScreen(items: List<Map<String, String>>, title: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp, 48.dp, 24.dp, 24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn {
            items(items) { item ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        item.forEach { (key, value) ->
                            Text("$key: $value", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

fun processExcelFile(context: Context, uri: Uri): Map<String, List<Map<String, String>>> {
    val data = mutableMapOf<String, MutableList<Map<String, String>>>()
    val unsortedData = mutableListOf<Map<String, String>>() // Для неотсортированных данных

    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val workbook = WorkbookFactory.create(inputStream)

        for (i in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(i)
            val rows = mutableListOf<Map<String, String>>()

            val header = sheet.getRow(0).map { it.toString() }

            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex)
                val rowData = header.zip(row.map { it?.toString() ?: "" }).toMap()

                when {
                    rowData.containsKey("Объект") -> data.getOrPut("Объекты") { mutableListOf() }.add(rowData)
                    rowData.containsKey("Работа") -> data.getOrPut("Работы") { mutableListOf() }.add(rowData)
                    rowData.containsKey("Справочник") -> data.getOrPut("Справочники") { mutableListOf() }.add(rowData)
                    rowData.containsKey("Сотрудник") -> data.getOrPut("Сотрудники") { mutableListOf() }.add(rowData)
                    else -> unsortedData.add(rowData) // Если не удаётся сопоставить, добавляем в "Не отсортированное"
                }
            }

            data[sheet.sheetName] = rows
        }
    }

    if (unsortedData.isNotEmpty()) {
        data["Не отсортированное"] = unsortedData
    }

    return data
}

fun saveJson(data: Map<String, List<Map<String, String>>>, uri: Uri, context: Context) {
    val gson = Gson()
    val jsonString = gson.toJson(data)
    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        outputStream.write(jsonString.toByteArray())
    }
}

fun loadJsonFile(context: Context, uri: Uri): Map<String, List<Map<String, String>>> {
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val json = inputStream.reader().readText()
        val type = object : TypeToken<Map<String, List<Map<String, String>>>>() {}.type
        return Gson().fromJson(json, type)
    }
    return emptyMap()
}

fun getFileName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && it.moveToFirst()) {
            return it.getString(nameIndex).substringBeforeLast(".")
        }
    }
    return "unknown_file"
}
