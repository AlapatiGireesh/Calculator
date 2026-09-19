package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

enum class OemType(val displayName: String, val osName: String) {
    GOOGLE_PIXEL("Google Pixel", "Pixel UI / Android 14/15"),
    IQOO_VIVO("iQOO / Vivo", "Funtouch OS / OriginOS"),
    ONEPLUS_OPPO_REALME("OnePlus / Oppo / Realme", "OxygenOS / ColorOS"),
    INFINIX_TECNO("Infinix / Tecno", "XOS / HiOS (Transsion)"),
    SAMSUNG("Samsung", "One UI"),
    XIAOMI_REDMI_POCO("Xiaomi / Redmi", "HyperOS / MIUI"),
    OTHER("Android Device", "Stock Android")
}

data class DeviceDetail(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val displayModel: String,
    val androidVersion: String,
    val sdkInt: Int,
    val oemType: OemType,
    val hiddenAppsName: String,
    val accessMethod: String,
    val instructions: List<String>,
    val dialerCodeHint: String?
)

object DeviceHelper {

    private const val TAG = "DeviceHelper"

    fun getDeviceDetail(): DeviceDetail {
        val manufacturer = Build.MANUFACTURER?.trim() ?: ""
        val brand = Build.BRAND?.trim() ?: ""
        val model = Build.MODEL?.trim() ?: ""
        
        val mLower = manufacturer.lowercase()
        val bLower = brand.lowercase()
        val modLower = model.lowercase()

        val oemType: OemType = when {
            mLower.contains("google") || bLower.contains("google") || modLower.contains("pixel") -> {
                OemType.GOOGLE_PIXEL
            }
            mLower.contains("iqoo") || bLower.contains("iqoo") ||
            mLower.contains("vivo") || bLower.contains("vivo") ||
            modLower.contains("iqoo") || modLower.contains("vivo") -> {
                OemType.IQOO_VIVO
            }
            mLower.contains("oneplus") || bLower.contains("oneplus") ||
            mLower.contains("one plus") || bLower.contains("one plus") ||
            mLower.contains("oppo") || bLower.contains("oppo") ||
            mLower.contains("realme") || bLower.contains("realme") -> {
                OemType.ONEPLUS_OPPO_REALME
            }
            mLower.contains("infinix") || bLower.contains("infinix") ||
            mLower.contains("tecno") || bLower.contains("tecno") ||
            mLower.contains("itel") || bLower.contains("itel") ||
            mLower.contains("transsion") || bLower.contains("transsion") -> {
                OemType.INFINIX_TECNO
            }
            mLower.contains("samsung") || bLower.contains("samsung") -> {
                OemType.SAMSUNG
            }
            mLower.contains("xiaomi") || bLower.contains("xiaomi") ||
            mLower.contains("redmi") || bLower.contains("redmi") ||
            mLower.contains("poco") || bLower.contains("poco") -> {
                OemType.XIAOMI_REDMI_POCO
            }
            else -> {
                OemType.OTHER
            }
        }

        val displayModel = if (model.isNotEmpty()) {
            val brandPrefix = if (brand.isNotEmpty() && !model.lowercase().startsWith(brand.lowercase())) {
                brand.replaceFirstChar { it.uppercase() } + " "
            } else ""
            brandPrefix + model
        } else {
            manufacturer.replaceFirstChar { it.uppercase() } + " Device"
        }

        val (hiddenAppsName, accessMethod, instructions, dialerCodeHint) = when (oemType) {
            OemType.GOOGLE_PIXEL -> {
                Quadruple(
                    "Pixel Private Space & App Cloak",
                    "Private Space PIN / Auto-Cloak",
                    listOf(
                        "On Android 14/15+: Open Settings ➔ Security & privacy ➔ Private Space.",
                        "Tap 'Set up' to create your secure private space locked with a separate PIN or fingerprint.",
                        "Turn ON 'Hide Private Space when locked' to completely conceal the space from the app drawer.",
                        "Tap 'Move to Hidden Apps & Lock App' below to auto-cloak and prevent child access.",
                        "Live GPS coordinates stream continuously in the background to the Parent Dashboard."
                    ),
                    "Open via Private Space at bottom of App Drawer or Parent Remote Unlock"
                )
            }
            OemType.IQOO_VIVO -> {
                Quadruple(
                    "iManager App Hide & Privacy Encryption",
                    "Two-Finger Swipe Up / Privacy PIN",
                    listOf(
                        "Open 'iManager' app or go to Settings ➔ Security ➔ Privacy and app encryption.",
                        "Set or enter your Privacy Password / PIN.",
                        "Tap 'Hide App' (App Hiding).",
                        "Locate and enable the toggle for this tracking application.",
                        "Turn on 'View Hidden Apps' to allow access via two-finger upward swipe on Home Screen."
                    ),
                    "Swipe up with 2 fingers on Home Screen"
                )
            }
            OemType.ONEPLUS_OPPO_REALME -> {
                Quadruple(
                    "OxygenOS Privacy Hide Apps",
                    "Dialer Access Code (#pin#)",
                    listOf(
                        "Go to Settings ➔ Privacy ➔ Privacy tab ➔ Hide apps.",
                        "Enter your Privacy password / fingerprint.",
                        "Turn ON the toggle next to this tracking application.",
                        "Set an Access Code starting and ending with '#' (e.g. #1234#).",
                        "The app is now hidden from app drawer and only openable by dialing your access code in the Phone dialer."
                    ),
                    "Dial #AccessCode# in Phone app (e.g. #0000#)"
                )
            }
            OemType.INFINIX_TECNO -> {
                Quadruple(
                    "XHide / Phone Master Hidden Space",
                    "Dialer Code (##pin) or 2-Finger Pinch",
                    listOf(
                        "Open 'Phone Master' app ➔ Toolbox ➔ tap 'XHide' (or dial ##0000 / ##pin in Phone dialer).",
                        "Set or enter your 4-digit XHide password.",
                        "Tap 'Hidden Apps' ➔ tap the '+' icon.",
                        "Select this tracking application and tap 'Confirm'.",
                        "The app is now stored in XHide and cloaked from the main home screen."
                    ),
                    "Dial ##YourPIN in Phone app or 2-Finger Pinch on Home Screen"
                )
            }
            OemType.SAMSUNG -> {
                Quadruple(
                    "One UI Hide Apps on Home & Screens",
                    "Home Screen Settings / Secure Folder",
                    listOf(
                        "Pinch Home Screen or open Settings ➔ Home screen.",
                        "Tap 'Hide apps on Home and Apps screens'.",
                        "Select this application from the list and tap 'Done'.",
                        "To protect with password, add to 'Secure Folder'."
                    ),
                    "Search in Secure Folder or unhide in Home Screen settings"
                )
            }
            OemType.XIAOMI_REDMI_POCO -> {
                Quadruple(
                    "MIUI / HyperOS Hidden Apps",
                    "Security App Lock Hidden Apps tab",
                    listOf(
                        "Open 'Security' app ➔ scroll down to 'App lock'.",
                        "Switch to the 'Hidden apps' tab.",
                        "Turn ON the toggle for this tracking application.",
                        "Spread two fingers outward on Home Screen to open hidden folder."
                    ),
                    "Spread two fingers outward on Home Screen"
                )
            }
            OemType.OTHER -> {
                Quadruple(
                    "Android System Privacy & Cloaking",
                    "Private Space / App Cloak",
                    listOf(
                        "Android 15+: Settings ➔ Security & privacy ➔ Private Space.",
                        "Alternatively: Launcher cloaking will hide the app icon automatically upon closing.",
                        "Parent Dashboard controls whether this device is authorized to open the app UI."
                    ),
                    "Use Parent Dashboard remote unlock"
                )
            }
        }

        val androidVersionStr = "Android ${Build.VERSION.RELEASE ?: "Unknown"} (API ${Build.VERSION.SDK_INT})"

        return DeviceDetail(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            displayModel = displayModel,
            androidVersion = androidVersionStr,
            sdkInt = Build.VERSION.SDK_INT,
            oemType = oemType,
            hiddenAppsName = hiddenAppsName,
            accessMethod = accessMethod,
            instructions = instructions,
            dialerCodeHint = dialerCodeHint
        )
    }

    /**
     * Attempts to open the OEM's specific Hidden Apps / Privacy Vault settings.
     */
    fun openOemHideAppsSettings(context: Context, oemType: OemType) {
        val intents = mutableListOf<Intent>()

        when (oemType) {
            OemType.GOOGLE_PIXEL -> {
                // Google Pixel Private Space (Android 15+) / Security & Privacy settings
                intents.add(Intent("android.settings.PRIVATE_SPACE_SETTINGS"))
                intents.add(Intent("android.settings.PRIVACY_SETTINGS"))
                intents.add(Intent("android.settings.SECURITY_SETTINGS"))
                intents.add(Intent().apply {
                    component = ComponentName("com.android.settings", "com.android.settings.Settings\$SecurityDashboardActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.android.settings", "com.android.settings.Settings\$PrivacyDashboardActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.google.android.apps.nexuslauncher", "com.google.android.apps.nexuslauncher.SettingsActivity")
                })
                intents.add(Intent().apply {
                    action = "android.settings.BIOMETRIC_ENROLL"
                })
            }
            OemType.IQOO_VIVO -> {
                // iQOO & Vivo specific privacy and app hide intents
                intents.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.SoftwareManagerActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.safecenter", "com.vivo.safecenter.permission.PermissionManagerActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.safecenter", "com.vivo.safecenter.MainActivity")
                })
            }
            OemType.ONEPLUS_OPPO_REALME -> {
                // OnePlus, Oppo, Realme ColorOS / OxygenOS privacy & hide app intents
                intents.add(Intent().apply {
                    component = ComponentName("com.oplus.safecenter", "com.oplus.safecenter.privacy.PrivacySettingsActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.privacy.PrivacySettingsActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.privacy.AppLockAndHideActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.oplus.encryption", "com.oplus.encryption.PrivacyPasswordActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.oneplus.security", "com.oneplus.security.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.oplus.safecenter", "com.oplus.safecenter.MainActivity")
                })
                intents.add(Intent().apply {
                    action = "com.android.settings.Settings\$PrivacyDashboardActivity"
                })
            }
            OemType.INFINIX_TECNO -> {
                // Infinix, Tecno, Itel Transsion XOS / HiOS XHide intents
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.activity.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.xhide", "com.transsion.xhide.activity.XHideMainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.xhide", "com.transsion.xhide.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.xhide.XHideActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.smartpanel", "com.transsion.smartpanel.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.hilauncher", "com.transsion.hilauncher.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.XOSLauncher", "com.transsion.XOSLauncher.MainActivity")
                })
            }
            OemType.SAMSUNG -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.sec.android.app.launcher", "com.sec.android.app.launcher.activities.HideAppsActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.sec.knox.foldercontainer", "com.sec.knox.foldercontainer.SecureFolderLauncherActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.knox.securefolder", "com.samsung.knox.securefolder.presentation.switcher.SwitchFolderActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.grapf.AppLockActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.knox.containeragent", "com.samsung.android.knox.containeragent.ui.KnoxContainerActivity")
                })
            }
            OemType.XIAOMI_REDMI_POCO -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.applock.AppLockSettingsActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.appmanager.AppProtectActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.securityscan.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.hiddenapps.HiddenAppsActivity")
                })
            }
            OemType.OTHER -> {}
        }

        // Generic fallback intents
        intents.add(Intent("android.settings.PRIVACY_SETTINGS"))
        intents.add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
        intents.add(Intent(Settings.ACTION_SETTINGS))

        var launched = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                launched = true
                Log.d(TAG, "Successfully launched settings intent: ${intent.component ?: intent.action}")
                break
            } catch (e: Exception) {
                // Try next
            }
        }

        if (launched) {
            Toast.makeText(
                context,
                "Opening ${oemType.displayName} Hidden Apps / Privacy Vault...",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                "Please open Settings ➔ Privacy on your ${oemType.displayName} device.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Checks if notifications are currently enabled for the application.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val product = Build.PRODUCT ?: ""
        val hardware = Build.HARDWARE ?: ""
        val board = Build.BOARD ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val tags = Build.TAGS ?: ""

        val isVirtualHardware = hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || hardware.contains("virtio")
                || hardware.contains("cutf_cvm")
                || hardware.contains("vbox")
                || hardware.contains("nox")

        val isVirtualProduct = product.contains("sdk")
                || product.contains("google_sdk")
                || product.contains("emulator")
                || product.contains("simulator")
                || product.contains("vbox")
                || product.contains("nox")
                || product.contains("cuttlefish")
                || product.contains("vsoc")
                || product.contains("gce")

        val isVirtualDevice = device.contains("generic")
                || device.contains("vsoc")
                || device.contains("cuttlefish")
                || device.contains("emulator")

        val isVirtualFingerprint = fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || fingerprint.contains("sdk_gphone")
                || fingerprint.contains("cuttlefish")
                || fingerprint.contains("test-keys")

        val isVirtualBoardOrModel = board.contains("vsoc")
                || board.contains("gce")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")

        val isTestBuild = tags.contains("test-keys")

        return isVirtualHardware || isVirtualProduct || isVirtualDevice || isVirtualFingerprint || isVirtualBoardOrModel || isTestBuild
    }

    /**
     * Opens system notification settings for the application so the user can easily toggle notifications OFF,
     * completely hiding the foreground service notification while allowing 24/7 background tracking.
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Toggle 'Allow notifications' OFF to hide notification completely.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Open Settings ➔ Apps ➔ Notifications to hide.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
