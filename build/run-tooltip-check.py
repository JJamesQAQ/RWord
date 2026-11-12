from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
rider = Path("C:/Program Files/JetBrains/JetBrains Rider 2026.2.0.2")
env = os.environ.copy()
env["CLASSPATH"] = os.pathsep.join(str(path) for path in (rider / "lib").rglob("*.jar"))
sources = sorted((root / "src").rglob("*.java")) + sorted((root / "check").rglob("*.java"))

with tempfile.TemporaryDirectory(prefix="riderword-check-") as temporary:
    subprocess.run([
        str(rider / "jbr/bin/javac.exe"), "--release", "25", "-encoding", "UTF-8",
        "-d", temporary, *map(str, sources),
    ], env=env, check=True)
    env["CLASSPATH"] = temporary + os.pathsep + env["CLASSPATH"]
    for main in ["WordListCheck", "WidgetTooltipCheck"]:
        subprocess.run([
            str(rider / "jbr/bin/java.exe"), "-Djava.awt.headless=true",
            "-Dfile.encoding=UTF-8", main,
        ], env=env, check=True)
