DESCRIPTION = "RDK Binder example module"
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

DEPENDS = "libbinder aidl-generator-native"
RDEPENDS:${PN} = "libbinder"

OECMAKE_SOURCEPATH = "${WORKDIR}/git/example"
EXTRA_OECMAKE += "-DCMAKE_INSTALL_INCDIR:PATH=${STAGING_INCDIR}"

do_configure:prepend() {
    aidl_dir="${S}/example/FWManager/aidl"
    generated_dir="${S}/example/stable/generated/FWManager"
    install -d "${generated_dir}/include"
    for interface in IFWManager IFirmwareUpdateStateListener FirmwareStatus; do
        ${STAGING_BINDIR_NATIVE}/aidl --lang=cpp -I"${aidl_dir}" \
            "${aidl_dir}/com/test/${interface}.aidl" \
            --header_out "${generated_dir}/include" -o "${generated_dir}"
    done
}

FILES:${PN} += "${libdir}/* \
                ${bindir}/* \
               "
INSANE_SKIP:${PN}-dev += "dev-elf"
INSANE_SKIP:${PN} += "dev-deps"
FILES_SOLIBSDEV = ""
