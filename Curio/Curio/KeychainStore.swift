import Foundation
import Security
import ComposeApp

/// Keychain-backed implementation of the Kotlin `SecureStore` protocol.
///
/// A session token is a credential. `UserDefaults` would work and is three
/// lines, but its contents appear in unencrypted device backups and are readable
/// by anything with access to the app container. The Keychain is encrypted at
/// rest and gated by the device passcode.
///
/// `kSecAttrAccessibleAfterFirstUnlock` rather than `WhenUnlocked`: Curio reads
/// the token at launch, and a stricter class would make someone opening the app
/// on a not-yet-unlocked device appear signed out. AfterFirstUnlock still keeps
/// everything unreadable until the device has been unlocked once since boot.
///
/// Deliberately not `kSecAttrSynchronizable` — a session token is device-scoped,
/// and syncing it through iCloud would put one credential on every device the
/// user owns.
final class KeychainStore: SecureStore {

    private let service = "app.curio.auth"

    func get(key: String) -> String? {
        var query = baseQuery(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        guard status == errSecSuccess,
              let data = item as? Data,
              let value = String(data: data, encoding: .utf8)
        else { return nil }

        return value
    }

    func set(key: String, value: String) {
        // SecItemAdd returns errSecDuplicateItem rather than overwriting, so
        // delete-then-add is the simplest correct upsert. SecItemUpdate would
        // avoid the brief empty window, but that only matters if the process
        // dies mid-write — in which case the user logs in again.
        remove(key: key)

        guard let data = value.data(using: .utf8) else { return }

        var attributes = baseQuery(key)
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock

        SecItemAdd(attributes as CFDictionary, nil)
    }

    func remove(key: String) {
        SecItemDelete(baseQuery(key) as CFDictionary)
    }

    private func baseQuery(_ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
