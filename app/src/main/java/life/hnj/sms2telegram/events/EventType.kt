package life.hnj.sms2telegram.events

enum class EventType(
    val keySuffix: String,
    val cliName: String,
) {
    SMS("sms", "sms"),
    MISSED_CALL("missed_call", "missed_call"),
    BATTERY_LOW("battery_low", "battery_low"),
    POWER_CONNECTED("power_connected", "power_connected"),
    POWER_DISCONNECTED("power_disconnected", "power_disconnected"),
    AIRPLANE_MODE("airplane_mode", "airplane_mode"),
    BOOT_COMPLETED("boot_completed", "boot_completed"),
    SHUTDOWN("shutdown", "shutdown"),
    SIM_STATE("sim_state", "sim_state");

    companion object {
        fun fromCliName(raw: String): EventType? {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull { it.cliName == normalized }
        }
    }
}
