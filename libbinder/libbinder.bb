DESCRIPTION = "RDK Binder module"
SECTION = "BinderModule"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "${RDKCENTRAL_GITHUB_ROOT}/linux_binder_idl;${RDKCENTRAL_GITHUB_SRC_URI_SUFFIX}"
SRC_URI += "file://servicemanager.service"

PV ?= "2.3.0"
PR ?= "r0"
SRCREV ?= "2.3.0"

# Do not use libblog.bb if using libbinder.bb because libbinder provides support for liblog.bb
RPROVIDES:${PN}:append = " liblog"
PROVIDES:append = " liblog"

S = "${WORKDIR}/git"

# Network access from tasks is disabled by default on kernels which support this feature
do_configure[network] = "1"

inherit cmake systemd

DEPENDS += "bison-native pkgconfig-native"

# Disable building the host AIDL compiler tool in Yocto cross-compilation.
# The aidl binary would be cross-compiled to the target arch (e.g. aarch64)
# and cannot execute on the host to generate stubs, causing "Exec format error".
# With BUILD_HOST_AIDL=OFF the build uses the prebuilt stubs in binder_aidl_gen/.
EXTRA_OECMAKE += "-DBUILD_HOST_AIDL=OFF"

do_configure:prepend() {
    cd ${S}
    # Source setup-env.sh to initialise the environment variables.
    # Clone the AOSP code and apply the patches.
    bash ./clone-android-binder-repo.sh
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
