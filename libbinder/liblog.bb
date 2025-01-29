DESCRIPTION = "RDK Android liblog module"
SECTION = "BinderModule"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

SRC_URI = "${RDKCENTRAL_GITHUB_ROOT}/linux_binder_idl;${RDKCENTRAL_GITHUB_SRC_URI_SUFFIX}"

PV ?= "1.0.0"
PR ?= "r0"
SRCREV ?= "1.0.0"

S = "${WORKDIR}/git"
OECMAKE_SOURCEPATH = "${WORKDIR}/git/liblog"

inherit cmake

do_configure:prepend() {
    cd ${S}/
    # Source setup-env.sh to initialise the environment variables.
    . ./setup-env.sh
    # Clone the AOSP code and apply the patches.
    clone_android_liblog_repo
    cd ${B}
}

FILES:${PN} += "${libdir}/*"

INSANE_SKIP:${PN}-dev += "dev-elf"
INSANE_SKIP:${PN} += "dev-deps"
FILES_SOLIBSDEV = ""
