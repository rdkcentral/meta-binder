DESCRIPTION = "RDK Binder module"
SECTION = "BinderModule"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "${RDKCENTRAL_GITHUB_ROOT}/linux_binder_idl;${RDKCENTRAL_GITHUB_SRC_URI_SUFFIX}"
SRC_URI += "file://servicemanager.service"

PV ?= "2.6.0"
PR ?= "r1"
SRCREV ?= "2.6.0"

S = "${WORKDIR}/git"

inherit cmake systemd siteinfo

RPROVIDES:${PN}:append = " liblog"
PROVIDES:append = " liblog"

DEPENDS += "bison-native"

# Need kernel configuration to derive the binder protocol.
do_configure[depends] += "virtual/kernel:do_shared_workdir"

# Override if building outside a device image build.
BINDER_PROTOCOL ?= ""


def binder_protocol(d):
    import os

    want = d.getVar('BINDER_PROTOCOL') or ''

    cfg = os.path.join(
        d.getVar('STAGING_KERNEL_BUILDDIR') or '',
        '.config'
    )

    derived = ''

    if os.path.exists(cfg):
        derived = '8'

        with open(cfg) as f:
            for line in f:
                if line.strip() == 'CONFIG_ANDROID_BINDER_IPC_32BIT=y':
                    derived = '7'
                    break

    if derived and want and derived != want:
        bb.fatal(
            "binder: kernel serves protocol %s but "
            "BINDER_PROTOCOL=%s"
            % (derived, want)
        )

    proto = derived or want

    if not proto:
        bb.fatal(
            "binder: unable to determine protocol. "
            "Set BINDER_PROTOCOL or provide kernel .config"
        )

    if proto not in ('7', '8'):
        bb.fatal(
            "binder: invalid protocol '%s'"
            % proto
        )

    return proto


def binder_protocol_source(d):
    import os

    cfg = os.path.join(
        d.getVar('STAGING_KERNEL_BUILDDIR') or '',
        '.config'
    )

    if os.path.exists(cfg):
        return "kernel:%s" % cfg

    return "declared:BINDER_PROTOCOL"


BINDER_PROTOCOL_RESOLVED ?= "${@binder_protocol(d)}"
BINDER_PROTOCOL_SOURCE ?= "${@binder_protocol_source(d)}"

EXTRA_OECMAKE += " \
    -DBUILD_HOST_AIDL=OFF \
    -DBINDER_IPC_32BIT=${@'ON' if d.getVar('BINDER_PROTOCOL_RESOLVED') == '7' else 'OFF'} \
    ${@bb.utils.contains('SITEINFO_BITS', '32', '-DTARGET_LIB32_VERSION=ON', '-DTARGET_LIB64_VERSION=ON', d)} \
"

do_configure:prepend() {
    bbplain "binder: protocol=${BINDER_PROTOCOL_RESOLVED} bits=${SITEINFO_BITS} source=${BINDER_PROTOCOL_SOURCE}"
}

do_install:append() {
    install -d ${D}${systemd_unitdir}/system
    install -m 0644 \
        ${WORKDIR}/servicemanager.service \
        ${D}${systemd_unitdir}/system/
}

SYSTEMD_SERVICE:${PN} = "servicemanager.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

#
# Binder currently installs real, unversioned .so files.
# Package them in runtime package rather than -dev.
#
FILES_SOLIBSDEV = ""

FILES:${PN} += " \
    ${systemd_unitdir}/system/servicemanager.service \
    ${libdir}/lib*.so \
    ${libdir}/lib*.so.* \
    ${bindir}/* \
"

FILES:${PN}-dev = " \
    ${includedir} \
    ${includedir}/* \
    ${libdir}/pkgconfig \
    ${libdir}/cmake \
    ${datadir}/pkgconfig \
"

INSANE_SKIP:${PN} += "dev-deps"
