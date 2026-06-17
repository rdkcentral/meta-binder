DESCRIPTION = "RDK Binder module"
SECTION = "BinderModule"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "${RDKCENTRAL_GITHUB_ROOT}/linux_binder_idl;${RDKCENTRAL_GITHUB_SRC_URI_SUFFIX}"
SRC_URI += "file://servicemanager.service"

# Do not use libblog.bb if using libbinder.bb because libbinder provides support for liblog.bb
RPROVIDES:${PN}:append = " liblog"
PROVIDES:append = " liblog"

S = "${WORKDIR}/git"

inherit cmake systemd

# ── Polaris 64-bit binder wire-format fix (compiler level) ───────────────────
CFLAGS:append   = " -UBINDER_IPC_32BIT"
CXXFLAGS:append = " -UBINDER_IPC_32BIT"

do_configure:prepend() {
    cd ${S}
    # Source setup-env.sh to initialise the environment variables.
    . ./setup-env.sh
    # Clone the AOSP code and apply the patches.
    clone_android_binder_repo

    # ── binder.h header patch ────────────────────────────────────────────────
        bbnote "Patching binder.h: replacing #ifdef BINDER_IPC_32BIT with #if 0"
        for _bh in \
                "${S}/android/build-tools/sysroots/x86_64-linux-musl/include/linux/android/binder.h" \
                "${S}/android/build-tools/sysroots/i686-linux-musl/include/linux/android/binder.h" \
                "${RECIPE_SYSROOT}/usr/include/linux/android/binder.h"; do
        if [ -f "${_bh}" ]; then
                sed -i 's/#ifdef BINDER_IPC_32BIT/#if 0 \/\* BINDER_IPC_32BIT disabled: Polaris needs 64-bit wire *\//' "${_bh}" \
                && bbnote "  Patched ${_bh}"
        else
                bbnote "  (not found, skipping) ${_bh}"
        fi
        done

        # ── CMakeLists.txt patch ─────────────────────────────────────────────────
        bbnote "Patching CMakeLists.txt: deleting BINDER_IPC_32BIT definition lines"
        find "${S}" -name "CMakeLists.txt" | while read _cm; do
                if grep -q "BINDER_IPC_32BIT" "${_cm}"; then
                        sed -i '/BINDER_IPC_32BIT/d' "${_cm}" \
                        && bbnote "  Deleted BINDER_IPC_32BIT lines from ${_cm}"
                fi
        done
        # ProcessState.cpp: remove FLAT_BINDER_FLAG_TXN_SECURITY_CTX ──────────────
        bbnote "Patching ProcessState.cpp: removing FLAT_BINDER_FLAG_TXN_SECURITY_CTX"
        find "${S}" -name "ProcessState.cpp" | while read _ps; do
                 if grep -q "FLAT_BINDER_FLAG_TXN_SECURITY_CTX" "${_ps}"; then
                        sed -i 's/FLAT_BINDER_FLAG_TXN_SECURITY_CTX/0/g' "${_ps}" \
                        && bbnote "  Patched ${_ps}"
                fi
        done
        cd ${B}
}

do_install:append() {
    # Install the systemd servicemanager service.
    install -d ${D}/${systemd_unitdir}/system
    install -m 0644 ${WORKDIR}/servicemanager.service ${D}/${systemd_unitdir}/system
}

SYSTEMD_SERVICE:${PN} += "servicemanager.service"
FILES:${PN} += "${systemd_unitdir}/system/servicemanager.service \
                ${libdir}/* \
                ${bindir}/* \
               "
INSANE_SKIP:${PN}-dev += "dev-elf"
INSANE_SKIP:${PN} += "dev-deps"
FILES_SOLIBSDEV = ""
