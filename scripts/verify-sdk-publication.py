"""Check staged Maven SDK artifacts without credentials or a remote upload."""

from pathlib import Path
import argparse
import hashlib
import xml.etree.ElementTree as ET
import zipfile


def verify(repository, version, require_signatures=False):
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    for artifact in ("sky-plugin-protocol", "sky-plugin-api"):
        directory = repository / "io/github/zaina01" / artifact / version
        metadata = ET.parse(directory / 'maven-metadata.xml').getroot() if version.endswith('-SNAPSHOT') else None

        def artifact_path(extension, classifier=None):
            file_version = version
            if metadata is not None:
                matches = [entry.findtext('value') for entry in metadata.findall('versioning/snapshotVersions/snapshotVersion')
                           if entry.findtext('extension') == extension and entry.findtext('classifier') == classifier]
                if len(matches) != 1:
                    raise ValueError(f'{artifact}: missing snapshot metadata for {extension}/{classifier}')
                file_version = matches[0]
            suffix = f'-{classifier}' if classifier else ''
            path = directory / f'{artifact}-{file_version}{suffix}.{extension}'
            for algorithm in ('md5', 'sha1'):
                checksum = path.with_name(f'{path.name}.{algorithm}').read_text().strip()
                if hashlib.new(algorithm, path.read_bytes()).hexdigest() != checksum:
                    raise ValueError(f'{path}: invalid {algorithm} checksum')
            if require_signatures:
                signature = path.with_name(f'{path.name}.asc')
                if not signature.is_file() or 'BEGIN PGP SIGNATURE' not in signature.read_text():
                    raise ValueError(f'{path}: missing ASCII-armored signature')
            return path

        pom = ET.parse(artifact_path('pom')).getroot()
        for field, expected in (("groupId", "io.github.zaina01"), ("artifactId", artifact), ("version", version)):
            actual = pom.findtext(f"m:{field}", namespaces=ns)
            if actual != expected:
                raise ValueError(f"{artifact}: {field} is {actual!r}, expected {expected!r}")
        for field in ("name", "description", "url", "licenses/m:license/m:name",
                      "licenses/m:license/m:url", "developers/m:developer/m:id",
                      "developers/m:developer/m:name", "scm/m:connection", "scm/m:developerConnection"):
            if not pom.findtext(f"m:{field}", namespaces=ns):
                raise ValueError(f"{artifact}: missing POM field {field}")
        for classifier, ending in ((None, ".class"), ("sources", ".java"), ("javadoc", ".html")):
            with zipfile.ZipFile(artifact_path('jar', classifier)) as jar:
                if not any(name.endswith(ending) for name in jar.namelist()):
                    raise ValueError(f"{artifact}/{classifier}: no {ending} files")
        if artifact == "sky-plugin-api":
            dependencies = pom.findall("m:dependencies/m:dependency", ns)
            protocol = [dep for dep in dependencies if dep.findtext("m:artifactId", namespaces=ns) == "sky-plugin-protocol"]
            if len(protocol) != 1 or protocol[0].findtext("m:version", namespaces=ns) != version:
                raise ValueError("API must depend on the protocol at the same SDK version")
        print(f"Verified {artifact}:{version}: binary, sources, Javadoc and POM")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('repository', type=Path)
    parser.add_argument('sdk_version')
    parser.add_argument('--require-signatures', action='store_true')
    args = parser.parse_args()
    verify(args.repository, args.sdk_version, args.require_signatures)
