package com.s24vision.app.core.settings

import android.content.Context
import com.s24vision.app.core.onnx.InferenceBackend
import com.s24vision.app.core.profiles.ProfileStore
import com.s24vision.app.core.profiles.ProfileType

/** Вкл/выкл встроенных моделей и обученных профилей для живого распознавания. */
class RecognitionSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("recognition_settings", Context.MODE_PRIVATE)

    fun inferenceBackend(): InferenceBackend =
        InferenceBackend.fromPref(prefs.getString(KEY_INFERENCE_BACKEND, InferenceBackend.NNAPI.prefKey)!!)

    fun setInferenceBackend(backend: InferenceBackend) {
        prefs.edit().putString(KEY_INFERENCE_BACKEND, backend.prefKey).apply()
    }

    fun droneDetVariant(): DroneDetVariant =
        DroneDetVariant.fromPref(prefs.getString(KEY_DRONE_DET_VARIANT, DroneDetVariant.S.prefKey)!!)

    fun setDroneDetVariant(variant: DroneDetVariant) {
        prefs.edit().putString(KEY_DRONE_DET_VARIANT, variant.prefKey).apply()
    }

    fun isBuiltinEnabled(id: String): Boolean =
        prefs.getBoolean(builtinKey(id), true)

    fun setBuiltinEnabled(id: String, enabled: Boolean) {
        prefs.edit().putBoolean(builtinKey(id), enabled).apply()
    }

    fun isProfileEnabled(type: ProfileType, name: String): Boolean =
        prefs.getBoolean(profileKey(type, name), true)

    fun setProfileEnabled(type: ProfileType, name: String, enabled: Boolean) {
        prefs.edit().putBoolean(profileKey(type, name), enabled).apply()
    }

    fun clearProfile(type: ProfileType, name: String) {
        prefs.edit().remove(profileKey(type, name)).apply()
    }

    fun isPersonDetectionEnabled(): Boolean =
        isBuiltinEnabled(BuiltinModels.FACE) || isBuiltinEnabled(BuiltinModels.BODY)

    /** Общая сцена RAM++ (стул, чашка, person от YOLOE…). */
    fun isYoloeEnabled(): Boolean = isBuiltinEnabled(BuiltinModels.YOLOE)

    fun hasEnabledObjectProfiles(store: ProfileStore): Boolean =
        isBuiltinEnabled(BuiltinModels.OBJECT_ENCODER) &&
            store.list(ProfileType.OBJECT).any { isProfileEnabled(ProfileType.OBJECT, it) }

    /**
     * YOLOE для объектов — только если есть обученные профили **кроме** drone.
     * Профиль drone обслуживает только drone_det; иначе при выкл. drone_det тайно включается YOLOE.
     */
    fun needsYoloeForObjectProfiles(store: ProfileStore): Boolean {
        if (!hasEnabledObjectProfiles(store)) return false
        return store.list(ProfileType.OBJECT)
            .filter { isProfileEnabled(ProfileType.OBJECT, it) }
            .any { !it.equals("drone", ignoreCase = true) }
    }

    private fun builtinKey(id: String) = "builtin:$id"

    private fun profileKey(type: ProfileType, name: String) = "profile:${type.name}:$name"

    companion object {
        private const val KEY_INFERENCE_BACKEND = "inference_backend"
        private const val KEY_DRONE_DET_VARIANT = "drone_det_variant"
    }
}
