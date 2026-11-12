SUMMARY = "Versioned RDK HALIF AIDL interfaces"
DESCRIPTION = "Builds selected RDK HALIF AIDL interface versions in isolated CMake trees and packages each exact version independently."
HOMEPAGE = "https://github.com/rdkcentral/rdk-halif-aidl"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

SRC_URI = "git://github.com/rdkcentral/rdk-halif-aidl.git;protocol=https;nobranch=1"
# Pin this to the integration commit used by the product.
#SRCREV = "e33dfee72454265ea91cd9b406a97531a6a280fc"

PV = "0.23.0+git${@d.getVar('SRCREV')[:10]}"

HALIF_UNPACKDIR = "${@d.getVar('UNPACKDIR') or d.getVar('WORKDIR')}"
S = "${HALIF_UNPACKDIR}/git"
B = "${WORKDIR}/build"

inherit cmake pkgconfig

# Override these names if the layer uses different recipe/package names for
# the Binder SDK.
HALIF_BINDER_RECIPE ??= "libbinder"
HALIF_BINDER_DEV_PACKAGE ??= "libbinder-dev"
DEPENDS += "${HALIF_BINDER_RECIPE}"

EXTRA_OECMAKE += "-DBUILD_SHARED_LIBS:BOOL=ON"

# This example intentionally names only three components. Dependencies such as
# common are discovered by CMake and packaged automatically.
HALIF_AIDL_COMPONENTS ??= "avclock bootreason videodecoder"
HALIF_AIDL_REPOSITORY_VERSION_COUNT ??= "3"

# repository: build the newest released versions found in the source tree.
# yaml:       build the versions selected by the product YAML plus overrides.
HALIF_AIDL_VERSION_MODE ??= "repository"
HALIF_AIDL_INTERFACE_VERSION_BASENAME ??= "halif-aidl-interface-versions.yaml"
HALIF_AIDL_INTERFACE_VERSION_FILE = "${HALIF_UNPACKDIR}/${HALIF_AIDL_INTERFACE_VERSION_BASENAME}"
HALIF_AIDL_INTERFACE_VERSION_OVERRIDES ??= ""

SRC_URI:append = "${@' file://' + d.getVar('HALIF_AIDL_INTERFACE_VERSION_BASENAME') if d.getVar('HALIF_AIDL_VERSION_MODE') == 'yaml' else ''}"

HALIF_UNIT_INSTALL_ROOT = "${WORKDIR}/halif-unit-install"
HALIF_PACKAGE_NAMESPACE = ".halif-packages"

PACKAGES =+ "${PN}-cmake-modules"
PACKAGES_DYNAMIC = "^${PN}-[a-z0-9][a-z0-9-]*-v[0-9]+-[0-9]+-[0-9]+-[0-9]+(-dev|-dbg)?$"

# Example consumers:
#   RDEPENDS:${PN} += "rdk-halif-aidl-avclock-v0-3-0-0"
#   RDEPENDS:${PN}-dev += "rdk-halif-aidl-avclock-v0-3-0-0-dev"
#
# The custom splitter creates exact-version package trees directly. The
# recipe-wide pkg-config provider scan cannot represent several mutually
# exclusive packages providing the same .pc module name.
PACKAGE_PREPROCESS_FUNCS += "halif_prepare_package_trees"
PACKAGESPLITFUNCS = "halif_split_package_trees"
PACKAGEFUNCS:remove = "package_do_pkgconfig"
PACKAGE_DEBUG_SPLIT_STYLE = "debug-without-src"

ALLOW_EMPTY:${PN}-src ="1"

def halif_validate_component(component, context):
    """Validate a component name.

    Args:
        component: Name to validate.
        context: Description of the configuration source for error messages.

    Returns:
        None. Calls ``bb.fatal`` if the name is not safe for paths and package
        names.
    """
    import re

    if not re.fullmatch(r"[a-z0-9][a-z0-9-]*", component):
        bb.fatal("%s contains invalid component name %r" % (context, component))


def halif_validate_version(version, context):
    """Validate a four-part numeric interface version.

    Args:
        version: Version string to validate, for example ``0.3.0.0``.
        context: Description of the configuration source for error messages.

    Returns:
        None. Calls ``bb.fatal`` if ``version`` is not four numeric fields.
    """
    import re

    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){3}", version):
        bb.fatal("%s contains invalid interface version %r" % (context, version))


def halif_version_key(version):
    """Convert a validated interface version into a numeric sort key.

    Args:
        version: Four-part, dot-separated interface version.

    Returns:
        Tuple containing the four integer version fields.
    """
    return tuple(int(part) for part in version.split("."))


def halif_package_name(d, component, version, kind="runtime"):
    """Build the package name for one exact component version.

    Args:
        d: BitBake datastore that supplies ``PN``.
        component: Validated component name.
        version: Four-part interface version.
        kind: ``runtime`` for the base package, or a suffix such as ``dev``.

    Returns:
        Package name with dots in ``version`` replaced by hyphens.
    """
    name = "%s-%s-v%s" % (
        d.getVar("PN"),
        component,
        version.replace(".", "-"),
    )
    if kind != "runtime":
        name += "-" + kind
    return name


def halif_parse_versions_yaml(path):
    """Read the component selections from the product YAML file.

    Expected input layout::

        components:
          component: 0.1.2.3

    Only blank lines and full-line comments are accepted in the mapping.

    Args:
        path: YAML file to read.

    Returns:
        Mapping from component names to validated interface versions.

    File output:
        None. This parser does not modify or generate YAML.
    """
    import re

    components = {}
    found_components = False
    in_components = False

    with open(path, "r", encoding="utf-8") as stream:
        for line_number, raw_line in enumerate(stream, 1):
            line = raw_line.rstrip("\n")
            stripped = line.strip()

            if not stripped or line.lstrip().startswith("#"):
                continue

            if line == "components:":
                if found_components:
                    bb.fatal("%s:%d: duplicate components mapping" % (path, line_number))
                found_components = True
                in_components = True
                continue

            if not in_components:
                continue

            match = re.fullmatch(
                r"  ([a-z0-9][a-z0-9-]*):[ \t]*([^# \t]+)[ \t]*",
                line,
            )
            if not match:
                bb.fatal(
                    "%s:%d: expected '  component: version', a blank line, or a full-line comment"
                    % (path, line_number)
                )

            component, version = match.groups()
            if component in components:
                bb.fatal("%s:%d: duplicate component %s" % (path, line_number, component))
            halif_validate_version(version, "%s:%d" % (path, line_number))
            components[component] = version

    if not found_components:
        bb.fatal("%s does not contain a components mapping" % path)

    return components


def halif_parse_overrides(d):
    """Parse product version overrides from the BitBake datastore.

    Input syntax is a semicolon-separated sequence of ``component=version``
    fields. Repeating a component selects several versions.

    Args:
        d: BitBake datastore that supplies
            ``HALIF_AIDL_INTERFACE_VERSION_OVERRIDES``.

    Returns:
        Mapping from component names to ordered, duplicate-free version lists.
    """
    grouped = {}
    raw = d.getVar("HALIF_AIDL_INTERFACE_VERSION_OVERRIDES") or ""

    for index, field in enumerate(raw.split(";"), 1):
        field = field.strip()
        if not field:
            continue
        if field.count("=") != 1:
            bb.fatal(
                "HALIF_AIDL_INTERFACE_VERSION_OVERRIDES entry %d must be component=version: %r"
                % (index, field)
            )

        component, version = (part.strip() for part in field.split("=", 1))
        halif_validate_component(component, "override entry %d" % index)
        halif_validate_version(version, "override entry %d" % index)

        versions = grouped.setdefault(component, [])
        if version not in versions:
            versions.append(version)

    return grouped


def halif_build_roots(d):
    """Resolve the isolated component/version roots to build.

    Repository mode reads release directories from this source layout::

        ${S}/<component>/<four-part-version>/

    YAML mode reads the product selection and replaces a selected version with
    all explicit overrides for that component.

    Args:
        d: BitBake datastore containing selection variables and ``S``.

    Returns:
        Sorted, duplicate-free list of ``(component, version)`` tuples.

    File output:
        None. CMake validates transitive dependencies later.
    """
    import os
    import re

    mode = d.getVar("HALIF_AIDL_VERSION_MODE")
    source = d.getVar("S")
    requested_components = d.getVar("HALIF_AIDL_COMPONENTS").split()
    overrides = halif_parse_overrides(d)
    roots = []

    for component in requested_components:
        halif_validate_component(component, "HALIF_AIDL_COMPONENTS")

    if mode == "repository":
        if overrides:
            bb.fatal(
                "HALIF_AIDL_INTERFACE_VERSION_OVERRIDES is only valid in yaml mode"
            )

        count = int(d.getVar("HALIF_AIDL_REPOSITORY_VERSION_COUNT"))
        if count < 1:
            bb.fatal("HALIF_AIDL_REPOSITORY_VERSION_COUNT must be positive")

        version_pattern = re.compile(r"[0-9]+(?:\.[0-9]+){3}")
        for component in requested_components:
            component_dir = os.path.join(source, component)
            if not os.path.isdir(component_dir):
                bb.fatal("Component directory does not exist: %s" % component_dir)

            versions = [
                entry
                for entry in os.listdir(component_dir)
                if version_pattern.fullmatch(entry)
                and os.path.isdir(os.path.join(component_dir, entry))
            ]
            versions.sort(key=halif_version_key)

            if not versions:
                bb.fatal("Component %s has no released versions" % component)
            if len(versions) < count:
                bb.warn(
                    "%s has only %d released version(s); requested the newest %d"
                    % (component, len(versions), count)
                )

            roots.extend((component, version) for version in versions[-count:])

    elif mode == "yaml":
        selection = halif_parse_versions_yaml(
            d.getVar("HALIF_AIDL_INTERFACE_VERSION_FILE")
        )

        for component in requested_components:
            if component not in selection and component not in overrides:
                bb.fatal(
                    "YAML selection does not contain requested component %s"
                    % component
                )

        for component in requested_components:
            versions = overrides.pop(component, None)
            if versions is None:
                versions = [selection[component]]
            roots.extend((component, version) for version in versions)

        # An override can deliberately add a component not present in the
        # example allow-list. CMake remains responsible for validating it.
        for component, versions in overrides.items():
            roots.extend((component, version) for version in versions)

    else:
        bb.fatal(
            "HALIF_AIDL_VERSION_MODE must be 'repository' or 'yaml', not %r"
            % mode
        )

    return sorted(set(roots), key=lambda root: (root[0], halif_version_key(root[1])))


def halif_unit_id(component, version):
    """Create the directory identifier for one isolated build unit.

    Args:
        component: Component name.
        version: Four-part interface version.

    Returns:
        Identifier in ``<component>-v<version>`` form.
    """
    return "%s-v%s" % (component, version)


def halif_cmake_command(d, build_dir, component, version):
    """Construct the root-CMake configure command for one build unit.

    Args:
        d: BitBake datastore supplying CMake class variables and source paths.
        build_dir: Unit-specific out-of-tree CMake build directory.
        component: Interface selected through ``INTERFACE_TARGET``.
        version: Interface version selected through ``INTERFACE_VERSION``.

    Returns:
        Argument list suitable for ``subprocess.run``. The list includes the
        Yocto toolchain, site file, generator, and extra CMake arguments.

    File output:
        None. Executing the returned command creates ``build_dir``.
    """
    import os
    import shlex

    generator = d.getVar("OECMAKE_GENERATOR")
    command = [
        "cmake",
        "-S",
        d.getVar("OECMAKE_SOURCEPATH"),
        "-B",
        build_dir,
        "-G",
        generator,
    ]

    if generator == "Ninja":
        command.append("-DCMAKE_MAKE_PROGRAM=ninja")
    elif generator == "Unix Makefiles":
        command.append("-DCMAKE_MAKE_PROGRAM=%s" % d.getVar("MAKE"))
    else:
        bb.fatal("Unsupported CMake generator: %s" % generator)

    site_file = os.path.join(d.getVar("WORKDIR"), "site-file.cmake")
    if os.path.exists(site_file):
        command.extend(["-C", site_file])

    # Kirkstone's cmake_do_configure passes these directly, not via OECMAKE_ARGS.
    prefix = d.getVar("prefix")
    command.append("-DCMAKE_INSTALL_PREFIX:PATH=%s" % prefix)
    for cmake_dir, variable in (
        ("BINDIR", "bindir"),
        ("SBINDIR", "sbindir"),
        ("LIBEXECDIR", "libexecdir"),
        ("SHAREDSTATEDIR", "sharedstatedir"),
        ("LIBDIR", "libdir"),
        ("INCLUDEDIR", "includedir"),
        ("DATAROOTDIR", "datadir"),
    ):
        command.append(
            "-DCMAKE_INSTALL_%s:PATH=%s"
            % (cmake_dir, os.path.relpath(d.getVar(variable), prefix + "/"))
        )
    command.extend(
        [
            "-DCMAKE_INSTALL_SYSCONFDIR:PATH=%s" % d.getVar("sysconfdir"),
            "-DCMAKE_INSTALL_LOCALSTATEDIR:PATH=%s" % d.getVar("localstatedir"),
            "-DPYTHON_EXECUTABLE:PATH=%s" % d.getVar("PYTHON"),
            "-DPython_EXECUTABLE:PATH=%s" % d.getVar("PYTHON"),
            "-DPython3_EXECUTABLE:PATH=%s" % d.getVar("PYTHON"),
            "-DLIB_SUFFIX=%s" % d.getVar("baselib").replace("lib", ""),
            "-DCMAKE_INSTALL_SO_NO_EXE=0",
            "-DCMAKE_TOOLCHAIN_FILE=%s"
            % os.path.join(d.getVar("WORKDIR"), "toolchain.cmake"),
            "-DCMAKE_NO_SYSTEM_FROM_IMPORTED=1",
            "-DCMAKE_EXPORT_NO_PACKAGE_REGISTRY=ON",
            "-DFETCHCONTENT_FULLY_DISCONNECTED=ON",
        ]
    )
    command.extend(shlex.split(d.getVar("EXTRA_OECMAKE") or ""))
    command.extend(
        [
            "-DINTERFACE_TARGET:STRING=%s" % component,
            "-DINTERFACE_VERSION:STRING=%s" % version,
            "-Wno-dev",
        ]
    )
    return command


python do_configure() {
    """Configure one root-CMake build tree per selected root.

    Inputs:
        The BitBake datastore supplies source, CMake, and version-selection
        variables. ``halif_build_roots`` provides the selected roots.

    Output layout::

        ${B}/
          units/
            <component>-v<version>/
              CMakeCache.txt
              ...

    The task clears ``${B}`` before creating this layout. It returns no value.
    """
    import os
    import shlex
    import subprocess

    units_dir = os.path.join(d.getVar("B"), "units")
    bb.utils.mkdirhier(units_dir)

    for component, version in halif_build_roots(d):
        build_dir = os.path.join(units_dir, halif_unit_id(component, version))
        command = halif_cmake_command(d, build_dir, component, version)
        bb.note("Configuring %s@%s: %s" % (
            component,
            version,
            shlex.join(command),
        ))
        subprocess.run(command, check=True)
}

do_configure[cleandirs] = "${B}"
do_configure[vardeps] += "\
    HALIF_AIDL_COMPONENTS \
    HALIF_AIDL_REPOSITORY_VERSION_COUNT \
    HALIF_AIDL_VERSION_MODE \
    HALIF_AIDL_INTERFACE_VERSION_FILE \
    HALIF_AIDL_INTERFACE_VERSION_OVERRIDES \
"


def halif_build_directories(d):
    """List the configured isolated CMake build directories.

    Args:
        d: BitBake datastore that supplies ``B``.

    Returns:
        Sorted list of absolute directories below ``${B}/units``.

    File output:
        None. Calls ``bb.fatal`` if configuration produced no unit directories.
    """
    import os

    units_dir = os.path.join(d.getVar("B"), "units")
    if not os.path.isdir(units_dir):
        bb.fatal("CMake unit directory does not exist: %s" % units_dir)

    build_dirs = [
        os.path.join(units_dir, entry)
        for entry in sorted(os.listdir(units_dir))
        if os.path.isdir(os.path.join(units_dir, entry))
    ]
    if not build_dirs:
        bb.fatal("No HALIF CMake units were configured")
    return build_dirs


python do_compile() {
    """Build every configured CMake unit independently.

    Input layout::

        ${B}/units/<component>-v<version>/

    Output layout:
        CMake writes libraries and generated files into each input build tree.
        No unit reads artifacts from another unit, and the task returns no
        value.
    """
    import shlex
    import subprocess

    for build_dir in halif_build_directories(d):
        command = [
            "cmake",
            "--build",
            build_dir,
            "--target",
            d.getVar("OECMAKE_TARGET_COMPILE"),
        ]
        bb.note("Building %s: %s" % (
            build_dir.rsplit("/", 1)[-1],
            shlex.join(command),
        ))
        subprocess.run(command, check=True)
}


python do_install() {
    """Install each built closure into a separate destination root.

    Input layout::

        ${B}/units/<component>-v<version>/

    Output layout::

        ${HALIF_UNIT_INSTALL_ROOT}/
          <component>-v<version>/
            usr/
              include/...
              lib/...
              share/...

    ``DESTDIR`` creates the unit-specific prefix. ``${D}`` remains empty
    because several development variants intentionally contain the same
    installed paths. The task returns no value.
    """
    import os
    import shlex
    import subprocess

    install_root = d.getVar("HALIF_UNIT_INSTALL_ROOT")
    bb.utils.mkdirhier(d.getVar("D"))
    bb.utils.mkdirhier(install_root)

    for build_dir in halif_build_directories(d):
        unit_id = os.path.basename(build_dir)
        destination = os.path.join(install_root, unit_id)
        bb.utils.mkdirhier(destination)

        command = ["cmake", "--install", build_dir]
        environment = os.environ.copy()
        environment["DESTDIR"] = destination
        bb.note("Installing %s into %s: %s" % (
            unit_id,
            destination,
            shlex.join(command),
        ))
        subprocess.run(command, check=True, env=environment)
}

do_install[cleandirs] = "${D} ${HALIF_UNIT_INSTALL_ROOT}"


def halif_copy_file(source, destination):
    """Copy one staged file without allowing silent replacement.

    Args:
        source: Existing regular file or symbolic link.
        destination: New path to create.

    Returns:
        None. Parent directories are created as needed.

    File output:
        Regular files retain metadata through ``shutil.copy2``. Symbolic links
        retain their link target. An existing destination is a fatal collision.
    """
    import os
    import shutil

    bb.utils.mkdirhier(os.path.dirname(destination))
    if os.path.lexists(destination):
        bb.fatal("Package staging collision at %s" % destination)

    if os.path.islink(source):
        os.symlink(os.readlink(source), destination)
    else:
        shutil.copy2(source, destination)


def halif_unit_files(root):
    """Collect all files below an isolated install or package root.

    Args:
        root: Directory to traverse recursively.

    Returns:
        Sorted list of absolute file paths. Directories are not returned.

    File output:
        None.
    """
    import os

    files = []
    for directory, _, filenames in os.walk(root):
        for filename in filenames:
            files.append(os.path.join(directory, filename))
    return sorted(files)


def halif_relative_path(root, path):
    """Express a path below a root with package-compatible separators.

    Args:
        root: Directory against which to make ``path`` relative.
        path: File below ``root``.

    Returns:
        Relative path using ``/`` separators on every build host.
    """
    import os

    return os.path.relpath(path, root).replace(os.sep, "/")


def halif_unit_versions(d, unit_root):
    """Infer the component versions contained in one installed closure.

    Version evidence comes from runtime library names in this layout::

        <unit_root>/<libdir>/lib<component>-v<version>-cpp.so[.<suffix>]

    Args:
        d: BitBake datastore that supplies ``libdir``.
        unit_root: Root of one isolated CMake installation.

    Returns:
        Mapping from component names to exact interface versions.

    File output:
        None. A closure with no versioned library, or two versions of one
        component, is rejected.
    """
    import os
    import re

    libdir = d.getVar("libdir").lstrip("/")
    library_pattern = re.compile(
        r"lib([a-z0-9][a-z0-9-]*)-v([0-9]+(?:\.[0-9]+){3})-cpp\.so(?:\..*)?"
    )
    versions = {}

    for path in halif_unit_files(unit_root):
        relative = halif_relative_path(unit_root, path)
        if not relative.startswith(libdir + "/"):
            continue

        match = library_pattern.fullmatch(os.path.basename(path))
        if not match:
            continue

        component, version = match.groups()
        previous = versions.get(component)
        if previous is not None and previous != version:
            bb.fatal(
                "%s contains %s in versions %s and %s"
                % (unit_root, component, previous, version)
            )
        versions[component] = version

    if not versions:
        bb.fatal("No versioned HALIF libraries found under %s" % unit_root)
    return versions


def halif_file_component(d, relative, versions):
    """Assign one installed file to exactly one component.

    The function recognises versioned libraries, pkg-config module names,
    component path segments, and component-specific CMake directories.
    Source and header files directly under com/rdk/hal belong to common,
    which must have version evidence in the same unit.

    Args:
        d: BitBake datastore. Reserved for path configuration consistency.
        relative: Installed path relative to its isolated unit root.
        versions: Component/version mapping inferred for that unit.

    Returns:
        The single component that owns ``relative``.

    File output:
        None. Missing or ambiguous ownership is fatal.
    """
    import os
    import re

    if re.search(r"(?:^|/)com/rdk/hal/[^/]+\.(?:cpp|h)$", relative):
        if "common" not in versions:
            bb.fatal(
                "%s requires common, but this build unit has no common version evidence"
                % relative
            )
        return "common"

    basename = os.path.basename(relative)
    library_match = re.fullmatch(
        r"lib([a-z0-9][a-z0-9-]*)-v([0-9]+(?:\.[0-9]+){3})-cpp\.so(?:\..*)?",
        basename,
    )
    if library_match:
        component, version = library_match.groups()
        if versions.get(component) != version:
            bb.fatal("Version evidence disagrees for %s" % relative)
        return component

    pkgconfig_match = re.fullmatch(
        r"rdk-halif-aidl-([a-z0-9][a-z0-9-]*)\.pc",
        basename,
    )
    if pkgconfig_match:
        return pkgconfig_match.group(1)

    matches = []
    for component in versions:
        component_pattern = r"(^|[-_/])%s([./_-]|$)" % re.escape(component)
        cmake_directory = "RdkHalifAidl" + component[0].upper() + component[1:]
        if re.search(component_pattern, relative) or cmake_directory in relative:
            matches.append(component)

    if len(matches) != 1:
        bb.fatal(
            "Cannot assign %s to exactly one component; candidates: %s"
            % (relative, " ".join(matches) or "<none>")
        )
    return matches[0]


def halif_pkgconfig_dependencies(d, pc_path, versions):
    """Resolve pkg-config requirements to exact development packages.

    Args:
        d: BitBake datastore supplying package and Binder package names.
        pc_path: Installed HALIF ``.pc`` file to inspect.
        versions: Component/version mapping for the isolated closure.

    Returns:
        List of exact ``-dev`` package dependencies. The Binder requirement is
        translated to ``HALIF_BINDER_DEV_PACKAGE``.

    File output:
        None. Unsupported, absent, or version-mismatched requirements are
        rejected.
    """
    import re

    dependencies = []
    with open(pc_path, "r", encoding="utf-8") as stream:
        for line in stream:
            if not line.startswith(("Requires:", "Requires.private:")):
                continue

            for requirement in line.split(":", 1)[1].split(","):
                requirement = requirement.strip()
                if not requirement:
                    continue
                if requirement == "binder":
                    dependencies.append(d.getVar("HALIF_BINDER_DEV_PACKAGE"))
                    continue

                match = re.fullmatch(
                    r"rdk-halif-aidl-([a-z0-9][a-z0-9-]*)"
                    r"(?:[ \t]*(?:=|>=|<=|>|<)[ \t]*([0-9]+(?:\.[0-9]+){3}))?",
                    requirement,
                )
                if not match:
                    bb.fatal(
                        "Unsupported pkg-config requirement %r in %s"
                        % (requirement, pc_path)
                    )

                component, requested_version = match.groups()
                version = requested_version or versions.get(component)
                if version is None:
                    bb.fatal(
                        "%s requires %s, which is absent from its isolated closure"
                        % (pc_path, component)
                    )
                if versions.get(component) != version:
                    bb.fatal(
                        "%s requires %s@%s, but its closure contains %s"
                        % (pc_path, component, version, versions.get(component))
                    )

                dependencies.append(
                    halif_package_name(d, component, version, "dev")
                )

    return dependencies


python halif_prepare_package_trees() {
    """Canonicalise isolated installs into namespaced package trees.

    Input layout::

        ${HALIF_UNIT_INSTALL_ROOT}/
          <root-component>-v<root-version>/
            usr/<installed closure files>

    Output layout before BitBake strips binaries::

        ${PKGD}/
          ${HALIF_PACKAGE_NAMESPACE}/
            <exact-runtime-package>/usr/<versioned library>
            <exact-dev-package>/usr/<headers, .pc, and CMake files>
            ${PN}-cmake-modules/usr/<shared CMake find modules>

    The function infers component versions and file ownership from installed
    content. It keeps the first copy of duplicated transitive output, omits the
    Binder-owned ``binder.pc`` and ``utils.pc`` files, and adds exact package
    dependencies and development-package conflicts to the datastore.

    BitBake subsequently strips files in this namespace and may add
    ``usr/lib/.debug`` files below a runtime package tree. The function returns
    no value.
    """
    import os
    import re

    install_root = d.getVar("HALIF_UNIT_INSTALL_ROOT")
    pkgd = d.getVar("PKGD")
    namespace = os.path.join(pkgd, d.getVar("HALIF_PACKAGE_NAMESPACE"))
    includedir = d.getVar("includedir").lstrip("/")
    libdir = d.getVar("libdir").lstrip("/")
    datadir = d.getVar("datadir").lstrip("/")
    support_prefix = datadir + "/cmake/Modules/"
    support_package = d.getVar("PN") + "-cmake-modules"

    bb.utils.mkdirhier(namespace)

    canonical_packages = set()
    dev_packages_by_component = {}
    dynamic_packages = [support_package]
    support_copied = False
    support_file_count = 0

    unit_roots = [
        os.path.join(install_root, entry)
        for entry in sorted(os.listdir(install_root))
        if os.path.isdir(os.path.join(install_root, entry))
    ]
    if not unit_roots:
        bb.fatal("No isolated HALIF install roots found under %s" % install_root)

    for unit_root in unit_roots:
        versions = halif_unit_versions(d, unit_root)
        files_by_package = {}
        pc_files = {}

        for source in halif_unit_files(unit_root):
            relative = halif_relative_path(unit_root, source)
            basename = os.path.basename(relative)

            if relative == libdir + "/pkgconfig/binder.pc" or relative == libdir + "/pkgconfig/utils.pc":
                continue

            if relative == "usr/include/" + "halcompat.h":
                # TODO make it's own package?
                #destination = os.path.join(namespace, support_package, relative)
                #halif_copy_file(source, destination)
                #support_file_count += 1
                continue

            if relative.startswith(support_prefix):
                if not support_copied:
                    destination = os.path.join(namespace, support_package, relative)
                    halif_copy_file(source, destination)
                    support_file_count += 1
                continue

            component = halif_file_component(d, relative, versions)
            version = versions.get(component)
            if version is None:
                bb.fatal(
                    "%s identifies component %s without version evidence"
                    % (relative, component)
                )

            is_runtime = (
                relative.startswith(libdir + "/")
                and re.fullmatch(
                    r"lib%s-v%s-cpp\.so(?:\..*)?"
                    % (re.escape(component), re.escape(version)),
                    basename,
                )
            )
            kind = "runtime" if is_runtime else "dev"
            package = halif_package_name(d, component, version, kind)
            files_by_package.setdefault(package, []).append((source, relative))

            if relative == "%s/pkgconfig/rdk-halif-aidl-%s.pc" % (libdir, component):
                pc_files[package] = source

        support_copied = True

        for package in sorted(files_by_package):
            if package in canonical_packages:
                bb.note("Ignoring duplicate output for %s from %s" % (
                    package,
                    os.path.basename(unit_root),
                ))
                continue

            canonical_packages.add(package)
            dynamic_packages.append(package)
            for source, relative in files_by_package[package]:
                destination = os.path.join(namespace, package, relative)
                halif_copy_file(source, destination)

            d.setVar("DEBIAN_NOAUTONAME:" + package, "1")
            d.setVar("SUMMARY:" + package, "RDK HALIF AIDL " + package)

            if package.endswith("-dev"):
                identity = package[:-4]
                component_and_version = identity[len(d.getVar("PN")) + 1:]
                component, encoded_version = component_and_version.rsplit("-v", 1)
                version = encoded_version.replace("-", ".")
                runtime_package = halif_package_name(
                    d, component, version, "runtime"
                )
                dependencies = [runtime_package, support_package]

                pc_path = pc_files.get(package)
                if pc_path is None:
                    bb.fatal("Development package %s has no pkg-config file" % package)
                dependencies.extend(
                    halif_pkgconfig_dependencies(d, pc_path, versions)
                )
                d.setVar(
                    "RDEPENDS:" + package,
                    " ".join(sorted(set(dependencies))),
                )
                dev_packages_by_component.setdefault(component, []).append(package)
            else:
                # The actual shared object ends in .so, but its basename
                # contains the complete interface version and is a runtime
                # artifact rather than an unversioned development symlink.
                d.appendVar("INSANE_SKIP:" + package, " dev-so")

    if support_file_count == 0:
        bb.fatal("No installed HALIF CMake find modules were found")

    d.setVar("SUMMARY:" + support_package, "RDK HALIF AIDL CMake find modules")
    d.setVar("DEBIAN_NOAUTONAME:" + support_package, "1")

    for component, packages in dev_packages_by_component.items():
        packages = sorted(set(packages))
        for package in packages:
            conflicts = [candidate for candidate in packages if candidate != package]
            if conflicts:
                d.setVar("RCONFLICTS:" + package, " ".join(conflicts))

    existing = d.getVar("PACKAGES").split()
    ordered = []
    for package in dynamic_packages + existing:
        if package not in ordered:
            ordered.append(package)
    d.setVar("PACKAGES", " ".join(ordered))
}


python halif_split_package_trees() {
    """Split namespaced trees directly into BitBake's package destination.

    Input layout after BitBake's strip/debug processing::

        ${PKGD}/${HALIF_PACKAGE_NAMESPACE}/
          <package>/usr/<normal files>
          <package>/usr/lib/.debug/<debug files>

    Output layout::

        ${PKGDEST}/
          <package>/usr/<normal files>
          <package>-dbg/usr/lib/.debug/<debug files>

    The function preserves identical installed paths in different package
    directories, creates debug package metadata when needed, and creates a
    destination directory for every declared package, including empty ones.
    It removes the temporary namespace after a successful split and returns
    no value.
    """
    import os
    import shutil

    pkgd = d.getVar("PKGD")
    pkgdest = d.getVar("PKGDEST")
    namespace = os.path.join(pkgd, d.getVar("HALIF_PACKAGE_NAMESPACE"))

    if not os.path.isdir(namespace):
        bb.fatal("HALIF package namespace does not exist: %s" % namespace)

    packages = d.getVar("PACKAGES").split()
    for package in sorted(os.listdir(namespace)):
        package_root = os.path.join(namespace, package)
        if not os.path.isdir(package_root):
            continue

        for source in halif_unit_files(package_root):
            relative = halif_relative_path(package_root, source)
            debug_file = "/.debug/" in "/" + relative
            destination_package = package + "-dbg" if debug_file else package

            if destination_package not in packages:
                packages.insert(0, destination_package)
                d.setVar("DEBIAN_NOAUTONAME:" + destination_package, "1")
                if debug_file:
                    d.setVar("RDEPENDS:" + destination_package, package)

            destination = os.path.join(pkgdest, destination_package, relative)
            halif_copy_file(source, destination)

        shutil.rmtree(package_root)

    for package in packages:
        bb.utils.mkdirhier(os.path.join(pkgdest, package))

    shutil.rmtree(namespace)
    d.setVar("PACKAGES", " ".join(packages))
}