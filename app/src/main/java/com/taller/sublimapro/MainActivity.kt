package com.taller.sublimapro

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taller.sublimapro.data.CalculationRecordEntity
import com.taller.sublimapro.data.WorkshopConfigEntity
import com.taller.sublimapro.domain.CostBreakdown
import com.taller.sublimapro.domain.CostInput
import com.taller.sublimapro.domain.InkCoverage
import com.taller.sublimapro.domain.Technique
import com.taller.sublimapro.ui.MainViewModel
import com.taller.sublimapro.util.QuotePdfHelper
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                SublimaProApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SublimaProApp(vm: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val config by vm.config.collectAsState()
    val input by vm.input.collectAsState()
    val breakdown by vm.breakdown.collectAsState()
    val records by vm.records.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(config.workshopName, fontWeight = FontWeight.Bold)
                        Text(
                            "Tasa: 1 USD = ${fmt(config.exchangeRate)} ${config.localCurrencySymbol} • Offline",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Calculate, contentDescription = null) },
                    label = { Text("Calculadora") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Historial") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Taller / Tasa") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> CalculatorScreen(
                    config = config,
                    input = input,
                    breakdown = breakdown,
                    onSelectTechnique = vm::selectTechnique,
                    onUpdateInput = vm::updateInput,
                    onSave = vm::saveCurrentCalculation
                )
                1 -> HistoryScreen(
                    records = records,
                    onSelect = {
                        vm.loadRecord(it)
                        selectedTab = 0
                    },
                    onDelete = vm::deleteRecord
                )
                2 -> SettingsScreen(
                    config = config,
                    onSaveConfig = vm::saveConfig
                )
            }
        }
    }
}

@Composable
fun CalculatorScreen(
    config: WorkshopConfigEntity,
    input: CostInput,
    breakdown: CostBreakdown,
    onSelectTechnique: (Technique) -> Unit,
    onUpdateInput: ((CostInput) -> CostInput) -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tarjeta de Resultado Principal
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Precio Sugerido de Venta", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "$${fmt(breakdown.suggestedUnitPriceUsd)} USD c/u",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${breakdown.currencySymbol} ${fmt(breakdown.suggestedUnitPriceLocal)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Costo Real Unitario: $${fmt(breakdown.totalUnitCostUsd)} | Ganancia: $${fmt(breakdown.unitProfitUsd)}")
                Text(
                    "Total Lote (${input.quantity} u.): $${fmt(breakdown.totalOrderPriceUsd)} USD (${breakdown.currencySymbol} ${fmt(breakdown.totalOrderPriceLocal)})",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Desglose -> Blank: $${fmt(breakdown.blankCostUsd)} | Mat: $${fmt(breakdown.materialCostUsd)} | Tinta: $${fmt(breakdown.inkCostUsd)} | Luz/Desg: $${fmt(breakdown.energyAndWearUsd)} | M.Obra: $${fmt(breakdown.laborCostUsd)} | Merma: $${fmt(breakdown.wasteCostUsd)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Botones de acción rápida
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave()
                    Toast.makeText(context, "Guardado en historial local", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Guardar")
            }
            FilledTonalButton(
                onClick = { QuotePdfHelper.generateAndSharePdf(context, config.workshopName, input, breakdown) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("PDF")
            }
            FilledTonalButton(
                onClick = { QuotePdfHelper.shareTextSummary(context, config.workshopName, input, breakdown) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("WhatsApp")
            }
        }

        // Selector de Técnica
        Text("Técnica de Estampado", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Technique.entries.forEach { tech ->
                FilterChip(
                    selected = input.technique == tech,
                    onClick = { onSelectTechnique(tech) },
                    label = { Text(tech.label) }
                )
            }
        }

        OutlinedTextField(
            value = input.productName,
            onValueChange = { txt -> onUpdateInput { it.copy(productName = txt) } },
            label = { Text("Nombre del producto (ej. Franela Microdurazno / Taza 11oz)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Ancho (cm)", input.widthCm, Modifier.weight(1f)) { v -> onUpdateInput { it.copy(widthCm = v) } }
            NumField("Alto (cm)", input.heightCm, Modifier.weight(1f)) { v -> onUpdateInput { it.copy(heightCm = v) } }
            NumField("Cantidad", input.quantity.toDouble(), Modifier.weight(1f)) { v ->
                onUpdateInput { it.copy(quantity = v.toInt().coerceAtLeast(1)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Costo Blank ($)", input.blankCostUsd, Modifier.weight(1f)) { v -> onUpdateInput { it.copy(blankCostUsd = v) } }
            NumField("Costo Hoja/Metro ($)", input.materialCostPerSheetOrMeterUsd, Modifier.weight(1f)) { v ->
                onUpdateInput { it.copy(materialCostPerSheetOrMeterUsd = v) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Área Hoja/Metro (cm²)", input.materialAreaCm2, Modifier.weight(1f)) { v ->
                onUpdateInput { it.copy(materialAreaCm2 = v) }
            }
            NumField("Tiempo Plancha (seg)", input.pressSeconds, Modifier.weight(1f)) { v ->
                onUpdateInput { it.copy(pressSeconds = v) }
            }
        }

        if (input.technique != Technique.VINIL) {
            Text("Cobertura de Tinta", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InkCoverage.entries.filter { it != InkCoverage.NONE }.forEach { cov ->
                    FilterChip(
                        selected = input.inkCoverage == cov,
                        onClick = { onUpdateInput { it.copy(inkCoverage = cov) } },
                        label = { Text(cov.label) }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Min. Diseño (lote)", input.designMinutes, Modifier.weight(1f)) { v ->
                onUpdateInput { it.copy(designMinutes = v) }
            }
            NumField("Min. Prensado (c/u)", input.operationMinutes, Modifier.weight(1f)) { v ->
                onUpdateInput { it.copy(operationMinutes = v) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("% Merma / Fallo", input.wastePercent, Modifier.weight(1f)) { v ->
                onUpdateInput { it.copy(wastePercent = v) }
            }
            NumField("% Margen Ganancia", input.marginPercent, Modifier.weight(1f)) { v ->
                onUpdateInput { it.copy(marginPercent = v) }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    records: List<CalculationRecordEntity>,
    onSelect: (CalculationRecordEntity) -> Unit,
    onDelete: (CalculationRecordEntity) -> Unit
) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay cotizaciones guardadas aún.")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(records, key = { it.id }) { item ->
            val dateStr = remember(item.createdAt) {
                SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(item.createdAt))
            }
            Card(
                onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.productName, fontWeight = FontWeight.Bold)
                        Text("${item.technique} • ${fmt(item.widthCm)}x${fmt(item.heightCm)} cm • Cant: ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Venta: $${fmt(item.unitPriceUsd)} (${item.currencySymbol} ${fmt(item.unitPriceUsd * item.exchangeRateApplied)}) | Costo: $${fmt(item.unitCostUsd)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Guardado: $dateStr", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    config: WorkshopConfigEntity,
    onSaveConfig: (WorkshopConfigEntity) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(config) { mutableStateOf(config) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configuración del Taller y Tasa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = draft.workshopName,
            onValueChange = { draft = draft.copy(workshopName = it) },
            label = { Text("Nombre de tu Taller / Negocio") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.localCurrencySymbol,
                onValueChange = { draft = draft.copy(localCurrencySymbol = it) },
                label = { Text("Moneda Local (ej. Bs.)") },
                modifier = Modifier.weight(1f)
            )
            NumField("Tasa del Día (por 1 USD)", draft.exchangeRate, Modifier.weight(1.5f)) {
                draft = draft.copy(exchangeRate = it)
            }
        }

        HorizontalDivider()
        Text("Parámetros de Costos Operativos (USD)", fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Potencia Plancha (W)", draft.pressPowerWatts, Modifier.weight(1f)) {
                draft = draft.copy(pressPowerWatts = it)
            }
            NumField("Costo kWh ($)", draft.electricityCostPerKwhUsd, Modifier.weight(1f)) {
                draft = draft.copy(electricityCostPerKwhUsd = it)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Desgaste Teflón/c.u ($)", draft.teflonWearPerPressUsd, Modifier.weight(1f)) {
                draft = draft.copy(teflonWearPerPressUsd = it)
            }
            NumField("Tinta por ml ($)", draft.inkCostPerMlUsd, Modifier.weight(1f)) {
                draft = draft.copy(inkCostPerMlUsd = it)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Tarifa Diseño/min ($)", draft.designRatePerMinuteUsd, Modifier.weight(1f)) {
                draft = draft.copy(designRatePerMinuteUsd = it)
            }
            NumField("Tarifa Operario/min ($)", draft.operatorRatePerMinuteUsd, Modifier.weight(1f)) {
                draft = draft.copy(operatorRatePerMinuteUsd = it)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Cinta Térmica/c.u ($)", draft.thermalTapeCostPerPieceUsd, Modifier.weight(1f)) {
                draft = draft.copy(thermalTapeCostPerPieceUsd = it)
            }
            NumField("Merma defecto (%)", draft.defaultWastePercent, Modifier.weight(1f)) {
                draft = draft.copy(defaultWastePercent = it)
            }
        }

        Button(
            onClick = {
                onSaveConfig(draft)
                Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, null)
            Spacer(Modifier.width(8.dp))
            Text("Guardar Configuración")
        }
    }
}

@Composable
fun NumField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit
) {
    var text by remember(value) {
        mutableStateOf(if (value % 1.0 == 0.0) value.toInt().toString() else value.toString())
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val sanitized = raw.replace(',', '.')
            text = sanitized
            sanitized.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
