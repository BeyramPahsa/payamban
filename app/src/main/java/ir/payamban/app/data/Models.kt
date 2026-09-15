package ir.payamban.app.data

import ir.payamban.app.classify.Category
import ir.payamban.app.classify.DiscountCode
import ir.payamban.app.classify.Verdict

data class Message(
    val id: Long,
    val threadId: Long,
    val sender: String,
    val canonicalSender: String,
    val body: String,
    val date: Long,
    val incoming: Boolean,
    val read: Boolean
)

data class ClassifiedMessage(
    val message: Message,
    val verdict: Verdict,
    val contactName: String?
) {
    val category: Category get() = verdict.category
    val displayName: String get() = contactName ?: message.sender
}

data class SenderGroup(
    val canonicalSender: String,
    val displayName: String,
    val messages: List<ClassifiedMessage>
) {
    val count: Int get() = messages.size
    val lastDate: Long get() = messages.maxOfOrNull { it.message.date } ?: 0L
    val preview: String get() = messages.maxByOrNull { it.message.date }?.message?.body.orEmpty()
}

data class SavedCode(
    val code: DiscountCode,
    val sourceName: String,
    val sourceSender: String,
    val receivedAt: Long,
    val body: String
)
