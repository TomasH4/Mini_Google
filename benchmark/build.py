import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HERE = ROOT / "benchmark"
WORK = HERE / "work"

def command(args, cwd=ROOT):
    subprocess.run([str(a) for a in args], cwd=cwd, check=True)

def tool(name):
    path = os.environ.get(name.upper()) or shutil.which(name)
    if not path and name == "go" and os.name == "nt":
        path = Path(os.environ["ProgramFiles"]) / "Go/bin/go.exe"
    if not path or not Path(path).is_file():
        raise RuntimeError(f"Set {name.upper()} to the executable for {name}")
    return str(path)

def build():
    WORK.mkdir(exist_ok=True)
    go, mvn = tool("go"), tool("mvn")
    exe = WORK / ("suite.exe" if os.name == "nt" else "suite")
    command([go, "build", "-trimpath", "-ldflags=-s -w", "-o", exe, "./cmd/suite"], ROOT / "mini-google-go")
    commands, artifacts = {"go": [str(exe)]}, {"go": exe}
    for lang in ("java", "scala"):
        module = ROOT / f"mini-google-{lang}"
        # Distinct Maven caches can optionally be supplied on offline teaching machines.
        cache = os.environ.get("M2_" + lang.upper())
        args = [mvn, "-B", "-ntp", "-q", "-DskipTests", "-Dmaven.compiler.debug=false"]
        if cache:
            args.append("-Dmaven.repo.local=" + cache)
        command([*args, "clean", "package"], module)
        jar, = (module / "target").glob("mini-google*-SNAPSHOT.jar")
        artifact = WORK / (lang + ".jar")
        shutil.copyfile(jar, artifact)
        classes = WORK / ("classes-" + lang)
        classes.mkdir(exist_ok=True)
        engine = lang.capitalize() + "Engine"
        sources = [HERE / "jvm" / (name + ".java") for name in ["Suite", "Vocabulary", engine]]
        command(["javac", "--release", "21", "-g:none", "-encoding", "UTF-8", "-cp", artifact, "-d", classes, *sources])
        command(["jar", "uf", artifact, "-C", classes, "."])
        commands[lang] = ["java", "-Xms64m", "-Xmx2g", "-Dfile.encoding=UTF-8", "-cp", str(artifact), "Suite", engine]
        artifacts[lang] = artifact
    return commands, artifacts

