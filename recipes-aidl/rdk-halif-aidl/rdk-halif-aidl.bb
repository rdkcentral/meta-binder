SUMMARY = "HAL interface definitions for RDK-E"
SECTION = "multimedia/hal"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# tag 0.22.0 with broadcast and ringbuffer from tag 0.23.0
BASELINE_RELEASE_DATE = "202606" 

SRC_URI = "${CMF_GITHUB_ROOT}/rdk-halif-aidl.git;${CMF_GITHUB_SRC_URI_SUFFIX}"
SRC_URI:append = " file://versions_baseline_${BASELINE_RELEASE_DATE}.yaml"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

S = "${WORKDIR}/git"


DEPENDS:append = " aidl-generator-native libbinder"

inherit cmake

EXTRA_OECMAKE += "\
    -DINTERFACE_VERSION=release \
    -DINTERFACE_RELEASE_FILE=${WORKDIR}/versions_baseline_${BASELINE_RELEASE_DATE}.yaml \
"
# This is only to support legacy build from source. Should be remove when all
# recipes has transistioned use new rdk-halif-aidl.
HALIF_SRC_DIR_NAME := "${PN}"
do_install:append() {
    local version=""
    local hal_modules=" \
        audiodecoder \
        audiomixer \
        audiosink \
        avbuffer \
        avclock \
        common \
        planecontrol \
        videodecoder \
        videosink \
"

    mkdir -p ${D}/${HALIF_SRC_DIR_NAME}/cpp ${D}/${HALIF_SRC_DIR_NAME}/h
    for m in $hal_modules; do
        version=`grep -E "${m}:" ${WORKDIR}/versions_baseline_${BASELINE_RELEASE_DATE}.yaml | cut -d':' -f2 | tr -d ' '`
        echo "$m : $version"
        cp --no-preserve=ownership -r ${S}/${m}/${version}/src/* ${D}/${HALIF_SRC_DIR_NAME}/cpp/
    done
}

# FILES:${PN}-dev only affects packaging (`do_package`).
# It does **not** affect sysroot population.
# Sysroot population comes from  do_install and SYSROOT_DIRS
# SYSROOT_DIRS is a predefined list of directories
# Hence we need to append to it so when other recipes depend on
# rdk-halif-aidl there recipe-sysroot gets /usr/src included
SYSROOT_DIRS += "${prefix}/src"
SYSROOT_DIRS += " /${HALIF_SRC_DIR_NAME}"
FILES:${PN}-dev += "${prefix}/src"
FILES:${PN}-dev += "rdk-halif-aidl"
