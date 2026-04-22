package com.qoffee.core.records

import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecordValidationResult
import com.qoffee.core.model.validate
import javax.inject.Inject

class RecordValidator @Inject constructor() {

    fun validate(record: CoffeeRecord, grinderProfile: GrinderProfile?): RecordValidationResult {
        val errors = buildList {
            record.waterCurve?.validate(record.brewMethod)?.forEach(::add)
            if (record.brewMethod == null) add("请选择制作方式。")
            if (record.beanProfileId == null) add("请选择咖啡豆。")
            if (record.coffeeDoseG == null || record.coffeeDoseG <= 0.0) add("请填写有效的咖啡粉重量。")
            if (record.brewWaterMl == null || record.brewWaterMl <= 0.0) add("请填写有效的冲煮水量。")
            if (record.brewMethod?.isHotBrew == true && (record.waterTempC == null || record.waterTempC <= 0.0)) {
                add("热冲煮方式需要填写水温。")
            }
            if (record.grinderProfileId != null && grinderProfile != null && record.grindSetting != null) {
                if (record.grindSetting < grinderProfile.minSetting || record.grindSetting > grinderProfile.maxSetting) {
                    add(
                        "研磨格数需要落在 ${grinderProfile.name} 的范围内" +
                            "（${grinderProfile.minSetting}-${grinderProfile.maxSetting}${grinderProfile.unitLabel}）。",
                    )
                }
            }
        }
        return if (errors.isEmpty()) RecordValidationResult.success() else RecordValidationResult.failure(errors)
    }
}
