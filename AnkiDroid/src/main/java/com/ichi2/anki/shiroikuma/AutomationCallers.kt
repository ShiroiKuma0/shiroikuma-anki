// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shiroikuma

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Binder
import android.os.Build
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.GET_SIGNING_CERTIFICATES_L
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import java.security.MessageDigest

/**
 * Fork: who is allowed through the automation data door ([AutomationProvider]),
 * and how that is decided. Copied verbatim in substance from 自由作業盤's
 * `core/automation/AutomationCallers.kt` — it is deliberately app-independent,
 * and the pins are the family's.
 *
 * ## Why not a token
 *
 * The token this replaces was a 48-character secret 白い熊 pasted from one
 * app's settings into another's. It cannot survive a wipe, which is fatal for
 * the case the family now exists to serve: 応用管理 restoring apps and their
 * data onto a clean phone, where nothing is configured yet.
 *
 * ## Why not a `shiroikuma.*` prefix
 *
 * Because that is not an identity. What makes
 * [android.content.ContentProvider.getCallingPackage] worth anything is that a
 * package name **cannot be taken while the real package is installed** —
 * package names are not a namespace anyone owns, so any sideloaded app may
 * call itself `shiroikuma.evil` and pass a prefix test. Since the caller
 * supplies the file descriptor an export is written into, a prefix check would
 * hand such an app the complete data of every sister app in turn: strictly
 * weaker than the token it replaces.
 *
 * ## What is actually checked, in order
 *
 * 1. **An exact name** from [CALLERS].
 * 2. **The uid agrees.** `getCallingPackage()` reflects the caller's *declared*
 *    attribution, and packages sharing a uid are not distinguished by it, so it
 *    is confirmed against the uid the kernel reports.
 * 3. **The signing certificate matches a pinned hash.** This closes the real
 *    gap: *whichever caller package is absent from the device is a name anyone
 *    can take*, and a clean phone is precisely a device where not everything is
 *    installed yet — the moment the assumption is weakest is the moment it is
 *    most needed.
 */
object AutomationCallers {
    /**
     * The apps allowed to drive this one's data door.
     *
     * 応用管理 backs up and restores; 自由作業盤 runs the 保存復元 batch.
     * Nothing else has any business exporting this app's data, and an entry
     * added here is a deliberate act.
     *
     * Where the hashes come from, so the next person can re-derive them rather
     * than trust them:
     * ```
     * apksigner verify --print-certs <the app's signed release APK> | grep 'SHA-256 digest'
     * ```
     * Every app in the family has **its own keystore**, so there is no shared
     * signing key to compare against and each caller is pinned by name. That is
     * also why a `protectionLevel="signature"` permission was never an option.
     * If a caller's key is ever rotated its calls stop working and the fix is
     * these constants — the intended failure, because a signing key changing
     * unnoticed is exactly what a pin is for.
     */
    private val CALLERS =
        mapOf(
            "shiroikuma.oyokanri" to "9c585f4d118cb97ff653f949a8872875548403b9083ce6b9baa2e8f0c55ac6cc",
            "shiroikuma.jiyusagyoban" to "efd0d352192651593a92288ecdc64fc87262ec8648c24ed8f51a5587d46ac602",
        )

    /**
     * Why the check answers a string and not a boolean: a refusal that says
     * only "no" is a refusal nobody can debug from the other side of an IPC
     * boundary. Each of these is a different mistake with a different fix, and
     * the caller shows them to 白い熊 verbatim.
     */
    sealed interface Verdict {
        object Allowed : Verdict

        data class Refused(
            val why: String,
        ) : Verdict
    }

    fun verify(
        context: Context,
        declared: String?,
    ): Verdict {
        val name =
            declared?.takeIf { it.isNotEmpty() }
                ?: return Verdict.Refused("ERROR:caller unknown")
        val pin = CALLERS[name] ?: return Verdict.Refused("ERROR:caller not permitted: $name")

        // The kernel's answer, not the caller's. A package may declare an
        // attribution it does not own; the uid cannot be borrowed.
        val real =
            runCatching {
                context.packageManager.getPackagesForUid(Binder.getCallingUid())
            }.getOrNull().orEmpty()
        if (name !in real) return Verdict.Refused("ERROR:caller uid mismatch: $name")

        val signature =
            signingSha256(context, name)
                ?: return Verdict.Refused("ERROR:caller signature unreadable: $name")
        // Constant-time, like the token compare it replaces — the value is a
        // public hash, but the habit is worth keeping and costs nothing.
        if (!MessageDigest.isEqual(signature.toByteArray(), pin.toByteArray())) {
            return Verdict.Refused("ERROR:caller signature mismatch: $name")
        }
        return Verdict.Allowed
    }

    /**
     * The SHA-256 of [pkg]'s current signing certificate, lower-case hex.
     *
     * `signingInfo` rather than the deprecated `signatures`: a rotated key
     * reports its whole history and we want the certificate actually in force.
     * `GET_SIGNING_CERTIFICATES` is API 28 and this app's minSdk is 24 — on a
     * 24–27 device the flag is accepted and `signingInfo` comes back null, so
     * *without* the branch the door would refuse every caller, a total failure
     * that never appears on 白い熊's phone (API 31) and would only surface on an
     * older one. The deprecated array is the correct answer there, not a
     * compromise: before key rotation existed, `signatures` **was** the signing
     * certificate.
     *
     * An app with more than one current signer is refused by returning null —
     * ours have exactly one, and "several signers, one of which matches" is not
     * a question this needs to answer.
     */
    private fun signingSha256(
        context: Context,
        pkg: String,
    ): String? =
        runCatching {
            val pm = context.packageManager
            val certs: Array<out Signature>? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pm
                        .getPackageInfoCompat(pkg, PackageInfoFlagsCompat.of(GET_SIGNING_CERTIFICATES_L))
                        ?.signingInfo
                        ?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
                }
            val only = certs?.singleOrNull() ?: return null
            MessageDigest
                .getInstance("SHA-256")
                .digest(only.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.getOrNull()
}
