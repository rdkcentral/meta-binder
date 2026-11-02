DESCRIPTION = "RDK AIDL Compiler/Generator Host Tool"
SECTION = "BinderModule"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

PR = "r1"
# Minimum version of linux_binder_idl required/supported by this recipe 
PV = "2.7.0"

SRC_URI = "${RDKCENTRAL_GITHUB_ROOT}/linux_binder_idl;${RDKCENTRAL_GITHUB_SRC_URI_SUFFIX}"

SRCREV = "${PV}"
S = "${WORKDIR}/git"

inherit cmake native

DEPENDS += "m4-native"
export M4 = "${STAGING_BINDIR_NATIVE}/m4"

OECMAKE_SOURCEPATH = "${S}"
OECMAKE_TARGET_COMPILE = "aidl aidl-cpp"
EXTRA_OECMAKE += "-DBUILD_HOST_AIDL=ON -DBUILD_STATIC_LIB=ON"

# Enable network access during configure step
# To download binder and aidl sources
do_configure[network] = "1"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/aidl ${D}${bindir}/aidl
    install -m 0755 ${B}/aidl-cpp ${D}${bindir}/aidl-cpp
}

