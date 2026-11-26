package com.taller.sublimapro.domain

import com.taller.sublimapro.data.WorkshopConfigEntity

enum class Technique(val label: String) {
    SUBLIMACION("Sublimación"),
    DTF("DTF"),
    VINIL("Vinil Textil")
}

enum class InkCoverage(val label: String, val factor: Double) {
    NONE("Sin tinta (0%)", 0.0),
    LOW("Baja (25%)", 0.25),
    MEDIUM("Media (60%)", 0.60),
    HIGH("Alta (100%)", 1.0)
}

data class CostInput(
    val productName: String = "",
    val technique: Technique = Technique.SUBLIMACION,
    val widthCm: Double = 20.0,
    val heightCm: Double = 28.0,
    val quantity: Int = 1,
    val blankCostUsd: Double = 2.50,
    val materialCostPerSheetOrMeterUsd: Double = 0.25,
    val materialAreaCm2: Double = 623.7, // A4 por defecto (21 x 29.7 cm)
    val nestingWasteFactor: Double = 1.15, // +15% aprovechamiento de corte
    val inkCoverage: InkCoverage = InkCoverage.MEDIUM,
    val pressSeconds: Double = 60.0,
    val designMinutes: Double = 5.0,
    val operationMinutes: Double = 2.0,
    val wastePercent: Double = 5.0,
    val marginPercent: Double = 40.0
)

data class CostBreakdown(
    val areaCm2: Double,
    val blankCostUsd: Double,
    val materialCostUsd: Double,
    val inkCostUsd: Double,
    val energyAndWearUsd: Double,
    val laborCostUsd: Double,
    val subtotalCostUsd: Double,
    val wasteCostUsd: Double,
    val totalUnitCostUsd: Double,
    val suggestedUnitPriceUsd: Double,
    val unitProfitUsd: Double,
    val totalOrderPriceUsd: Double,
    val totalOrderCostUsd: Double,
    val exchangeRate: Double,
    val currencySymbol: String
) {
    val totalUnitCostLocal: Double get() = totalUnitCostUsd * exchangeRate
    val suggestedUnitPriceLocal: Double get() = suggestedUnitPriceUsd * exchangeRate
    val totalOrderPriceLocal: Double get() = totalOrderPriceUsd * exchangeRate
}

object CostCalculator {
    // Consumo estimado promedio: 0.0018 ml por cm2 al 100% cobertura
    private const val BASE_ML_PER_CM2 = 0.0018

    fun calculate(input: CostInput, config: WorkshopConfigEntity): CostBreakdown {
        val qty = input.quantity.coerceAtLeast(1)
        val areaCm2 = (input.widthCm * input.heightCm).coerceAtLeast(0.0)
        val usableArea = input.materialAreaCm2.coerceAtLeast(1.0)

        // 1. Costo del papel / DTF / vinil proporcional al área + aprovechamiento
        val costPerCm2 = input.materialCostPerSheetOrMeterUsd / usableArea
        val materialCost = areaCm2 * costPerCm2 * input.nestingWasteFactor

        // 2. Costo de tinta (en vinil textil es 0 salvo que se use tinta)
        val effectiveCoverage = if (input.technique == Technique.VINIL) 0.0 else input.inkCoverage.factor
        val inkMl = areaCm2 * BASE_ML_PER_CM2 * effectiveCoverage
        val inkCost = inkMl * config.inkCostPerMlUsd

        // 3. Electricidad + desgaste de teflón/resistencia + cinta térmica
        val electricityCost = (config.pressPowerWatts / 1000.0) * (input.pressSeconds / 3600.0) * config.electricityCostPerKwhUsd
        val tapeCost = if (input.technique == Technique.SUBLIMACION) config.thermalTapeCostPerPieceUsd else 0.0
        val energyAndWear = electricityCost + config.teflonWearPerPressUsd + tapeCost

        // 4. Mano de obra (diseño prorrateado por cantidad del lote + operación por unidad)
        val designPerUnit = (input.designMinutes * config.designRatePerMinuteUsd) / qty
        val operationPerUnit = input.operationMinutes * config.operatorRatePerMinuteUsd
        val laborCost = designPerUnit + operationPerUnit

        // 5. Subtotal y merma
        val subtotal = input.blankCostUsd + materialCost + inkCost + energyAndWear + laborCost
        val wasteCost = subtotal * (input.wastePercent.coerceIn(0.0, 90.0) / 100.0)
        val totalUnitCost = subtotal + wasteCost

        // 6. Precio de venta por margen real sobre venta
        val safeMargin = input.marginPercent.coerceIn(0.0, 90.0) / 100.0
        val suggestedPrice = if (safeMargin > 0.0) totalUnitCost / (1.0 - safeMargin) else totalUnitCost
        val profit = suggestedPrice - totalUnitCost

        return CostBreakdown(
            areaCm2 = areaCm2,
            blankCostUsd = input.blankCostUsd,
            materialCostUsd = materialCost,
            inkCostUsd = inkCost,
            energyAndWearUsd = energyAndWear,
            laborCostUsd = laborCost,
            subtotalCostUsd = subtotal,
            wasteCostUsd = wasteCost,
            totalUnitCostUsd = totalUnitCost,
            suggestedUnitPriceUsd = suggestedPrice,
            unitProfitUsd = profit,
            totalOrderPriceUsd = suggestedPrice * qty,
            totalOrderCostUsd = totalUnitCost * qty,
            exchangeRate = config.exchangeRate,
            currencySymbol = config.localCurrencySymbol
        )
    }
}
