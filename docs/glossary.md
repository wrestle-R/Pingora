# Pingora glossary

- **Source notification**: notification emitted by the original app on its own device.
- **Mirror**: Pingora presentation of a source notification on the paired device. Never a new source to forward back.
- **Conversation**: stable source-provided thread/group identity where available; not merely the sender display name.
- **Version**: identity of the current source update used to reject stale actions.
- **Dismiss**: explicitly clear an active notification, separate from marking a conversation read.
- **Expire**: native popup timeout; does not authorize dismissal of the source.
- **History**: bounded saved local notification text, including OTP-bearing messages.
- **Clear history**: delete retained local history without issuing source dismissals.
- **Feature toggle**: endpoint-enforced permission to use one capability with the paired device.
- **Pause sharing**: temporary sharing suspension that preserves pairing and local history.
- **Health**: observed connection/permission/capability status, not a promise of delivery.
- **Verified calling setup**: phone/Linux combination with tested Bluetooth answering and bidirectional audio.
