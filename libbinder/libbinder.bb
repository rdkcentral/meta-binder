DESCRIPTION = "RDK Binder module"
SECTION = "BinderModule"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "${RDKCENTRAL_GITHUB_ROOT}/linux_binder_idl;${RDKCENTRAL_GITHUB_SRC_URI_SUFFIX}"
SRC_URI += "file://servicemanager.service"

PV ?= "1.0.0"
PR ?= "r0"
SRCREV ?= "1.0.0"

S = "${WORKDIR}/git"

inherit cmake systemd

do_configure:prepend() {
    cd ${S}
    # Source setup-env.sh to initialise the environment variables.
    . ./setup-env.sh
    # Clone the AOSP code and apply the patches.
    clone_android_binder_repo
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
