package com.example.mesh3d

import org.json.JSONArray
import org.json.JSONObject

object AssemblySerializer {

    fun partsToJson(parts: List<AssemblyPart>): String {
        val array = JSONArray()
        for (p in parts) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("profileType", p.profileType)
                put("lengthMm", p.lengthMm.toDouble())
                put("widthMm", p.widthMm.toDouble())
                put("heightMm", p.heightMm.toDouble())
                put("thicknessMm", p.thicknessMm.toDouble())
                put("posX", p.posX.toDouble())
                put("posY", p.posY.toDouble())
                put("posZ", p.posZ.toDouble())
                put("rotPitch", p.rotPitch.toDouble())
                put("rotYaw", p.rotYaw.toDouble())
                put("rotRoll", p.rotRoll.toDouble())
                put("color", p.color)
                put("isVisible", p.isVisible)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToParts(json: String): List<AssemblyPart> {
        val result = mutableListOf<AssemblyPart>()
        if (json.isBlank()) return result
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    AssemblyPart(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Batang #${i + 1}"),
                        profileType = obj.optString("profileType", "Hollow Box"),
                        lengthMm = obj.optDouble("lengthMm", 1000.0).toFloat(),
                        widthMm = obj.optDouble("widthMm", 40.0).toFloat(),
                        heightMm = obj.optDouble("heightMm", 40.0).toFloat(),
                        thicknessMm = obj.optDouble("thicknessMm", 1.35).toFloat(),
                        posX = obj.optDouble("posX", 0.0).toFloat(),
                        posY = obj.optDouble("posY", 0.0).toFloat(),
                        posZ = obj.optDouble("posZ", 0.0).toFloat(),
                        rotPitch = obj.optDouble("rotPitch", 0.0).toFloat(),
                        rotYaw = obj.optDouble("rotYaw", 0.0).toFloat(),
                        rotRoll = obj.optDouble("rotRoll", 0.0).toFloat(),
                        color = obj.optString("color", "Silver"),
                        isVisible = obj.optBoolean("isVisible", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
