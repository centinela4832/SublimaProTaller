package com.taller.sublimapro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taller.sublimapro.data.AppDatabase
import com.taller.sublimapro.data.CalculationRecordEntity
import com.taller.sublimapro.data.WorkshopConfigEntity
import com.taller.sublimapro.domain.CostBreakdown
import com.taller.sublimapro.domain.CostCalculator
import com.taller.sublimapro.domain.CostInput
import com.taller.sublimapro.domain.Technique
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).workshopDao()

    val config: StateFlow<WorkshopConfigEntity> = dao.observeConfig()
        .map { it ?: WorkshopConfigEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkshopConfigEntity())

    val records: StateFlow<List<CalculationRecordEntity>> = dao.observeRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _input = MutableStateFlow(CostInput())
    val input: StateFlow<CostInput> = _input.asStateFlow()

    val breakdown: StateFlow<CostBreakdown> = combine(_input, config) { currentInput, currentConfig ->
        CostCalculator.calculate(currentInput, currentConfig)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CostCalculator.calculate(CostInput(), WorkshopConfigEntity())
    )

    init {
        viewModelScope.launch {
            if (dao.getConfigOnce() == null) {
                dao.saveConfig(WorkshopConfigEntity())
            }
        }
    }

    fun updateInput(transform: (CostInput) -> CostInput) {
        _input.update(transform)
    }

    fun selectTechnique(technique: Technique) {
        _input.update { current ->
            when (technique) {
                Technique.SUBLIMACION -> current.copy(
                    technique = technique,
                    materialCostPerSheetOrMeterUsd = 0.25,
                    materialAreaCm2 = 623.7, // Hoja A4
                    pressSeconds = 60.0
                )
                Technique.DTF -> current.copy(
                    technique = technique,
                    materialCostPerSheetOrMeterUsd = 14.0,
                    materialAreaCm2 = 5800.0, // 1 metro de 58cm ancho
                    pressSeconds = 15.0
                )
                Technique.VINIL -> current.copy(
                    technique = technique,
                    materialCostPerSheetOrMeterUsd = 9.0,
                    materialAreaCm2 = 5000.0, // 1 metro de 50cm ancho
                    pressSeconds = 15.0
                )
            }
        }
    }

    fun saveCurrentCalculation() {
        val currentInput = _input.value
        val currentBreakdown = breakdown.value
        val currentConfig = config.value
        viewModelScope.launch {
            dao.insertRecord(
                CalculationRecordEntity(
                    productName = currentInput.productName.ifBlank { "Trabajo ${currentInput.technique.label}" },
                    technique = currentInput.technique.label,
                    widthCm = currentInput.widthCm,
                    heightCm = currentInput.heightCm,
                    quantity = currentInput.quantity.coerceAtLeast(1),
                    blankCostUsd = currentInput.blankCostUsd,
                    unitCostUsd = currentBreakdown.totalUnitCostUsd,
                    unitPriceUsd = currentBreakdown.suggestedUnitPriceUsd,
                    exchangeRateApplied = currentConfig.exchangeRate,
                    currencySymbol = currentConfig.localCurrencySymbol
                )
            )
        }
    }

    fun deleteRecord(record: CalculationRecordEntity) {
        viewModelScope.launch { dao.deleteRecord(record) }
    }

    fun loadRecord(record: CalculationRecordEntity) {
        val tech = Technique.entries.find { it.label == record.technique } ?: Technique.SUBLIMACION
        _input.update {
            it.copy(
                productName = record.productName,
                technique = tech,
                widthCm = record.widthCm,
                heightCm = record.heightCm,
                quantity = record.quantity,
                blankCostUsd = record.blankCostUsd
            )
        }
    }

    fun saveConfig(newConfig: WorkshopConfigEntity) {
        viewModelScope.launch {
            dao.saveConfig(newConfig)
            _input.update {
                it.copy(
                    wastePercent = newConfig.defaultWastePercent,
                    marginPercent = newConfig.defaultMarginPercent
                )
            }
        }
    }
}
