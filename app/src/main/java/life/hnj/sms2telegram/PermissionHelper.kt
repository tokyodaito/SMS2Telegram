package life.hnj.sms2telegram

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {
    fun getMissingDangerousPermissions(context: Context): List<String> {
        val packageManager = context.packageManager
        val packageInfo = getPackageInfo(packageManager, context.packageName)
        val requestedPermissions = packageInfo.requestedPermissions?.toList().orEmpty()

        return requestedPermissions.filter { permission ->
            isDangerousPermission(packageManager, permission) &&
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }
    }

    private fun isDangerousPermission(packageManager: PackageManager, permission: String): Boolean {
        val info = runCatching { packageManager.getPermissionInfo(permission, 0) }.getOrNull()
            ?: return false
        val protection = info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        return protection == PermissionInfo.PROTECTION_DANGEROUS
    }
}
