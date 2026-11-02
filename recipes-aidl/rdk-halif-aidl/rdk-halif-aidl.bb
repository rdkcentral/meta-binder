SUMMARY = "Versioned RDK HALIF AIDL interfaces"
DESCRIPTION = "Builds selected RDK HALIF AIDL interface versions in isolated CMake trees and packages each exact version independently."
HOMEPAGE = "https://github.com/rdkcentral/rdk-halif-aidl"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# tag 0.22.0 with broadcast and ringbuffer from tag 0.23.0
BASELINE_RELEASE_DATE = "202606" 

SRC_URI = "git://github.com/rdkcentral/rdk-halif-aidl.git;protocol=https;nobranch=1"
SRC_URI:append = " file://versions_baseline_${BASELINE_RELEASE_DATE}.yaml"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

inherit cmake pkgconfig

DEPENDS += "libbinder"

HALIF_AIDL_INTERFACE_VERSION_APPENDS ??= ""
# Keep the original baseline intact and share the selected manifest with all consumers.
HALIF_AIDL_INTERFACE_VERSIONS_FILE = "${WORKDIR}/versions_baseline_${BASELINE_RELEASE_DATE}${@'_append' if (d.getVar('HALIF_AIDL_INTERFACE_VERSION_APPENDS') or '').strip() else ''}.yaml"


do_unpack[postfuncs] += "apply_halif_aidl_interface_versions"
do_unpack[vardeps] += "HALIF_AIDL_INTERFACE_VERSION_APPENDS"

python apply_halif_aidl_interface_versions() {
    import re
    from pathlib import Path

    appends = (d.getVar("HALIF_AIDL_INTERFACE_VERSION_APPENDS") or "").strip()
    if not appends:
        return

    versions = []
    for entry in appends.split(";"):
        component, separator, version = entry.strip().partition("=")
        component = component.strip()
        version = version.strip()
        if not separator or not re.fullmatch(r"[a-z][a-z0-9_]*", component) or not re.fullmatch(r"(?:[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+|current)", version):
            bb.fatal("Invalid HALIF_AIDL_INTERFACE_VERSION_APPENDS entry: %r; expected component=x.y.z.w or component=current" % entry)
        versions.append((component, version))

    baseline = Path(d.getVar("WORKDIR")) / ("versions_baseline_%s.yaml" % d.getVar("BASELINE_RELEASE_DATE"))
    content = baseline.read_text()
    pattern = re.compile(r"^(  ([a-z][a-z0-9_]*):[ \t]*)([^ \t\r\n#]+)([ \t]*(?:#.*)?)$", re.MULTILINE)
    existing = {(match.group(2), match.group(3)) for match in pattern.finditer(content)}
    components = {component for component, version in existing}
    unknown = {component for component, version in versions} - components
    if unknown:
        bb.fatal("Unknown HALIF_AIDL_INTERFACE_VERSION_APPENDS components: %s" % ", ".join(sorted(unknown)))

    for component, version in versions:
        if (component, version) not in existing:
            if not content.endswith("\n"):
                content += "\n"
            content += "  %s: %s\n" % (component, version)
            existing.add((component, version))

    Path(d.getVar("HALIF_AIDL_INTERFACE_VERSIONS_FILE")).write_text(content)
}

EXTRA_OECMAKE += "-DBUILD_SHARED_LIBS:BOOL=${@bb.utils.contains('DISTRO_FEATURES', 'AIDL_HAL_LIBS_STATIC', 'OFF', 'ON', d)}"
EXTRA_OECMAKE += "-DINTERFACE_RELEASE_FILE=${HALIF_AIDL_INTERFACE_VERSIONS_FILE}"

PACKAGES_DYNAMIC = "^${PN}-.*"

def find_components(d):
    import os
    import pathlib
    import re
    libdir = os.path.join(d.getVar('D'), d.getVar('libdir').lstrip('/'))
    components = []
    for d in pathlib.Path(libdir).iterdir():
        regex = r'lib([A-Za-z]+)-v(\d+\.\d+\.\d+\.\d+)-cpp\.so'
        match = re.search(regex, d.name)
        if not match:
            continue
        components.append((match.group(1), match.group(2), match.group(0)))
    print(f"components={components}")
    return components

python populate_packages:prepend() {
    import os
    pkgs = []
    for component, version, library in find_components(d):
        name = d.getVar('PN') + '-' + component + '-' + version.replace('.', '-')
        pkgs.append(name)
        d.setVar('SUMMARY:' + name, f'RDK HAL AIDL interface library: {component}')
        d.setVar('FILES:' + name, os.path.join(d.getVar('libdir'), library))
    # Exact runtime libraries must be claimed before the default -dev .so glob.
    d.setVar('PACKAGES', ' '.join(pkgs + d.getVar('PACKAGES').split()))
}

SYSROOT_DIRS += " ${prefix}/src"
FILES:${PN}-dev += "${prefix}/src"
FILES:${PN}-staticdev += "${libdir}/lib*.a"
