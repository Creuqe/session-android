package org.session.libsession.messaging.sending_receiving

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ClosedGroupUpdate
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.control.ReadReceipt
import org.session.libsession.messaging.messages.control.TypingIndicator
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.libsignal.util.Hex

import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchet
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchetCollectionType
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysImplementation
import org.session.libsignal.service.loki.utilities.toHexString
import java.util.*
import kotlin.collections.ArrayList

internal fun MessageReceiver.isBlock(publicKey: String): Boolean {
    // TODO: move isBlocked from Recipient to BlockManager
    return false
}

fun MessageReceiver.handle(message: Message, proto: SignalServiceProtos.Content, openGroupID: String?) {
    when (message) {
        is ReadReceipt -> handleReadReceipt(message)
        is TypingIndicator -> handleTypingIndicator(message)
        is ClosedGroupUpdate -> handleClosedGroupUpdate(message)
        is ExpirationTimerUpdate -> handleExpirationTimerUpdate(message)
        is VisibleMessage -> handleVisibleMessage(message, proto, openGroupID)
    }
}

private fun MessageReceiver.handleReadReceipt(message: ReadReceipt) {
    // TODO
}

private fun MessageReceiver.handleTypingIndicator(message: TypingIndicator) {
    when (message.kind!!) {
        TypingIndicator.Kind.STARTED -> showTypingIndicatorIfNeeded(message.sender!!)
        TypingIndicator.Kind.STOPPED -> hideTypingIndicatorIfNeeded(message.sender!!)
    }
}

fun MessageReceiver.showTypingIndicatorIfNeeded(senderPublicKey: String) {

}

fun MessageReceiver.hideTypingIndicatorIfNeeded(senderPublicKey: String) {

}

fun MessageReceiver.cancelTypingIndicatorsIfNeeded(senderPublicKey: String) {

}

private fun MessageReceiver.handleExpirationTimerUpdate(message: ExpirationTimerUpdate) {
    if (message.duration!! > 0) {
        setExpirationTimer(message.duration!!, message.sender!!, message.groupPublicKey)
    } else {
        disableExpirationTimer(message.sender!!, message.groupPublicKey)
    }
}

fun MessageReceiver.setExpirationTimer(duration: Int, senderPublicKey: String, groupPublicKey: String?) {

}

fun MessageReceiver.disableExpirationTimer(senderPublicKey: String, groupPublicKey: String?) {

}

fun MessageReceiver.handleVisibleMessage(message: VisibleMessage, proto: SignalServiceProtos.Content, openGroupID: String?) {
    val storage = MessagingConfiguration.shared.storage
    // Parse & persist attachments
    val attachments = proto.dataMessage.attachmentsList.mapNotNull { proto ->
        val attachment = Attachment.fromProto(proto)
        if (attachment == null || !attachment.isValid()) {
            return@mapNotNull null
        } else {
            return@mapNotNull attachment
        }
    }
    val attachmentIDs = storage.persist(attachments)
    message.attachmentIDs = attachmentIDs as ArrayList<String>
    var attachmentsToDownload = attachmentIDs
    // Update profile if needed
    val newProfile = message.profile
    if (newProfile != null) {

    }
    // Get or create thread
    val threadID = storage.getOrCreateThreadIdFor(message.sender!!, message.groupPublicKey, openGroupID) ?: throw MessageSender.Error.NoThread
    // Parse quote if needed
    if (message.quote != null && proto.dataMessage.hasQuote()) {
        // TODO
    }
    // Parse link preview if needed
    if (message.linkPreview != null && proto.dataMessage.previewCount > 0) {
        // TODO
    }
    // Persist the message
    message.threadID = threadID
    // Start attachment downloads if needed
    attachmentsToDownload.forEach { attachmentID ->
        val downloadJob = AttachmentDownloadJob()
    }
    // TODO finish this process
}

private fun MessageReceiver.handleClosedGroupUpdate(message: ClosedGroupUpdate) {
    when (message.kind!!) {
        is ClosedGroupUpdate.Kind.New -> handleNewGroup(message)
        is ClosedGroupUpdate.Kind.Info -> handleGroupUpdate(message)
        is ClosedGroupUpdate.Kind.SenderKeyRequest -> handleSenderKeyRequest(message)
        is ClosedGroupUpdate.Kind.SenderKey -> handleSenderKey(message)
    }
}

private fun MessageReceiver.handleNewGroup(message: ClosedGroupUpdate) {
    val storage = MessagingConfiguration.shared.storage
    val sskDatabase = MessagingConfiguration.shared.sskDatabase
    if (message.kind !is ClosedGroupUpdate.Kind.New) { return }
    val kind = message.kind!! as ClosedGroupUpdate.Kind.New
    val groupPublicKey = kind.groupPublicKey.toHexString()
    val name = kind.name
    val groupPrivateKey = kind.groupPrivateKey
    val senderKeys = kind.senderKeys
    val members = kind.members.map { it.toHexString() }
    val admins = kind.admins.map { it.toHexString() }
    // Persist the ratchets
    senderKeys.forEach { senderKey ->
        if (!members.contains(senderKey.publicKey.toHexString())) { return@forEach }
        val ratchet = ClosedGroupRatchet(senderKey.chainKey.toHexString(), senderKey.keyIndex, listOf())
        sskDatabase.setClosedGroupRatchet(groupPublicKey, senderKey.publicKey.toHexString(), ratchet, ClosedGroupRatchetCollectionType.Current)
    }
    // Sort out any discrepancies between the provided sender keys and what's required
    val missingSenderKeys = members.toSet().subtract(senderKeys.map { Hex.toStringCondensed(it.publicKey) })
    val userPublicKey = storage.getUserPublicKey()!!
    if (missingSenderKeys.contains(userPublicKey)) {
        val userRatchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, userPublicKey)
        val userSenderKey = ClosedGroupSenderKey(Hex.fromStringCondensed(userRatchet.chainKey), userRatchet.keyIndex, Hex.fromStringCondensed(userPublicKey))
        members.forEach { member ->
            if (member == userPublicKey) return@forEach
            val closedGroupUpdateKind = ClosedGroupUpdate.Kind.SenderKey(groupPublicKey.toByteArray(), userSenderKey)
            val closedGroupUpdate = ClosedGroupUpdate()
            closedGroupUpdate.kind = closedGroupUpdateKind
            MessageSender.send(closedGroupUpdate, Destination.ClosedGroup(groupPublicKey))
        }
    }
    missingSenderKeys.minus(userPublicKey).forEach { publicKey ->
        MessageSender.requestSenderKey(groupPublicKey, publicKey)
    }
    // Create the group
    val groupID = GroupUtil.getEncodedClosedGroupID(groupPublicKey)
    if (storage.getGroup(groupID) != null) {
        // Update the group
        storage.updateTitle(groupID, name)
        storage.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    } else {
        storage.createGroup(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }),
                null, null, LinkedList(admins.map { Address.fromSerialized(it) }))
    }
    storage.setProfileSharing(Address.fromSerialized(groupID), true)
    // Add the group to the user's set of public keys to poll for
    sskDatabase.setClosedGroupPrivateKey(groupPublicKey, groupPrivateKey.toHexString())
    // Notify the PN server
    PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Subscribe, groupPublicKey, userPublicKey)
    // Notify the user
    /* TODO
    insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, SignalServiceGroup.Type.UPDATE, name, members, admins)
    */
}

private fun MessageReceiver.handleGroupUpdate(message: ClosedGroupUpdate) {
    val storage = MessagingConfiguration.shared.storage
    val sskDatabase = MessagingConfiguration.shared.sskDatabase
    if (message.kind !is ClosedGroupUpdate.Kind.Info) { return }
    val kind = message.kind!! as ClosedGroupUpdate.Kind.Info
    val groupPublicKey = kind.groupPublicKey.toHexString()
    val name = kind.name
    val senderKeys = kind.senderKeys
    val members = kind.members.map { it.toHexString() }
    val admins = kind.admins.map { it.toHexString() }
    // Get the group
    val groupID = GroupUtil.getEncodedClosedGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: return Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
    // Check that the sender is a member of the group (before the update)
    if (!group.members.contains(Address.fromSerialized(message.sender!!))) { return Log.d("Loki", "Ignoring closed group info message from non-member.") }
    // Store the ratchets for any new members (it's important that this happens before the code below)
    senderKeys.forEach { senderKey ->
        val ratchet = ClosedGroupRatchet(senderKey.chainKey.toHexString(), senderKey.keyIndex, listOf())
        sskDatabase.setClosedGroupRatchet(groupPublicKey, senderKey.publicKey.toHexString(), ratchet, ClosedGroupRatchetCollectionType.Current)
    }
    // Delete all ratchets and either:
    // • Send out the user's new ratchet using established channels if other members of the group left or were removed
    // • Remove the group from the user's set of public keys to poll for if the current user was among the members that were removed
    val oldMembers = group.members.map { it.serialize() }.toSet()
    val userPublicKey = storage.getUserPublicKey()!!
    val wasUserRemoved = !members.contains(userPublicKey)
    if (members.toSet().intersect(oldMembers) != oldMembers.toSet()) {
        val allOldRatchets = sskDatabase.getAllClosedGroupRatchets(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
        for (pair in allOldRatchets) {
            val senderPublicKey = pair.first
            val ratchet = pair.second
            val collection = ClosedGroupRatchetCollectionType.Old
            sskDatabase.setClosedGroupRatchet(groupPublicKey, senderPublicKey, ratchet, collection)
        }
        sskDatabase.removeAllClosedGroupRatchets(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
        if (wasUserRemoved) {
            sskDatabase.removeClosedGroupPrivateKey(groupPublicKey)
            storage.setActive(groupID, false)
            storage.removeMember(groupID, Address.fromSerialized(userPublicKey))
            // Notify the PN server
            PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
        } else {
            val userRatchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, userPublicKey)
            val userSenderKey = ClosedGroupSenderKey(Hex.fromStringCondensed(userRatchet.chainKey), userRatchet.keyIndex, Hex.fromStringCondensed(userPublicKey))
            members.forEach { member ->
                if (member == userPublicKey) return@forEach
                val address = Address.fromSerialized(member)
                val closedGroupUpdateKind = ClosedGroupUpdate.Kind.SenderKey(Hex.fromStringCondensed(groupPublicKey), userSenderKey)
                val closedGroupUpdate = ClosedGroupUpdate()
                closedGroupUpdate.kind = closedGroupUpdateKind
                MessageSender.send(closedGroupUpdate, address)
            }
        }
    }
    // Update the group
    storage.updateTitle(groupID, name)
    storage.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    // Notify the user if needed
    val infoType = if (wasUserRemoved) SignalServiceProtos.GroupContext.Type.QUIT else SignalServiceProtos.GroupContext.Type.UPDATE
    val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
    // TODO insertOutgoingInfoMessage(context, groupID, infoType, name, members, admins, threadID)
}

private fun MessageReceiver.handleSenderKeyRequest(message: ClosedGroupUpdate) {

}

private fun MessageReceiver.handleSenderKey(message: ClosedGroupUpdate) {

}