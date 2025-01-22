DESCRIPTION = "RDK Binder example module"
SECTION = "BinderModule"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

SRC_URI = "${RDKE_GITHUB_ROOT}/linux_binder_idl;${RDKE_GITHUB_SRC_URI_SUFFIX}"

DEPENDS = "libbinder"
RDEPENDS:${PN} = "libbinder"

S = "${WORKDIR}/git"
OECMAKE_SOURCEPATH = "${WORKDIR}/git/example"

inherit cmake

FILES:${PN} += "${libdir}/* \
                ${bindir}/* \
               "
INSANE_SKIP:${PN}-dev += "dev-elf"
INSANE_SKIP:${PN} += "dev-deps"
FILES_SOLIBSDEV = ""
