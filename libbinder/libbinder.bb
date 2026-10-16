#/**
# * Copyright 2026 RDK Management
# *
# * Licensed under the Apache License, Version 2.0 (the "License");
# * you may not use this file except in compliance with the License.
# * You may obtain a copy of the License at
# *
# * http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# *
# * SPDX-License-Identifier: Apache-2.0
# */

SUMMARY = "Linux Binder IPC runtime (libbinder, libutils, servicemanager)"
LICENSE = "Apache-2.0"
# A non-CLOSED licence needs a checksum, or do_populate_lic fails before the
# build starts. Points at the LICENSE file in the fetched source.
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

# files/ sits beside this recipe and holds the systemd unit. Without this a
# layer that copies only the .bb fails during fetch, before anything builds.
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "${RDKCENTRAL_GITHUB_ROOT}/linux_binder_idl;${RDKCENTRAL_GITHUB_SRC_URI_SUFFIX}"
SRC_URI += "file://servicemanager.service"


# Pin to a released tag. A branch name or a feature-branch SHA makes the build
# unreproducible and is not a supported configuration.
PV ?= "2.7.0"
SRCREV ?= "2.7.0"
S = "${WORKDIR}/git"

# Do not use libblog.bb if using libbinder.bb because libbinder provides support for liblog.bb
RPROVIDES:${PN}:append = " liblog"
PROVIDES:append = " liblog"

# Network access from tasks is disabled by default on kernels which support this feature
do_configure[network] = "1"

inherit cmake systemd 

do_install:append() {
    install -d ${D}${systemd_unitdir}/system
    install -m 0644 ${WORKDIR}/servicemanager.service ${D}${systemd_unitdir}/system
}

SYSTEMD_SERVICE:${PN} += "servicemanager.service"
FILES:${PN} += "${systemd_unitdir}/system/servicemanager.service \
                ${libdir}/* \
                ${bindir}/* \
               "
INSANE_SKIP:${PN}-dev += "dev-elf"
INSANE_SKIP:${PN} += "dev-deps"
FILES_SOLIBSDEV = ""
