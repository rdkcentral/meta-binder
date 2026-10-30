DESCRIPTION = "RDK Android liblog module"
SECTION = "BinderModule"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

PR = "r1"
# Minimum version of linux_binder_idl required/supported by this recipe 
PV = "2.7.0"

SRC_URI = "${RDKCENTRAL_GITHUB_ROOT}/linux_binder_idl;${RDKCENTRAL_GITHUB_SRC_URI_SUFFIX}"

SRCREV = "${PV}"
S = "${WORKDIR}/git"

inherit cmake

OECMAKE_SOURCEPATH = "${S}"
OECMAKE_TARGET_COMPILE = "log"

# Enable network access during configure step
# To download binder sources
do_configure[network] = "1"

do_install() {
    install -d ${D}${libdir} ${D}${includedir}
    install -m 0755 ${B}/liblog.so ${D}${libdir}/liblog.so
    cp -R ${S}/android/logging/liblog/include/android ${D}${includedir}/
    cp -R ${S}/android/logging/liblog/include/log ${D}${includedir}/
}

FILES:${PN} += "${libdir}/*"

INSANE_SKIP:${PN}-dev += "dev-elf"
INSANE_SKIP:${PN} += "dev-deps"
FILES_SOLIBSDEV = ""
