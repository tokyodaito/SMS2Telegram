package life.hnj.sms2telegram.events

data class PhoneEvent(
    val type: EventType,
    val title: String,
    val body: String,
    val metadata: Map<String, String> = emptyMap(),
)
